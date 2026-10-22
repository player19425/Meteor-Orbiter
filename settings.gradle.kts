// Meteor Orbiter for Minecraft 1.21.11, standalone project.
//
//   gradlew build        -> build the jar
//   gradlew runClient    -> launch a dev client with the addon loaded
//   gradlew printTarget  -> show the Minecraft/Meteor pair this project targets
//
// Nothing is shared with the rest of the repository. This folder can be copied
// out on its own and it still builds.

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

rootProject.name = "orbiter-1.21.11"