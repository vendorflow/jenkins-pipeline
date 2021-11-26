package co.vendorflow.pipeline.semver

import com.cloudbees.groovy.cps.NonCPS

enum Component {
    PATCH(2),
    MINOR(1),
    MAJOR(0);

    final int index

    private Component(int index) {
        this.index = index
    }

    @NonCPS
    static Component find(String component) {
        for(Component it : Component.values()) {
            if(it.toString().equalsIgnoreCase(component)) {
                return it
            }
        }
        
        return null
    }
}
