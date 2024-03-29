import co.vendorflow.pipeline.semver.Component

@NonCPS
boolean call(String increment, boolean throwOnError) {
  if(Component.find(increment)) {
    return true
  }

  if(throwOnError) {
    throw new IllegalArgumentException("expected a valid version increment but was $increment")    
  }

  return false
}
