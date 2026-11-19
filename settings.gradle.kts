pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "EmberPlayer"
include(":app")

if(providers.gradleProperty("includePerformanceTests").orNull=="true")include(":benchmark")
