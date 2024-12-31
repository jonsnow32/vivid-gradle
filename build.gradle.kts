plugins {
  kotlin("jvm") version "2.0.21"
  id("java-gradle-plugin")
  id("maven-publish")
  id("com.gradle.plugin-publish") version "1.3.0"
}

group = "cloud.app.vvf"
version = "1.0.0"

repositories {
  mavenCentral()
  google()
  maven("https://jitpack.io")
}

dependencies {
  implementation(kotlin("stdlib", kotlin.coreLibrariesVersion))
  compileOnly(gradleApi())
  compileOnly("com.google.guava:guava:33.3.1-jre")
  compileOnly("com.android.tools:sdk-common:31.7.2")
  compileOnly("com.android.tools.build:gradle:8.6.1")
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")

  implementation("org.ow2.asm:asm:9.4")
  implementation("org.ow2.asm:asm-tree:9.4")
  //adb tool
  implementation("com.github.vidstige:jadb:master-SNAPSHOT")

  testImplementation(kotlin("test"))
}

kotlin {
  jvmToolchain(17)
}


gradlePlugin {
  website = "https://github.com/jonsnow32/vivid-gradle"
  vcsUrl = "https://github.com/jonsnow32/vivid-gradle.git"
  plugins {
    create("vvfPlugin") {
      displayName = "VVF gradle plugin"
      description = "Automate building the VVF extension."
      id = "cloud.app.vvf.plugin"
      tags = listOf("testing", "integrationTesting", "vvf extensions")
      implementationClass = "cloud.app.vvf.VvfPlugin"
    }
  }
}

publishing {
  publications {
    create<MavenPublication>("vvfPlugin") {
      groupId = "cloud.app.vvf" // Matches the `group` in your build script
      artifactId = "vvf-plugin" // Simpler artifact ID
      version = "1.0.0"

      from(components["java"])
    }
  }
  repositories {
    mavenLocal() // For local testing
  }
}

tasks.test {
  useJUnitPlatform()
}
tasks.withType<Jar> {
  exclude("**/*.md", "**/test/**", "**/docs/**")
}
