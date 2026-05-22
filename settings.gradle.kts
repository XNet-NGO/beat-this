pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
rootProject.name = "beat-this"
include(":app")
include(":ai-services")
include(":plugin-host")
include(":audio-engine")
include(":mwengine")
project(":mwengine").projectDir = file("deps/MWEngine/mwengine")
