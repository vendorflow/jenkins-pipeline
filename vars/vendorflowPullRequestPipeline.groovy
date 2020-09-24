def call(Map parameters = [:]) {

  def problems = []
  String nextVersion = null

  hudson.tasks.junit.TestResultSummary testResults
  def merged = null

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
      stage('Analyze') {
        steps {
          script {
            // https://github.com/cloudbees/groovy-cps/issues/89
            boolean ff = gitCanFastForward()
            echo "ff: $ff"
            unstableUnless(ff, "unable to fast-forward this branch onto $UPSTREAM_BRANCH".toString(), problems)

            echo "planned version update: $EXISTING_VERSION -> $NEW_VERSION".toString()
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
          sh "./mvnw clean package"
        }
      }


      stage('Report') {
        steps {
          gitHubPrUpdate(problems)
        }
      }


      stage('Publish') {
        when { 
          allOf {
            expression { readyForMerge() }
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
          }
        }

        parallel {
          stage('Close pull request') {
            steps {
              gitPush()
              script { merged = true }
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
          allOf {
            expression { readyForMerge() }
            expression { currentBuild.resultIsBetterOrEqualTo('SUCCESS') }
          }
        }

        steps {
          echo 'Would deploy here'
        }
      }
    }


    post {
      always {
        script {
          testResults = junit '**/target/surefire-reports/**.xml'
          if(testResults.failCount) problems << "$testResults.failCount failing test(s)"
        }

        echo "problems: $problems"

        vendorflowSlackReport(nextVersion, problems, testResults, merged)
        gitHubPrUpdate(problems)
      }

      success {
        echo "buildResult: $currentBuild.result"
      }
    }
  }
}
