pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TamilScripture"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:rust")
include(":core:data")
include(":core:media")
include(":feature:home")
include(":feature:reader")
include(":feature:plans")
include(":feature:study")
include(":feature:search")
