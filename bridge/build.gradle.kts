plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

// Compile-only source set holding stubs of the Matrix API so the bridge can be built without
// the proprietary Matrix jar. These classes are NOT bundled; the real Matrix classes are used
// at runtime (see softdepend in plugin.yml).
sourceSets {
    create("matrixStub")
}

val paperApi = "io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT"

dependencies {
    compileOnly(paperApi)
    "matrixStubCompileOnly"(paperApi)

    compileOnly(sourceSets["matrixStub"].output)

    implementation(project(":common"))
}

tasks.shadowJar {
    archiveBaseName.set("FreshwaterBanX-Bridge")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    // Only the :common module is bundled; matrixStub output is compileOnly and excluded.
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier.set("plain")
}
