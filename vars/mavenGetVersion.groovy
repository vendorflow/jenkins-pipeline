String call() {
    return sh(
      returnStdout: true,
      script: "./mvnw -q -Dexec.executable='echo' -Dexec.args='\${project.version}' --non-recursive org.codehaus.mojo:exec-maven-plugin:1.6.0:exec | tail -n 1"
    ).trim()
}
