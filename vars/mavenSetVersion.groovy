def call(String version, String maven = './mvnw') {
  withMaven(mavenSettingsConfig: 'vendorflow-ci-settings-xml') {
    sh "$maven versions:set -DnewVersion='${version}' -DgenerateBackupPoms=false"
  }
}
