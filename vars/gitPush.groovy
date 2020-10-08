def call(String sshKey='5d553976-bdfb-494b-a087-7dab6125f518', String remote = UPSTREAM_REMOTE, String remoteBranch = UPSTREAM_BRANCH) {
  sshagent([sshKey]) {
    sh "git push ${remote} HEAD:${remoteBranch}"
  }
}
