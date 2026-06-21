plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    val velocityVersion = "3.3.0-SNAPSHOT"
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityVersion")

    implementation(project(":common"))

    // Provided by Velocity at runtime.
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterBanX-VelocityRelay")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Velocity plugins must not produce a plain (un-shaded) jar as the primary artifact.
tasks.jar {
    archiveClassifier.set("plain")
}
