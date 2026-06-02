package com.codeint.objectboxkmp.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ObjectBoxKmpGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "objectBoxKmp",
            ObjectBoxKmpExtension::class.java,
            project,
        )

        project.pluginManager.apply("com.google.devtools.ksp")

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            configureKotlin(project)
        }
        project.afterEvaluate {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                throw GradleException(
                    "ObjectBox KMP plugin must be applied to a Kotlin Multiplatform module. " +
                        "Apply 'org.jetbrains.kotlin.multiplatform' before 'com.codeint.objectbox-kmp' in ${project.path}.",
                )
            }
        }

        project.plugins.withId("com.google.devtools.ksp") {
            configureKsp(project, extension)
        }
    }

    private fun configureKotlin(project: Project) {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
            kotlin.sourceSets.named("commonMain") { sourceSet ->
                sourceSet.kotlin.srcDir(
                    project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"),
                )
            }
        }
    }

    private fun configureKsp(
        project: Project,
        extension: ObjectBoxKmpExtension,
    ) {
        val schemaFile = project.layout.buildDirectory.file("generated/objectbox-kmp/schema.properties")

        project.afterEvaluate {
            validateExtension(project, extension)
            project.extensions.configure(KspExtension::class.java) { ksp ->
                ksp.arg("objectboxKmpPackage", extension.generatedPackage.get())
                ksp.arg("objectboxKmpSchemaFile", schemaFile.get().asFile.absolutePath)
            }
            addKspCompilerDependencies(project, extension)
            wireGeneratedSchemaTasks(project)
        }
    }

    private fun validateExtension(
        project: Project,
        extension: ObjectBoxKmpExtension,
    ) {
        val generatedPackage = extension.generatedPackage.get()
        if (!generatedPackage.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*"""))) {
            throw GradleException(
                "Invalid objectBoxKmp.generatedPackage '$generatedPackage' in ${project.path}. " +
                    "Use a valid Kotlin package, for example '${project.group}.generated'.",
            )
        }

    }

    private fun addKspCompilerDependencies(
        project: Project,
        extension: ObjectBoxKmpExtension,
    ) {
        val dependency = if (extension.compilerProjectPath.isPresent) {
            val compilerPath = extension.compilerProjectPath.get()
            if (project.rootProject.findProject(compilerPath) == null) {
                throw GradleException(
                    "ObjectBox KMP compilerProjectPath '$compilerPath' does not exist. " +
                        "Use a valid included project path or remove compilerProjectPath and set compilerDependency to the published compiler artifact.",
                )
            }
            project.dependencies.project(mapOf("path" to compilerPath))
        } else {
            val compilerDependency = extension.compilerDependency.get()
            if (compilerDependency.isBlank()) {
                throw GradleException(
                    "ObjectBox KMP compiler dependency is missing. " +
                        "Set objectBoxKmp.compilerDependency to the published compiler artifact or compilerProjectPath to a local compiler project.",
                )
            }
            project.dependencies.create(compilerDependency)
        }

        val kspConfigurationNames = project.configurations.names
            .filter { name ->
                name == "kspCommonMainMetadata" ||
                    name.matches(Regex("""ksp(Kotlin)?[A-Z].*"""))
            }
        if (kspConfigurationNames.isEmpty()) {
            throw GradleException(
                "ObjectBox KMP could not find any KSP configurations in ${project.path}. " +
                    "Make sure the KSP plugin is applied and Kotlin Multiplatform targets are configured before project evaluation completes.",
            )
        }
        kspConfigurationNames.forEach { configurationName ->
            project.dependencies.add(configurationName, dependency)
        }
    }

    private fun wireGeneratedSchemaTasks(project: Project) {
        val commonKspTaskName = "kspCommonMainKotlinMetadata"
        project.tasks.matching { task ->
            task.name != commonKspTaskName &&
                (
                    task.name.startsWith("ksp") ||
                        task.name.startsWith("compileKotlin") ||
                        task.name.matches(Regex("""compile.+KotlinAndroid"""))
                    )
        }.configureEach { task ->
            task.dependsOn(commonKspTaskName)
        }
    }
}
