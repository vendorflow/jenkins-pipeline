import co.vendorflow.pipeline.semver.Component

def call(Map parameters = [:]) {

  def problems = []
  String nextVersion = null

  hudson.tasks.junit.TestResultSummary testResults
  def merging = null

//  def artifactory = getArtifactoryServer('ngin-artifactory')
//  def rtMaven = newMavenBuild()
//  def buildInfo = newBuildInfo()

  pipeline {
    agent any

    tools {
      jdk parameters.get('jdk', 'jdk11')
    }

    environment {
      GIT_AUTHOR = 'Vendorflow Jenkins <jenkins@vendorflow.co>'
      UPSTREAM_REMOTE = parameters.get('remoteName', 'origin')
      UPSTREAM_BRANCH = parameters.get('trunkBranch', 'master')

      EXISTING_VERSION = mavenGetVersion()
      SEMVER_INCREMENT = semverFindTag()
      // can't use this as an enviroment variable
      // https://issues.jenkins-ci.org/browse/JENKINS-50269
      // MERGE_REQUESTED = readyForMerge()
    }

    stages {
      stage('Acknowledge') {
        steps {
          slackSend message: "Pipeline started for $JOB_NAME"
        }
      }


      stage('Analyze') {
        steps {
          script {
            // https://github.com/cloudbees/groovy-cps/issues/89
            boolean ff = gitCanFastForward()
            echo "ff: $ff"
            unstableUnless(ff, "unable to fast-forward this branch onto $UPSTREAM_BRANCH".toString(), problems)

            if(unstableUnless(semverValidate(SEMVER_INCREMENT, readyToMerge()), "expected valid version increment but was $SEMVER_INCREMENT".toString(), problems)) {
              nextVersion = semverNextVersion(EXISTING_VERSION, SEMVER_INCREMENT) as String
              echo "planned version update: $EXISTING_VERSION -> $nextVersion".toString()
            }
          }

          sh 'printenv | sort'
        }
      }


      stage('Local merge') {
        when { expression { nextVersion } }
        steps {
          mavenSetVersion nextVersion
          gitCommit "CI version $nextVersion (build $BUILD_ID)\n\n$GITHUB_PR_URL $GITHUB_PR_TITLE\n\n$GITHUB_PR_BODY"
        }
      }


      stage('Build') {
        steps {
          withMaven(mavenSettingsConfig: 'vendorflow-ci-settings-xml') {
            sh "./mvnw clean verify"
          }
        }

        post {
          always {
            script {
              testResults = junit '**/target/surefire-reports/**.xml'
              if(testResults.failCount) problems << "$testResults.failCount failing test(s)"
              jacoco execPattern: '**/target/jacoco.exec'

              script { merging = readyToMerge() && currentBuild.resultIsBetterOrEqualTo('SUCCESS') }

              vendorflowSlackReport(nextVersion, problems, testResults, merging)
            }
          }
        }
      }


      stage('Report') {
        steps {
          gitHubPrUpdate(problems)
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
          }

          stage('Deploy artifacts') {
            steps {
              withMaven(mavenSettingsConfig: 'vendorflow-ci-settings-xml') {
                sh "./mvnw deploy -DskipTests=true"
              }
              archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
          }
        }
      }
    }

    post {
      always {
        echo "problems: $problems"
      }

      success {
        echo "buildResult: $currentBuild.result"
      }

      cleanup {
        gitHubPrUpdate(problems)
      }
    }
  }
}
