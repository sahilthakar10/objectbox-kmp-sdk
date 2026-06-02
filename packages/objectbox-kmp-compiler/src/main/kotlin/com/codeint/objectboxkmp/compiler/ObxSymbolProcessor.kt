package com.codeint.objectboxkmp.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.File
import java.util.Properties
import kotlin.reflect.KClass

private const val ENTITY_ANNOTATION = "com.codeint.objectboxkmp.annotations.ObxEntity"
private const val ID_ANNOTATION = "com.codeint.objectboxkmp.annotations.ObxId"

class ObxSymbolProcessor(
    private val environment: com.google.devtools.ksp.processing.SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger
    private var generated = false

    override fun process(resolver: Resolver): List<KSClassDeclaration> {
        if (generated) return emptyList()

        val isCommonMetadata = isCommonMetadataCompilation()
        val entities = resolver.getSymbolsWithAnnotation(ENTITY_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val invalid = entities.filterNot { it.validate() }
        if (invalid.isNotEmpty()) return invalid

        val androidRuntimeVisible = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("com.codeint.objectboxkmp.runtime.AndroidObxAdapter"),
        ) != null
        val specs = entities.mapNotNull(::toEntitySpec)
            .ifEmpty { readSchemaEntitySpecsOrEmptyForCommonMetadata(isCommonMetadata) }
            .sortedBy { it.className.simpleName }
        if (specs.isEmpty() && isCommonMetadata) {
            generated = true
            return emptyList()
        }
        if (specs.isEmpty() && !isCommonMetadata) {
            logger.error(
                "ObjectBox KMP could not find generated common schema metadata for this platform target. " +
                    "Apply the com.codeint.objectbox-kmp Gradle plugin to the KMP module that owns your @ObxEntity classes, " +
                    "or configure KSP arg 'objectboxKmpSchemaFile' and make platform KSP tasks depend on kspCommonMainKotlinMetadata.",
            )
            generated = true
            return emptyList()
        }
        generate(specs, androidRuntimeVisible)
        generated = true
        return emptyList()
    }

    private fun readSchemaEntitySpecsOrEmptyForCommonMetadata(isCommonMetadata: Boolean): List<EntitySpec> {
        if (!isCommonMetadata) return readSchemaEntitySpecs()

        val configuredSchemaPath = environment.options["objectboxKmpSchemaFile"]
            ?.takeIf { path -> path.isNotBlank() }
        if (configuredSchemaPath == null || !File(configuredSchemaPath).isFile) {
            logger.warn(
                "ObjectBox KMP common metadata KSP did not find any @ObxEntity classes and no previous schema metadata exists. " +
                    "No query fields were generated for this pass.",
            )
            return emptyList()
        }

        logger.warn(
            "ObjectBox KMP common metadata KSP did not receive entity symbols in this pass. " +
                "Regenerating query fields from existing schema metadata.",
        )
        return readSchemaEntitySpecs()
    }

    private fun toEntitySpec(declaration: KSClassDeclaration): EntitySpec? {
        val qualifiedName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        if (declaration.classKind != ClassKind.CLASS) {
            logger.error(
                "Invalid ObjectBox KMP entity '$qualifiedName': " +
                    "@ObxEntity can only be used on Kotlin classes.",
                declaration,
            )
            return null
        }

        if (Modifier.DATA !in declaration.modifiers) {
            logger.error(
                "Invalid ObjectBox KMP entity '$qualifiedName': " +
                    "entities must be data classes because generated adapters use copy(id = ...). " +
                    "Change it to 'data class ${declaration.simpleName.asString()}(...)'.",
                declaration,
            )
            return null
        }

        val constructor = declaration.primaryConstructor
        if (constructor == null) {
            logger.error(
                "Invalid ObjectBox KMP entity '$qualifiedName': entities must declare a primary constructor. " +
                    "Define persistent properties in the primary constructor, for example data class ${declaration.simpleName.asString()}(@ObxId val id: Long = 0, ...).",
                declaration,
            )
            return null
        }

        val constructorProperties = constructor.parameters
            .filter { parameter -> parameter.isVal || parameter.isVar }
            .toList()
        if (constructorProperties.isEmpty()) {
            logger.error(
                "Invalid ObjectBox KMP entity '$qualifiedName': no persistent constructor properties found. " +
                    "Declare properties with val/var in the primary constructor.",
                declaration,
            )
            return null
        }

        val declaredPropertiesByName = declaration.getAllProperties()
            .associateBy { property -> property.simpleName.asString() }
        val idProperties = constructorProperties
            .filter { parameter ->
                val parameterName = parameter.name?.asString()
                parameterName != null && declaredPropertiesByName[parameterName].hasObxIdAnnotation()
            }
            .toList()

        if (idProperties.size != 1) {
            logger.error(
                "Invalid ObjectBox KMP entity '$qualifiedName': " +
                    "expected exactly one @ObxId property, found ${idProperties.size}. " +
                    "Add one property like '@ObxId val id: Long = 0'.",
                declaration,
            )
            return null
        }

        val idProperty = idProperties.single()
        val idPropertyName = idProperty.name?.asString()
        if (idPropertyName == null) {
            logger.error("Invalid ObjectBox KMP entity '$qualifiedName': @ObxId constructor property must be named.", declaration)
            return null
        }
        val idType = idProperty.type?.resolve()?.declaration?.qualifiedName?.asString()
        if (idType != "kotlin.Long") {
            logger.error(
                "Invalid ObjectBox KMP id '${declaration.simpleName.asString()}.$idPropertyName': " +
                    "@ObxId must be Long, but found '$idType'. Use '@ObxId val $idPropertyName: Long = 0'.",
                idProperty,
            )
            return null
        }

        val className = declaration.toClassName()
        val entityName = declaration.annotations
            .firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == ENTITY_ANNOTATION }
            ?.arguments
            ?.firstOrNull { it.name?.asString() == "name" }
            ?.value
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: className.simpleName

        return EntitySpec(
            className = className,
            entityName = entityName,
            idPropertyName = idPropertyName,
            properties = constructorProperties
                .mapNotNull { property ->
                    val propertyName = property.name?.asString()
                    if (propertyName == null) {
                        logger.error(
                            "Invalid ObjectBox KMP entity '$qualifiedName': all persistent constructor properties must be named.",
                            declaration,
                        )
                        return@mapNotNull null
                    }
                    EntityProperty(
                        name = propertyName,
                        typeName = property.type.toTypeName(),
                        isId = propertyName == idPropertyName,
                    )
                }
                .toList(),
        )
    }

    private fun generate(
        entities: List<EntitySpec>,
        androidRuntimeVisible: Boolean,
    ) {
        val packageName = environment.options["objectboxKmpPackage"] ?: "com.codeint.objectboxkmp.generated"
        if (environment.options["objectboxKmpPackage"].isNullOrBlank()) {
            logger.warn(
                "ObjectBox KMP KSP arg 'objectboxKmpPackage' is not configured. " +
                    "Using fallback package '$packageName'. Apply the com.codeint.objectbox-kmp Gradle plugin " +
                    "or set objectBoxKmp.generatedPackage to your app/module package plus '.generated'.",
            )
        }
        val fieldType = ClassName("com.codeint.objectboxkmp.runtime", "ObxField")
        if (!validateEntitySpecs(entities)) return

        if (isCommonMetadataCompilation()) {
            generateFields(packageName, entities, fieldType)
            writeSchemaEntitySpecs(entities)
        } else {
            generateAdapters(packageName, entities, androidRuntimeVisible)
        }
    }

    private fun validateEntitySpecs(entities: List<EntitySpec>): Boolean {
        var valid = true
        entities
            .groupBy { entity -> entity.className.simpleName }
            .filterValues { matches -> matches.size > 1 }
            .forEach { (simpleName, matches) ->
                valid = false
                logger.error(
                    "ObjectBox KMP cannot generate adapters for multiple entities named '$simpleName': " +
                        matches.joinToString { entity -> entity.className.canonicalName } +
                        ". Use unique simple class names in one KMP module.",
                )
            }
        entities
            .groupBy { entity -> entity.entityName }
            .filterValues { matches -> matches.size > 1 }
            .forEach { (entityName, matches) ->
                valid = false
                logger.error(
                    "ObjectBox KMP entity name '$entityName' is used by multiple classes: " +
                        matches.joinToString { entity -> entity.className.canonicalName } +
                        ". Use unique @ObxEntity names.",
                )
            }

        entities.forEach { entity ->
            val idCount = entity.properties.count { property -> property.isId }
            val hasSingleLongId = idCount == 1 &&
                entity.properties.first { property -> property.isId }.typeName.copy(nullable = false).toString() == "kotlin.Long"
            entity.properties.forEach { property ->
                val supported = defaultValueForSupportedType(property.typeName.copy(nullable = false)) != null
                if (!supported) {
                    valid = false
                    logger.error(
                        "Unsupported ObjectBox KMP property type '${property.typeName}' on ${entity.className}.${property.name}. " +
                            "Supported property types are Long, Int, Short, Byte, Double, Float, Boolean, String, ByteArray, FloatArray, " +
                        "and nullable variants. Change the property type or add SDK support before using it.",
                    )
                }
            }

            if (!hasSingleLongId) {
                valid = false
                logger.error(
                    "Invalid ObjectBox KMP entity ${entity.className}: expected exactly one non-null Long @ObxId property. " +
                        "Use '@ObxId val id: Long = 0'.",
                )
            }
        }
        return valid
    }

    private fun writeSchemaEntitySpecs(entities: List<EntitySpec>) {
        val schemaFile = environment.options["objectboxKmpSchemaFile"]
            ?.takeIf { path -> path.isNotBlank() }
            ?.let(::File)
            ?: run {
                logger.error(
                    "ObjectBox KMP schema metadata output is not configured. " +
                        "Apply the com.codeint.objectbox-kmp Gradle plugin or set KSP arg 'objectboxKmpSchemaFile'.",
                )
                return
            }

        val properties = Properties()
        properties["format"] = "objectbox-kmp-schema-v1"
        properties["entity.count"] = entities.size.toString()
        entities.forEachIndexed { entityIndex, entity ->
            val entityPrefix = "entity.$entityIndex"
            properties["$entityPrefix.package"] = entity.className.packageName
            properties["$entityPrefix.simpleName"] = entity.className.simpleName
            properties["$entityPrefix.entityName"] = entity.entityName
            properties["$entityPrefix.idPropertyName"] = entity.idPropertyName
            properties["$entityPrefix.property.count"] = entity.properties.size.toString()
            entity.properties.forEachIndexed { propertyIndex, property ->
                val propertyPrefix = "$entityPrefix.property.$propertyIndex"
                properties["$propertyPrefix.name"] = property.name
                properties["$propertyPrefix.type"] = property.typeName.copy(nullable = false).toString()
                properties["$propertyPrefix.nullable"] = property.typeName.isNullable.toString()
                properties["$propertyPrefix.id"] = property.isId.toString()
            }
        }

        try {
            schemaFile.parentFile?.mkdirs()
            schemaFile.outputStream().use { output ->
                properties.store(output, "Generated by ObjectBox KMP common metadata KSP")
            }
        } catch (exception: Exception) {
            logger.error(
                "ObjectBox KMP failed to write schema metadata to '${schemaFile.path}'. " +
                    "Check that the Gradle build directory is writable. Cause: ${exception.message}",
            )
        }
    }

    private fun readSchemaEntitySpecs(): List<EntitySpec> {
        val configuredSchemaPath = environment.options["objectboxKmpSchemaFile"]
            ?.takeIf { path -> path.isNotBlank() }
        if (configuredSchemaPath == null) {
            logger.error(
                "ObjectBox KMP schema metadata input is not configured for this platform KSP task. " +
                    "Apply the com.codeint.objectbox-kmp Gradle plugin or set KSP arg 'objectboxKmpSchemaFile'.",
            )
            return emptyList()
        }

        val schemaFile = File(configuredSchemaPath)
        if (!schemaFile.isFile) {
            logger.error(
                "ObjectBox KMP schema metadata file was not found at '${schemaFile.path}'. " +
                    "The common KSP task must run before platform KSP. Apply the com.codeint.objectbox-kmp Gradle plugin " +
                    "or make this task depend on kspCommonMainKotlinMetadata.",
            )
            return emptyList()
        }

        val properties = Properties()
        try {
            schemaFile.inputStream().use(properties::load)
        } catch (exception: Exception) {
            logger.error(
                "ObjectBox KMP failed to read schema metadata from '${schemaFile.path}'. " +
                    "Delete the build directory and rebuild. Cause: ${exception.message}",
            )
            return emptyList()
        }
        if (properties.getProperty("format") != "objectbox-kmp-schema-v1") {
            logger.error(
                "Unsupported ObjectBox KMP schema metadata format in '${schemaFile.path}'. " +
                    "Delete the build directory and rebuild with matching objectbox-kmp-compiler and objectbox-kmp-gradle-plugin versions.",
            )
            return emptyList()
        }

        return try {
            val entityCount = properties.requiredInt("entity.count", schemaFile)
            (0 until entityCount).map { entityIndex ->
                val entityPrefix = "entity.$entityIndex"
                val className = ClassName(
                    properties.requiredString("$entityPrefix.package", schemaFile),
                    properties.requiredString("$entityPrefix.simpleName", schemaFile),
                )
                val propertyCount = properties.requiredInt("$entityPrefix.property.count", schemaFile)
                val entityProperties = (0 until propertyCount).map { propertyIndex ->
                    val propertyPrefix = "$entityPrefix.property.$propertyIndex"
                    EntityProperty(
                        name = properties.requiredString("$propertyPrefix.name", schemaFile),
                        typeName = typeNameFromSchema(
                            properties.requiredString("$propertyPrefix.type", schemaFile),
                            properties.requiredBoolean("$propertyPrefix.nullable", schemaFile),
                        ),
                        isId = properties.requiredBoolean("$propertyPrefix.id", schemaFile),
                    )
                }
                EntitySpec(
                    className = className,
                    entityName = properties.requiredString("$entityPrefix.entityName", schemaFile),
                    idPropertyName = properties.requiredString("$entityPrefix.idPropertyName", schemaFile),
                    properties = entityProperties,
                )
            }
        } catch (exception: IllegalArgumentException) {
            logger.error(
                "ObjectBox KMP schema metadata is invalid in '${schemaFile.path}'. " +
                    "Delete the build directory and rebuild. Cause: ${exception.message}",
            )
            emptyList()
        }
    }

    private fun typeNameFromSchema(
        rawType: String,
        nullable: Boolean,
    ): TypeName {
        val typeName = when (rawType) {
            "kotlin.Long" -> ClassName("kotlin", "Long")
            "kotlin.Int" -> ClassName("kotlin", "Int")
            "kotlin.Short" -> ClassName("kotlin", "Short")
            "kotlin.Byte" -> ClassName("kotlin", "Byte")
            "kotlin.Double" -> ClassName("kotlin", "Double")
            "kotlin.Float" -> ClassName("kotlin", "Float")
            "kotlin.Boolean" -> ClassName("kotlin", "Boolean")
            "kotlin.String" -> ClassName("kotlin", "String")
            "kotlin.ByteArray" -> ClassName("kotlin", "ByteArray")
            "kotlin.FloatArray" -> ClassName("kotlin", "FloatArray")
            else -> ClassName.bestGuess(rawType)
        }
        return typeName.copy(nullable = nullable)
    }

    private fun generateFields(
        packageName: String,
        entities: List<EntitySpec>,
        fieldType: ClassName,
    ) {
        val file = entities
            .fold(FileSpec.builder(packageName, "GeneratedObxFields")) { builder, entity ->
                builder.addType(createFieldMetadataType(entity, fieldType))
            }
            .build()

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true),
            packageName = packageName,
            fileName = "GeneratedObxFields",
        ).bufferedWriter().use { writer ->
            file.writeTo(writer)
        }
    }

    private fun generateAdapters(
        packageName: String,
        entities: List<EntitySpec>,
        androidRuntimeVisible: Boolean,
    ) {
        val backend = environment.options["objectboxKmpBackend"]
        val shouldGenerateAndroidAdapters = backend == "android" || (backend == null && androidRuntimeVisible)
        val registryClassName = ClassName(packageName, "GeneratedObxAdapters")
        val adapterType = ClassName("com.codeint.objectboxkmp.runtime", "ObxAdapter")
        val androidAdapterType = ClassName("com.codeint.objectboxkmp.runtime", "AndroidObxAdapter")
        val registryType = ClassName("com.codeint.objectboxkmp.runtime", "ObxAdapterRegistry")

        val file = FileSpec.builder(packageName, "GeneratedObxAdapters")
            .addType(
                TypeSpec.objectBuilder(registryClassName)
                    .addFunction(
                        FunSpec.builder("registry")
                            .returns(registryType)
                            .addCode(
                                CodeBlock.builder()
                                    .add("return %T(listOf(", registryType)
                                    .apply {
                                        entities.forEachIndexed { index, entity ->
                                            if (index > 0) add(", ")
                                            add("%LAdapter", entity.className.simpleName)
                                        }
                                    }
                                    .add("))\n")
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
        )

        val withAdapters = entities.fold(file) { builder, entity ->
            if (shouldGenerateAndroidAdapters) {
                builder
                    .addType(createObjectBoxEntityType(entity, packageName))
                    .addType(createAndroidAdapterType(entity, packageName, adapterType, androidAdapterType))
            } else {
                builder.addType(createCommonAdapterType(entity, adapterType))
            }
        }

        val dependencies = Dependencies(aggregating = true)
        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = packageName,
            fileName = "GeneratedObxAdapters",
        ).bufferedWriter().use { writer ->
            withAdapters.build().writeTo(writer)
        }
    }

    private fun isCommonMetadataCompilation(): Boolean {
        return environment.platforms.isEmpty() ||
            environment.platforms.size > 1 ||
            environment.platforms.any { platform ->
                val name = platform.platformName.lowercase()
                name == "common" ||
                    name == "metadata" ||
                    platform::class.simpleName == "UnknownPlatformInfo"
            }
    }

    private fun createCommonAdapterType(
        entity: EntitySpec,
        adapterType: ClassName,
    ): TypeSpec {
        val type = entity.className
        return baseAdapterBuilder(entity, type, adapterType)
            .build()
    }

    private fun createFieldMetadataType(
        entity: EntitySpec,
        fieldType: ClassName,
    ): TypeSpec {
        val type = TypeSpec.objectBuilder("${entity.className.simpleName}Fields")
        entity.properties.forEach { property ->
            type.addProperty(
                PropertySpec.builder(
                    property.name,
                    fieldType.parameterizedBy(entity.className, property.typeName),
                )
                    .initializer(
                        "%T(%S) { entity: %T -> entity.%L }",
                        fieldType,
                        property.name,
                        entity.className,
                        property.name,
                    )
                    .build(),
            )
        }
        return type.build()
    }

    private fun createAndroidAdapterType(
        entity: EntitySpec,
        packageName: String,
        adapterType: ClassName,
        androidAdapterType: ClassName,
    ): TypeSpec {
        val type = entity.className
        val objectBoxEntityType = entity.objectBoxEntityClassName(packageName)
        return baseAdapterBuilder(entity, type, adapterType)
            .addSuperinterface(androidAdapterType.parameterizedBy(type, objectBoxEntityType))
            .addProperty(
                PropertySpec.builder(
                    "entityClass",
                    Class::class.asClassName().parameterizedBy(objectBoxEntityType),
                    OVERRIDE,
                )
                    .initializer("%T::class.java", objectBoxEntityType)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("toDatabaseEntity")
                    .addModifiers(OVERRIDE)
                    .addParameter("model", type)
                    .returns(objectBoxEntityType)
                    .addCode(createToDatabaseEntityCode(entity, objectBoxEntityType))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("fromDatabaseEntity")
                    .addModifiers(OVERRIDE)
                    .addParameter("entity", objectBoxEntityType)
                    .returns(type)
                    .addCode(createFromDatabaseEntityCode(entity))
                    .build(),
            )
            .build()
    }

    private fun baseAdapterBuilder(
        entity: EntitySpec,
        type: ClassName,
        adapterType: ClassName,
    ): TypeSpec.Builder {
        return TypeSpec.objectBuilder("${entity.className.simpleName}Adapter")
            .addSuperinterface(adapterType.parameterizedBy(type))
            .addProperty(
                PropertySpec.builder("type", KClass::class.asClassName().parameterizedBy(type), OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return %T::class", type)
                            .build(),
                    )
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("entityName", String::class, OVERRIDE)
                    .initializer("%S", entity.entityName)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("getId")
                    .addModifiers(OVERRIDE)
                    .addParameter("entity", type)
                    .returns(LONG)
                    .addStatement("return entity.%L", entity.idPropertyName)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("withId")
                    .addModifiers(OVERRIDE)
                    .addParameter("entity", type)
                    .addParameter("id", LONG)
                    .returns(type)
                    .addCode(createWithIdCode(entity))
                    .build(),
            )
    }

    private fun createObjectBoxEntityType(entity: EntitySpec, packageName: String): TypeSpec {
        val entityAnnotation = ClassName("io.objectbox.annotation", "Entity")
        val idAnnotation = ClassName("io.objectbox.annotation", "Id")
        val constructor = FunSpec.constructorBuilder()

        entity.properties.forEach { property ->
            constructor.addParameter(
                ParameterSpec.builder(property.name, property.typeName)
                    .defaultValue(defaultValueFor(property))
                    .build(),
            )
        }

        val type = TypeSpec.classBuilder(entity.objectBoxEntityClassName(packageName))
            .addAnnotation(entityAnnotation)
            .primaryConstructor(constructor.build())

        entity.properties.forEach { property ->
            val propertySpec = PropertySpec.builder(property.name, property.typeName)
                .mutable(true)
                .initializer(property.name)

            if (property.isId) {
                propertySpec.addAnnotation(idAnnotation)
            }

            type.addProperty(propertySpec.build())
        }

        return type.build()
    }

    private fun createWithIdCode(entity: EntitySpec): CodeBlock {
        return CodeBlock.builder()
            .addStatement("return entity.copy(%L = id)", entity.idPropertyName)
            .build()
    }

    private fun createToDatabaseEntityCode(
        entity: EntitySpec,
        objectBoxEntityType: ClassName,
    ): CodeBlock {
        return CodeBlock.builder()
            .add("return %T(\n", objectBoxEntityType)
            .indent()
            .apply {
                entity.properties.forEach { property ->
                    add("%L = model.%L,\n", property.name, property.name)
                }
            }
            .unindent()
            .add(")\n")
            .build()
    }

    private fun createFromDatabaseEntityCode(entity: EntitySpec): CodeBlock {
        return CodeBlock.builder()
            .add("return %T(\n", entity.className)
            .indent()
            .apply {
                entity.properties.forEach { property ->
                    add("%L = entity.%L,\n", property.name, property.name)
                }
            }
            .unindent()
            .add(")\n")
            .build()
    }

    private fun defaultValueFor(property: EntityProperty): CodeBlock {
        if (property.typeName.isNullable) return CodeBlock.of("null")

        return defaultValueForSupportedType(property.typeName) ?: throw IllegalStateException(
            "Unsupported ObjectBox KMP property type '${property.typeName}' on '${property.name}' was not validated before code generation.",
        )
    }

    private fun defaultValueForSupportedType(typeName: TypeName): CodeBlock? {
        return when (typeName.copy(nullable = false).toString()) {
            "kotlin.Long" -> CodeBlock.of("0L")
            "kotlin.Int" -> CodeBlock.of("0")
            "kotlin.Short" -> CodeBlock.of("0")
            "kotlin.Byte" -> CodeBlock.of("0")
            "kotlin.Double" -> CodeBlock.of("0.0")
            "kotlin.Float" -> CodeBlock.of("0f")
            "kotlin.Boolean" -> CodeBlock.of("false")
            "kotlin.String" -> CodeBlock.of("%S", "")
            "kotlin.ByteArray" -> CodeBlock.of("byteArrayOf()")
            "kotlin.FloatArray" -> CodeBlock.of("floatArrayOf()")
            else -> null
        }
    }
}

