pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        if (providers.gradleProperty("useMavenCentralMirror").orNull == "true") {
            maven { url = uri("https://gcp.mirrors.maven.org/maven2") }
        }
        mavenCentral()
    }
}
rootProject.name = "EmberPlayer"
include(":app")
