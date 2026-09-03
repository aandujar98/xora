pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "sora"

include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:display")
include(":core:datastore")
include(":core:database")
include(":core:scanner")
include(":core:launcher")
include(":core:input")
include(":core:scraper")
include(":core:libretro")
include(":core:retroachievements")
include(":feature:home")
include(":feature:settings")
