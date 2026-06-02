pluginManagement {
    includeBuild("packages/objectbox-kmp-gradle-plugin")
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

rootProject.name = "ObjectBoxKmpSdk"
include(":packages:objectbox-kmp-annotations")
include(":packages:objectbox-kmp-runtime")
include(":packages:objectbox-kmp-compiler")
include(":apps:android")
include(":apps:shared")
