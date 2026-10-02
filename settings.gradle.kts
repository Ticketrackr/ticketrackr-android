pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "ticketrackr-android"
// The SDK (published as com.ticketrackr:support-android) and an example app that shows both ways in.
include(":support", ":example")
