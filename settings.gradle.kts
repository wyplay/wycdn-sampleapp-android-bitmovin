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
        maven {
            name = "wyplayRepositoryReleases"
            url = uri("https://maven.wyplay.com/releases")
            credentials(PasswordCredentials::class)
        }
    }
}

rootProject.name = "wycdn-sampleapp-android"
include(":wycdn-sampleapp")
