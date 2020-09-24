def call(String version, String maven = './mvnw') {
  sh "$maven versions:set -DnewVersion='${version}' -DgenerateBackupPoms=false"
}
