// Arboretum - a botanical exploration and world-generation project by CASTLED CODEX.
//
// Module layout:
//   :arboretum-core   platform-neutral botanical core. Zero Minecraft, zero Paper, zero Fabric
//                     classes. Holds the species catalogue, collection resolution, the world
//                     configuration schema, the structure-NBT reader and the placement planner.
//                     This is the module every platform adapter shares.
//   :arboretum-fabric Fabric adapter. Owns worldgen registration, networking and client screens.
pluginManagement {
    val loom_version: String by settings
    plugins {
        // 26.x is unobfuscated: the non-remapping Loom plugin builds against Mojang names as-is.
        id("net.fabricmc.fabric-loom") version loom_version
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "Fabric"
                    url = uri("https://maven.fabricmc.net")
                }
            }
            filter { includeGroupAndSubgroups("net.fabricmc") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Arboretum"

include("arboretum-core")
include("arboretum-fabric")