// The Fabric adapter for Minecraft 26.3. Thin by design: it turns Minecraft's world-generation
// context into the core's BlockQuery, runs the core planner, and writes the plan it returns.
// Species, templates, validation and placement rules all live in :arboretum-core.
plugins {
    java
    id("net.fabricmc.fabric-loom")
}

val minecraft_26_3: String by project
val fabric_loader_version: String by project
val fabric_api_26_3: String by project
val mod_version: String by project

base {
    archivesName.set("arboretum-fabric")
}
version = mod_version

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_26_3")
    implementation("net.fabricmc:fabric-loader:$fabric_loader_version")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabric_api_26_3")
    implementation(project(":arboretum-core"))
    // The core jar ships inside the mod (jar-in-jar): one file to install.
    include(project(":arboretum-core"))
}

java {
    // Minecraft 26.x needs Java 25.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    inputs.property("version", mod_version)
    filesMatching("fabric.mod.json") {
        expand("version" to mod_version)
    }
}
