plugins {
    //trick: for the same plugin versions in all sub-modules
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
    alias(libs.plugins.dependencyCheck)
}

// OWASP Dependency Check Configuration (Constitutional Security Requirement)
configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    formats = listOf("HTML", "JSON")
    suppressionFile = "${project.rootDir}/owasp-suppressions.xml"
    failBuildOnCVSS = 7.0f
    analyzers.apply {
        assemblyEnabled = false
        nugetconfEnabled = false
        nodeEnabled = false
    }
}
