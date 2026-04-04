import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    jvm()
    
    // Force the project to use JDK 21 toolchain
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        // Removed -Xjdk-release=21 as jvmToolchain(21) handles this more robustly
        freeCompilerArgs.add("-Xadd-modules=java.smartcardio")
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-modules=java.smartcardio")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=java.smartcardio")
}

compose.desktop {
    application {
        mainClass = "com.example.nps_nfc_desktop.MainKt"

        jvmArgs(
            "--add-modules=java.smartcardio"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "NPS NFC Desktop"
            packageVersion = "1.0.0"

            modules("java.smartcardio")

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icon.icns"))
            }
        }
    }
}