private fun KSPropertyDeclaration?.hasObxIdAnnotation(): Boolean {
    return this?.annotations?.any { annotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == ID_ANNOTATION
    } == true
}

private data class EntitySpec(
    val className: ClassName,
    val entityName: String,
    val idPropertyName: String,
    val properties: List<EntityProperty>,
) {
    fun objectBoxEntityClassName(packageName: String): ClassName =
        ClassName(packageName, "${className.simpleName}ObjectBoxEntity")
}

private data class EntityProperty(
    val name: String,
    val typeName: com.squareup.kotlinpoet.TypeName,
    val isId: Boolean,
)

private fun Properties.requiredString(
    key: String,
    schemaFile: File,
): String {
    return getProperty(key)
        ?: throw IllegalArgumentException("missing key '$key' in ${schemaFile.path}")
}

private fun Properties.requiredInt(
    key: String,
    schemaFile: File,
): Int {
    return requiredString(key, schemaFile).toIntOrNull()
        ?: throw IllegalArgumentException("invalid integer key '$key' in ${schemaFile.path}")
}

private fun Properties.requiredBoolean(
    key: String,
    schemaFile: File,
): Boolean {
    return when (val value = requiredString(key, schemaFile)) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("invalid boolean key '$key' in ${schemaFile.path}: '$value'")
    }
}
