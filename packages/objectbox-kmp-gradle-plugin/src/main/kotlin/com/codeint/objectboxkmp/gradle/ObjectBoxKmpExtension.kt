package com.codeint.objectboxkmp.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property

abstract class ObjectBoxKmpExtension internal constructor(
    project: Project,
) {
    val generatedPackage: Property<String> = project.objects.property(String::class.java)
        .convention(defaultGeneratedPackage(project))

    val compilerDependency: Property<String> = project.objects.property(String::class.java)
        .convention("com.codeint.objectboxkmp:objectbox-kmp-compiler")

    val compilerProjectPath: Property<String> = project.objects.property(String::class.java)
}

private fun defaultGeneratedPackage(project: Project): String {
    val group = project.group.toString().takeIf { value ->
        value.isNotBlank() && value != "unspecified"
    }
    val base = group ?: project.name
        .replace(Regex("[^A-Za-z0-9_]"), "_")
        .trim('_')
        .ifBlank { "objectboxkmp" }
    return "$base.generated"
}
