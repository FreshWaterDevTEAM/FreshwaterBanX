plugins {
    java
}

allprojects {
    group = "io.freshwater.banx"
    version = "1.1.0"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Target Java 17 bytecode regardless of the (newer) JDK used to run the build.
        options.release.set(17)
    }
}
