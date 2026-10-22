pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "citoyen-2027"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:model")
include(":core:testing")
include(":core:designsystem")
include(":core:ui")
include(":core:ui-testing")
include(":core:database")
include(":core:data")
include(":core:network")
include(":feature:candidates")
include(":feature:candidate")
include(":feature:compare")
include(":feature:ask")
include(":feature:institutions")
include(":feature:about")
include(":rag")
include(":llm")
include(":benchmarks")
include(":tools:neutrality-check")
include(":tools:rag-eval")
