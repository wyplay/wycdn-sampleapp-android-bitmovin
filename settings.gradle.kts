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
        maven {
            name = "bitmovinRepositoryReleases"
            url = uri("https://artifacts.bitmovin.com/artifactory/public-releases")
        }
    }

    // Allow to override wycdnService version from gradle.properties or from command-line
    // (eg: `./gradlew build -P wycdnService=x.x.x`)
    val wycdnService = extra.properties["wycdnService"] as String?
    if (wycdnService != null) {
        versionCatalogs {
            create("libs") {
                version("wycdnService", wycdnService)
            }
        }
    }
}

rootProject.name = "wycdn-sampleapp-android-bitmovin"
include(":wycdn-sampleapp")
project(":wycdn-sampleapp").name = "wycdn-sampleapp-bitmovin"
