import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

gradlePlugin {
    plugins {
        create("objectBoxKmp") {
            id = "com.codeint.objectbox-kmp"
            implementationClass = "com.codeint.objectboxkmp.gradle.ObjectBoxKmpGradlePlugin"
        }
    }
}

dependencies {
    compileOnly(libs.ksp.gradle)
    compileOnly(libs.kotlin.gradle.plugin)
}
