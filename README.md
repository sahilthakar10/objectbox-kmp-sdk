# ObjectBox KMP SDK

ObjectBox KMP SDK is a Kotlin Multiplatform wrapper for building ObjectBox-powered apps from shared `commonMain` code. It provides common Kotlin APIs for entities, CRUD operations, typed queries, migrations, Kotlin Flow change observation, KSP code generation, and Android ObjectBox integration.

This repository is designed for teams that want an ObjectBox Kotlin Multiplatform SDK-style developer experience across Android and iOS sample apps while keeping persistence code in one shared Kotlin module.

## Why This Exists

ObjectBox is a fast on-device database for mobile and edge apps. Kotlin Multiplatform lets teams share business logic across Android and iOS. This SDK bridges that gap by giving developers a common ObjectBox-style API in Kotlin shared code, while generating platform adapters behind the scenes.

## Features

- Kotlin Multiplatform `commonMain` API for ObjectBox-style data access
- `@ObxEntity` and `@ObxId` annotations for shared Kotlin entities
- KSP compiler for query fields, platform adapters, and schema metadata
- Gradle plugin for KSP wiring, generated source setup, and platform task ordering
- Android backend backed by real ObjectBox and generated `MyObjectBox`
- Native/iOS fallback backend for validating the shared SDK API in iOS apps
- CRUD operations from shared Kotlin code
- Typed query DSL with equality, string filters, ranges, grouped `anyOf` / `allOf`, sorting, offsets, limits, counts, and query deletes
- Kotlin Flow change stream for observing writes and deletes
- App-level migration hooks with schema version tracking
- SDK exception types with actionable error messages
- Android sample app and SwiftUI iOS sample app

## Current Platform Status

| Platform | Status |
| --- | --- |
| Android | Uses real ObjectBox through generated Android entities, adapters, and `MyObjectBox`. |
| iOS / Kotlin Native | Uses the SDK fallback backend with generated adapters and the same common API. |

The next persistence milestone is replacing the native fallback backend with ObjectBox native core integration through Kotlin/Native interop while keeping the same `commonMain` API.

## Repository Structure

```text
packages/
  objectbox-kmp-annotations      Common annotations: @ObxEntity and @ObxId
  objectbox-kmp-runtime          Shared SDK runtime, query DSL, migrations, Flow, backends
  objectbox-kmp-compiler         KSP code generator for fields, adapters, schema metadata
  objectbox-kmp-gradle-plugin    Gradle plugin for consumer setup

apps/
  shared                         KMP sample module that consumes the SDK
  android                        Android sample app with real ObjectBox backend
  ios                            SwiftUI iOS sample app using the shared KMP framework
```

## Install In A KMP Module

Apply the SDK Gradle plugin in the Kotlin Multiplatform module that owns your shared entities:

```kotlin
plugins {
    id("com.codeint.objectbox-kmp")
}

objectBoxKmp {
    generatedPackage.set("com.example.shared.generated")
}
```

In this monorepo sample, the plugin uses the local compiler project:

```kotlin
objectBoxKmp {
    generatedPackage.set("com.codeint.objectboxkmp.sample.shared.generated")
    compilerProjectPath.set(":packages:objectbox-kmp-compiler")
}
```

The plugin configures:

- KSP compiler dependencies
- generated common source directories
- schema metadata handoff from common KSP to platform KSP
- platform KSP task ordering
- generated package validation

## Define Entities In CommonMain

Create Kotlin data classes once in `commonMain`:

```kotlin
import com.codeint.objectboxkmp.annotations.ObxEntity
import com.codeint.objectboxkmp.annotations.ObxId

@ObxEntity
data class Note(
    @ObxId val id: Long = 0,
    val title: String,
    val done: Boolean = false,
)
```

Supported property types:

- `Long`, `Int`, `Short`, `Byte`
- `Double`, `Float`
- `Boolean`
- `String`
- `ByteArray`, `FloatArray`
- nullable variants of the supported types

The compiler validates unsupported types, duplicate entity names, missing IDs, invalid IDs, and incompatible entity shapes at build time.

## Generated Code

Common KSP generates typed query fields:

```kotlin
NoteFields.id
NoteFields.title
NoteFields.done
```

Platform KSP generates:

- `GeneratedObxAdapters.registry()`
- shared/native adapters
- Android ObjectBox entity classes
- Android adapter implementations

On Android, the official ObjectBox processor then generates:

- `MyObjectBox`
- ObjectBox entity metadata
- ObjectBox cursors
- ObjectBox model files

## Open A Store

Android initialization uses the generated registry and real ObjectBox:

