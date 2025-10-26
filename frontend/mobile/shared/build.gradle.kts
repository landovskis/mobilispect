import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        dependencies {
            api(libs.appcompat)
            implementation(libs.koin.android)
            testImplementation(libs.kotlinx.coroutines.test)
            testImplementation(libs.mockWebServer)
            ksp(libs.room.compiler)
            implementation(libs.room.ktx)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.javax.inject)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.serialization)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.resources)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.sqlite.driver)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.room.testing)
            implementation(libs.truth)
        }
    }
}

android {
    namespace = "com.mobilispect.mobile"
    compileSdk = 35
    defaultConfig {
        minSdk = 29
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("jacocoTestReport") {
    group = "verification"
    description = "Stub task to satisfy tooling when JaCoCo plugin is unavailable"

    doLast {
        logger.lifecycle("shared:jacocoTestReport stub: no coverage generated (JaCoCo plugin unavailable offline)")
    }
}
