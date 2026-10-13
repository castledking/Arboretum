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