```kotlin
ObjectBoxKmpAndroid.configureWithConfig(
    config = ObxConfig {
        name = "objectbox-kmp-sample"
        schemaVersion = 1
        schemaMismatchPolicy = ObxSchemaMismatchPolicy.DeleteAndReopen
    },
    registryProvider = { GeneratedObxAdapters.registry() },
    configBoxStoreProvider = { config ->
        MyObjectBox.builder()
            .name(config.name)
            .androidContext(context.applicationContext)
            .build()
    },
    migrationStateStoreProvider = {
        AndroidObxMigrationStateStore(context.applicationContext)
    },
)
```

Then use the same shared API:

```kotlin
val store = ObjectBoxKmp.open()
val notes = store.box<Note>()
```

## CRUD From Common Code

```kotlin
val id = store.write {
    notes.put(Note(title = "Build ObjectBox KMP SDK"))
}

val note = store.read {
    notes.get(id)
}

store.write {
    note?.let {
        notes.put(it.copy(done = true))
    }
}

store.write {
    notes.remove(id)
}
```

## Typed Query DSL

```kotlin
val openNotes = store.read {
    notes.query()
        .equal(NoteFields.done, false)
        .contains(NoteFields.title, "release")
        .orderBy(NoteFields.id, descending = true)
        .limit(20)
        .find()
}
```

Grouped query example:

```kotlin
val results = store.read {
    notes.query()
        .anyOf {
            startsWith(NoteFields.title, "Sprint")
            contains(NoteFields.title, "review")
            endsWith(NoteFields.title, "notes")
        }
        .notEqual(NoteFields.done, false)
        .orderBy(NoteFields.id, descending = true)
        .offset(0)
        .limit(10)
        .find()
}
```

Query delete:

```kotlin
val removed = store.write {
    notes.query()
        .allOf {
            contains(NoteFields.title, "Archive")
            equal(NoteFields.done, true)
        }
        .remove()
}
```

## Observe Changes With Kotlin Flow

```kotlin
store.changes.collect { change ->
    println("${change.entityName} ${change.operation} ${change.id}")
}
```

The sample app exposes this in both Android and iOS UI through the shared module.

## Migrations

Use SDK migrations for app-level data transformations:

```kotlin
val config = ObxConfig {
    name = "objectbox-kmp-sample"
    schemaVersion = 2
    migrations {
        migrate(from = 1, to = 2) {
            val notes = box<Note>()
            notes.getAll()
                .filter { note -> note.title.startsWith("Legacy:") }
                .forEach { note ->
                    notes.put(
                        note.copy(
                            title = note.title.removePrefix("Legacy:").trim(),
                        ),
                    )
                }
        }
    }
}
```

ObjectBox still owns structural schema evolution through ObjectBox model files and stable UIDs. SDK migrations are for controlled data transformations around those schema changes.

## Error Handling

The runtime uses SDK-specific exception types so app developers can quickly identify failures:

- `ObxConfigurationException`
- `ObxStoreOpenException`
- `ObxSchemaException`
- `ObxQueryException`
- `ObxTransactionException`

Compiler and Gradle errors are designed to point directly to the missing configuration, unsupported entity shape, invalid package, missing schema metadata, or adapter mismatch.

## Sample Apps

The repository includes polished sample apps for both platforms:

- Android app: grouped CRUD, query, complex query, and live output sections
- iOS SwiftUI app: matching SDK demo surface using the shared KMP framework

Build Android:

```bash
./gradlew :apps:android:assembleDebug
```

Build the shared iOS XCFramework:

```bash
./gradlew :apps:shared:assembleObjectBoxKmpSampleSharedDebugXCFramework
```

Build both:

```bash
./gradlew clean :apps:android:assembleDebug :apps:shared:assembleObjectBoxKmpSampleSharedDebugXCFramework
```

Android APK:

```text
apps/android/build/outputs/apk/debug/android-debug.apk
```

iOS XCFramework:

```text
apps/shared/build/XCFrameworks/debug/ObjectBoxKmpSampleShared.xcframework
```

## Requirements

- JDK 17
- Android Studio or Gradle CLI
- Android SDK
- Xcode for iOS simulator/framework builds
- Kotlin Multiplatform toolchain

## Keywords

ObjectBox KMP, ObjectBox Kotlin Multiplatform, Kotlin Multiplatform database, KMP database SDK, ObjectBox iOS Kotlin, ObjectBox Android Kotlin, Kotlin Native database, KSP code generation, Kotlin Flow database changes, mobile offline database SDK.

## Contributor

Sahil Thakar
