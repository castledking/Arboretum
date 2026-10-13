plugins {
    java
    `java-library`
}

val core_java_version: String by project

base {
    archivesName.set("arboretum-core")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(core_java_version.toInt()))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Gson is `compileOnly` on purpose. Both target platforms already provide it on the runtime
    // classpath: Minecraft bundles it (Fabric and Paper both inherit it), so Arboretum never has
    // to shade or relocate a JSON library. Tests need a real implementation, so it is also on the
    // test classpath. See docs/architecture.md "Dependency policy".
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation("com.google.code.gson:gson:2.11.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// --- the bundled data index ---------------------------------------------------------------
// A jar cannot list a directory, so every platform finds the bundled species, collections and
// templates through data/arboretum/index.json, written here from what src/main/resources holds.
// Adding a species is dropping its files in; nothing has to be registered by hand.
val bundledData = layout.projectDirectory.dir("src/main/resources/data/arboretum")
val indexDir = layout.buildDirectory.dir("generated/arboretum-index")
val writeDataIndex by tasks.registering {
    description = "Writes data/arboretum/index.json from the bundled species, collections and templates"
    inputs.dir(bundledData)
    outputs.dir(indexDir)
    doLast {
        val root = bundledData.asFile
        fun list(sub: String, ext: String) = root.resolve(sub).walkTopDown()
            .filter { it.isFile && it.name.endsWith(ext) }
            .map { "data/arboretum/" + it.relativeTo(root).invariantSeparatorsPath }
            .sorted().toList()
        fun array(items: List<String>) = items.joinToString(",\n    ", "[\n    ", "\n  ]") { "\"" + it + "\"" }
        val out = indexDir.get().file("data/arboretum/index.json").asFile
        out.parentFile.mkdirs()
        out.writeText("{\n  \"species\": " + array(list("species", ".json"))
            + ",\n  \"collections\": " + array(list("collections", ".json"))
            + ",\n  \"templates\": " + array(list("templates", ".nbt")) + "\n}\n")
    }
}
sourceSets.main {
    resources.srcDir(writeDataIndex)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = core_java_version.toInt()
    options.compilerArgs.add("-Xlint:all,-serial,-processing")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}