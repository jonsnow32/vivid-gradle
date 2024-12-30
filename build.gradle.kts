plugins {
  kotlin("jvm") version "2.0.21"
  id("java-gradle-plugin")
  id("maven-publish")
}

group = "cloud.app.vvf.plugin"
version = "1.0-SNAPSHOT"

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
  plugins {
    create("vvfPlugin") {
      id = group.toString()
      implementationClass = "cloud.app.vvf.VvfPlugin"
    }
  }
}
publishing {
  repositories {
    mavenLocal()
  }
}

tasks.test {
  useJUnitPlatform()
}
