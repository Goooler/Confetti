pluginManagement {
    listOf(repositories, dependencyResolutionManagement.repositories).forEach {
        it.apply {
            mavenCentral()
            google()
            maven(url = "https://androidx.dev/storage/compose-compiler/repository")
            maven(url = "https://maven.pkg.jetbrains.space/public/p/compose/dev")
            gradlePluginPortal {
                content {
                }
            }
            maven {
                url = uri("https://jitpack.io")
                content {
                    includeGroup("com.github.QuickBirdEng.kotlin-snapshot-testing")
                }
            }
            exclusiveContent {
                forRepository {
                    maven {
                        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    }
                }
                filter {
                    includeGroup("com.apollographql.apollo3")
                }
            }
        }
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                // https://github.com/GoogleCloudPlatform/app-gradle-plugin/issues/397
                "com.google.cloud.tools.appengine" -> useModule("com.google.cloud.tools:appengine-gradle-plugin:${requested.version}")
            }
        }
    }
}

plugins {
    id("com.gradle.enterprise") version "3.12.6"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishAlways()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Confetti"
include(":androidApp")
include(":androidBenchmark")
include(":shared")
include(":backend")
include(":backend:service-graphql")
include(":backend:datastore")
include(":backend:service-import")
include(":landing-page")
include(":wearApp")
include(":wearBenchmark")
include(":compose-desktop")

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_17)) {
    "This project needs to be run with Java 17 or higher (found: ${JavaVersion.current()})."
}
