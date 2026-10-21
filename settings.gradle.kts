// Meteor Orbiter, standalone project.
//
//   gradlew build        -> build the jar for the target in version.properties
//   gradlew runClient    -> launch a dev client with the addon loaded
//   gradlew printTarget  -> show the Minecraft/Meteor pair this project targets
//
// The other supported Minecraft versions live in versions/<target>/. Each one of
// those is a standalone project too, with its own wrapper and its own sources,
// so any of them can be copied out or checked out on its own.

pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "orbiter"