plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLib)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.dokka)
  signing
}

group = "net.avianlabs.apollo"
version = properties["version"] as String

kotlin {
  applyDefaultHierarchyTemplate()
  explicitApi()
  jvmToolchain(21)

  jvm()

  @Suppress("DEPRECATION") // AGP 9.x: 'android' overload is ambiguous with KGP's; androidLibrary still works
  androidLibrary {
    namespace = "net.avianlabs.apollo.sse"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    minSdk = libs.versions.androidMinSdk.get().toInt()
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    withHostTestBuilder { }
  }

  iosArm64()
  iosSimulatorArm64()
  iosX64()
  macosArm64()

  sourceSets {
    val jvmAndroidMain by creating {
      dependsOn(commonMain.get())
    }
    jvmMain { dependsOn(jvmAndroidMain) }
    androidMain { dependsOn(jvmAndroidMain) }

    commonMain {
      dependencies {
        api(libs.apollo.runtime)
        implementation(libs.coroutinesCore)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlinTest)
        implementation(libs.coroutinesTest)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.okhttp.mockWebServer)
      }
    }
  }
}

dokka {
  dokkaSourceSets.configureEach {
    documentedVisibilities.set(setOf(org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier.Public))
    skipEmptyPackages.set(true)
  }
}

signing {
  useGpgCmd()
}

publishing {
  repositories {
    mavenLocal()
    maven {
      name = "GitHubPackages"
      url = uri("https://maven.pkg.github.com/avianlabs/apollo-sse")
      credentials {
        username = System.getenv("GITHUB_ACTOR")
        password = System.getenv("GITHUB_TOKEN")
      }
    }
  }
}

mavenPublishing {
  publishToMavenCentral()
  if (rootProject.findProperty("signPublications") != "false") {
    signAllPublications()
  }
  coordinates("net.avianlabs.apollo", "apollo-sse", project.version.toString())

  pom {
    name.set("apollo-sse")
    description.set("Server-Sent Events NetworkTransport for Apollo Kotlin")
    url.set("https://github.com/avianlabs/apollo-sse")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        name.set("Avian Labs Engineers")
        email.set("engineering@avianlabs.net")
      }
    }
    scm {
      url.set("https://github.com/avianlabs/apollo-sse")
      connection.set("scm:git:git://github.com/avianlabs/apollo-sse.git")
      developerConnection.set("scm:git:ssh://git@github.com/avianlabs/apollo-sse.git")
    }
  }
}
