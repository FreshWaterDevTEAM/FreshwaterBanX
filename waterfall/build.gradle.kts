plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

// Waterfall can run on Java 8, so this plugin (and the shaded :common classes) must be
// Java 8 bytecode. javac --release 8 (available on the JDK 17/21 used to build) enforces this.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

dependencies {
    // Waterfall API (uses the net.md_5.bungee packages); also runs on BungeeCord.
    // Only stable, long-standing methods are used, so the compiled output runs on old (Java 8) Waterfall too.
    compileOnly("io.github.waterfallmc:waterfall-api:1.21-R0.1-SNAPSHOT")

    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterBanX-Waterfall")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("plain")
}
