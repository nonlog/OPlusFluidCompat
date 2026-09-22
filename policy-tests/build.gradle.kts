plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Compile the actual policy file, not a separate copy or an Android mock.
sourceSets {
    named("main") {
        java.setSrcDirs(listOf("../app/src/main/java"))
        java.include("io/github/nonlog/oplusfluidcompat/AmapCompatibilityPolicy.java")
        java.include("io/github/nonlog/oplusfluidcompat/ChinaCompatibilityPolicy.java")
    }
    named("test") {
        java.setSrcDirs(listOf("../app/src/test/java"))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
