// Shared, dependency-free protocol classes used by the Velocity plugin, the Paper bridge,
// and the Waterfall relay. Compiled to Java 8 bytecode because Waterfall may run on Java 8,
// and these classes are shaded into the Waterfall jar. Higher JVMs (17/21) load them fine too.

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}
