import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.objectbox)
    id("com.codeint.objectbox-kmp")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    val xcf = XCFramework("ObjectBoxKmpSampleShared")

    iosArm64 {
        binaries.framework {
            baseName = "ObjectBoxKmpSampleShared"
            isStatic = true
            xcf.add(this)
        }
    }
    iosX64 {
        binaries.framework {
            baseName = "ObjectBoxKmpSampleShared"
            isStatic = true
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ObjectBoxKmpSampleShared"
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":packages:objectbox-kmp-annotations"))
                implementation(project(":packages:objectbox-kmp-runtime"))
            }
        }
    }
}

android {
    namespace = "com.codeint.objectboxkmp.sample.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

objectBoxKmp {
    generatedPackage.set("com.codeint.objectboxkmp.sample.shared.generated")
    compilerProjectPath.set(":packages:objectbox-kmp-compiler")
}

kapt {
    arguments {
        arg("objectbox.myObjectBoxPackage", "com.codeint.objectboxkmp.sample.shared.generated")
    }
}

dependencies {
    add("kapt", libs.objectbox.processor)
}
