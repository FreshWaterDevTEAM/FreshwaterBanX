plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

dependencies {
    val velocityVersion = "3.3.0-SNAPSHOT"
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")
    annotationProcessor("com.velocitypowered:velocity-api:$velocityVersion")

    implementation(project(":common"))
    implementation(project(":api"))

    // Shaded runtime dependencies (Velocity does not provide these).
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")

    // Provided by Velocity at runtime.
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterBanX")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // Relocate shaded libraries to avoid clashing with anything on the proxy classpath.
    relocate("com.zaxxer.hikari", "io.freshwater.banx.libs.hikari")
    relocate("com.mysql", "io.freshwater.banx.libs.mysql")
    relocate("com.google.protobuf", "io.freshwater.banx.libs.protobuf")

    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Velocity plugins must not produce a plain (un-shaded) jar as the primary artifact.
tasks.jar {
    archiveClassifier.set("plain")
}
