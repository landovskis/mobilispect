pluginManagement {
  repositories {
    maven { url = uri("file:///tmp/local-maven-repo") }
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "mobilispect-backend"
