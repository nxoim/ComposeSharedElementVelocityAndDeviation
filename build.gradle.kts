import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.compose).apply(false)
    alias(libs.plugins.android.application).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.android.library).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.dokka).apply(false)
    alias(libs.plugins.compose.compiler).apply(false)
}


tasks.register<Copy>("setUpGitHooks") {
    group = "help"
    from("$rootDir/.hooks")
    into("$rootDir/.git/hooks")
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
//            if (project.findProperty("composeCompilerReports") == "true") {
                freeCompilerArgs += listOf(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir.absolutePath}/compose_compiler"
                )
//            }
//            if (project.findProperty("composeCompilerMetrics") == "true") {
                freeCompilerArgs += listOf(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir.absolutePath}/compose_compiler"
                )
//            }
        }
    }
}
