plugins {
  alias(libs.plugins.kotlinMultiplatform).apply(false)
  alias(libs.plugins.androidKotlinMultiplatformLib).apply(false)
  alias(libs.plugins.mavenPublish).apply(false)
  alias(libs.plugins.dokka)
}

if (rootProject.findProperty("snapshot") == "true") {
  allprojects {
    version = "$version-SNAPSHOT"
  }
}

dependencies {
  dokka(project(":apollo-sse"))
}

dokka {
  dokkaPublications.html {
    moduleName.set(rootProject.name)
    outputDirectory.set(layout.buildDirectory.dir("docs/html"))
  }
}
