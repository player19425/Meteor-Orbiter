// Meteor Orbiter, standalone build.
//
// This project is self-contained: `gradlew build` here produces the jar for the
// Minecraft version named in version.properties. Every other supported version
// lives in versions/<target>/ and is its own standalone project with its own
// wrapper and its own sources.

import java.util.Properties

plugins {
    alias(libs.plugins.fabric.loom)
}

val targetProps = Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}

fun target(key: String): String =
    targetProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException("version.properties is missing '$key'")

fun targetOrNull(key: String): String? =
    targetProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val mc = target("mc")

version = "${providers.gradleProperty("mod_version").get()}-$mc"
group = providers.gradleProperty("maven_group").get()

base {
    archivesName = providers.gradleProperty("archives_base_name").get()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(target("jdk").toInt()))
    }
}

repositories {
    maven {
        name = "meteor-releases"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:${target("loader")}")
    implementation("meteordevelopment:meteor-client:${target("meteor")}")

    // Compile-time only. The merged Minecraft jar of these versions still
    // exposes Fabric API types, so they have to be on the classpath.
    targetOrNull("resource-loader")?.let {
        implementation("net.fabricmc.fabric-api:fabric-resource-loader-v1:$it")
    }
    targetOrNull("fabric-api-base")?.let {
        implementation("net.fabricmc.fabric-api:fabric-api-base:$it")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked", "-Xmaxerrs", "10000"))
}

tasks.processResources {
    val compatible = "~" + mc.replace("-pre-", "-pre.").replace("-rc-", "-rc.")

    val tokens = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to compatible,
        "jdk_version" to target("jdk"),
        "jdk_maj" to target("jdk")
    )

    inputs.properties(tokens)
    filesMatching("fabric.mod.json") {
        expand(tokens)
    }
    filesMatching("orbiter.mixins.json") {
        expand(tokens)
    }
}

tasks.jar {
    val archiveName = providers.gradleProperty("archives_base_name").get()
    inputs.property("archivesName", archiveName)
    from(layout.projectDirectory.file("LICENSE")) {
        rename { "${it}_$archiveName" }
    }
}

tasks.register("printTarget") {
    val summary = "Minecraft $mc | Meteor ${target("meteor")} | JDK ${target("jdk")}"
    doLast { println("${project.path}: $summary") }
}