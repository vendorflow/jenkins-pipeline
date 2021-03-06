@NonCPS
def call(nextVersion = nextVersion, problems = problems, test = testResults, merging = merging, existingVersion = EXISTING_VERSION, jobName = JOB_NAME) {
  String pr = gitHubPrSlackLink()

  String status = problems ? "Build problems in $pr: $problems (<${BUILD_URL}console|console output>)" :
      (merging ? "Merging $pr" : "Validation succeeded for $pr")

  String TEST_REPORT_URL = BUILD_URL + 'testReport/'
  String testResults = "<$TEST_REPORT_URL|Test results:> $test.passCount passed, $test.failCount failed, $test.skipCount skipped"

  String message = """*<$BUILD_URL|$JOB_NAME>*: $existingVersion ⟶ $nextVersion
$status
$testResults"""

  String color = (currentBuild.resultIsBetterOrEqualTo('SUCCESS') && !problems) ? 'good' : 'danger'

  echo 'updating Slack'
  slackSend color: color, message: message
}
