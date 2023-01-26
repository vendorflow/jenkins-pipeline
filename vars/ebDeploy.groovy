def call(
    String env,
    String version = NEW_VERSION,
    String name = JOB_NAME,
    String makeDeployArtifactLink='./deploy/makeLink.sh',
    String credentialPrefix='aws_eb_deploy-'
  ) {
  sh makeDeployArtifactLink
  withCredentials([usernamePassword(
      credentialsId: "$credentialPrefix$env",
      usernameVariable: 'AWS_ACCESS_KEY_ID',
      passwordVariable: 'AWS_SECRET_ACCESS_KEY'
  )]) {
    slackSend color: 'good', message: "Deploying $name $version to $env"
    sh "eb deploy -l $version"
  }
}
