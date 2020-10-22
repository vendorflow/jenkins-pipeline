def call(Map parameters = [:]) {

  def problems = []

  hudson.tasks.junit.TestResultSummary testResults
  def merging = null

  pipeline {
    agent any

    tools {
      // maven 'maven3'
      jdk parameters.get('jdk', 'jdk11')
    }

    environment {
      GIT_AUTHOR = 'Vendorflow Jenkins <jenkins@vendorflow.co>'
      UPSTREAM_REMOTE = parameters.get('remoteName', 'origin')
      UPSTREAM_BRANCH = parameters.get('trunkBranch', 'master')

      EXISTING_VERSION = mavenGetVersion()
      NEW_VERSION = versionFromMilliAndBuild(currentBuild.timeInMillis, currentBuild.number)
      // can't use this as an enviroment variable
      // https://issues.jenkins-ci.org/browse/JENKINS-50269
      // MERGE_REQUESTED = readyForMerge()
    }

    stages {
      stage('Acknowledge') {
        steps {
          slackSend message: "Pipeline started for $JOB_NAME $NEW_VERSION"
        }
      }

      stage('Analyze') {
        steps {
          script {
            // https://github.com/cloudbees/groovy-cps/issues/89
            boolean ff = gitCanFastForward()
            echo "ff: $ff"
            unstableUnless(ff, "unable to fast-forward this branch onto $UPSTREAM_BRANCH".toString(), problems)

            echo "planned version update: $NEW_VERSION".toString()
          }

          sh 'printenv | sort'
        }
      }


      stage('Local merge') {
        steps {
          mavenSetVersion NEW_VERSION
          gitCommit "CI version $NEW_VERSION (build $BUILD_ID)\n\n$GITHUB_PR_URL $GITHUB_PR_TITLE\n\n$GITHUB_PR_BODY"
        }
      }


      stage('Build') {
        steps {
          sh "./mvnw clean verify"
        }

        post {
          always {
            script {
              testResults = junit '**/target/surefire-reports/**.xml'
              if(testResults.failCount) problems << "$testResults.failCount failing test(s)"
              jacoco execPattern: '**/target/jacoco.exec'
            }

            echo "problems: $problems"

            script { merging = readyToMerge() && currentBuild.resultIsBetterOrEqualTo('SUCCESS') }

            vendorflowSlackReport(NEW_VERSION, problems, testResults, merging)
          }

          cleanup {
            gitHubPrUpdate(problems)
          }
        }
      }


      stage('Publish') {
        when { 
          expression { merging }
        }

        parallel {
          stage('Close pull request') {
            steps {
              gitPush()
            }

            post {
              failure {
                slackSend color: 'danger', message: 'Error merging ' + gitHubPrSlackLink()
              }
            }
          }

          stage('Archive jar file(s)') {
            steps {
              archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
          }
        }
      }


      stage('Deploy') {
        when {
          expression { merging }
        }

        steps {
          ebDeploy 'dev'
          sh './deploy/acceptanceTest.sh'
          ebDeploy 'prod'
        }

        post {
          success {
            slackSend color: 'good', message: "Deployment of $JOB_NAME $NEW_VERSION successful"
          }

          failure {
            slackSend color: 'danger', message: "Deployment of $JOB_NAME $NEW_VERSION failed <!channel>"
          }
        }
      }
    }

/*
    post {
      always {
      }

      success {
      }
    }
*/
  }
}
