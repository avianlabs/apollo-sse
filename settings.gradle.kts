pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("""com\.android\..*""")
        includeGroupByRegex("""androidx\..*""")
        includeGroupByRegex("""com\.google\..*""")
      }
    }
    mavenCentral()
  }
}

rootProject.name = "apollo-sse"

include(":apollo-sse")
