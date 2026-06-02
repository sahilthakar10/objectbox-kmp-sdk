# We Built An ObjectBox KMP SDK So You Can Write Database Code From commonMain

Subtitle: A Kotlin Multiplatform wrapper around ObjectBox with shared entities, CRUD, typed queries, Flow observation, migrations, KSP code generation, Android ObjectBox support, and an iOS-ready API layer.

Hello Folks,

So recently I was exploring ObjectBox again.

And if you have worked on Android databases, then you already know this thing:

Room is good.
SQLite is everywhere.
But ObjectBox is fast.

It is one of those databases where you do not think too much about SQL, cursors, boilerplate, mapping, DAO layers, and all those things. You create entities, open a box, put data, query data, and move ahead.

But then one question came into my mind:

What if I want to use ObjectBox properly from Kotlin Multiplatform?

Not just Android.
Not just one platform.
But from `commonMain`.

Something like this:

```kotlin
val store = ObjectBoxKmp.open()
val notes = store.box<Note>()

store.write {
    notes.put(Note(title = "Build ObjectBox KMP SDK"))
}
```

Simple, right?

But here is the problem.

ObjectBox is powerful on Android, but if you are building a KMP project and you want your entity, CRUD, query, migration, and observation logic inside shared Kotlin code, then things are not directly available in the way KMP developers expect.

So we started building a wrapper.

Not a toy wrapper.
Not some static sample.
Not a hardcoded demo.

A proper SDK-style wrapper.

GitHub:

https://github.com/sahilthakar10/objectbox-kmp-sdk

If this project looks useful to you, please star it, try it, break it, open issues, and contribute. This is open source and we want more KMP developers to help shape it properly.

---

## What Are We Trying To Solve?

Let’s say you are building a Kotlin Multiplatform app.

You probably want these things:

1. Define entities once in `commonMain`
2. Write CRUD once in shared code
3. Write queries once in shared code
4. Observe database changes using Kotlin Flow
5. Keep Android and iOS sample apps using the same shared API
6. Avoid writing completely different database logic for both platforms

So ideally, your entity should look like this:

```kotlin
@ObxEntity
data class Note(
    @ObxId val id: Long = 0,
    val title: String,
    val done: Boolean = false,
)
```

And your shared code should look like this:

```kotlin
class NotesRepository {
    private val store = ObjectBoxKmp.open()
    private val notes = store.box<Note>()

    fun create(title: String): Long {
        return store.write {
            notes.put(Note(title = title))
        }
    }

    fun all(): List<Note> {
        return store.read {
            notes.getAll()
        }
    }
}
```

This is the target.

Write the logic once.
Use it from Android.
Use it from iOS.
Keep your app logic clean.

---

## What We Built

We created a monorepo with four SDK packages and two sample apps.

```text
packages/
  objectbox-kmp-annotations
  objectbox-kmp-runtime
  objectbox-kmp-compiler
  objectbox-kmp-gradle-plugin

apps/
  shared
  android
  ios
```

Let’s understand each part.

### 1. objectbox-kmp-annotations

This module gives us common annotations:

```kotlin
@ObxEntity
@ObxId
```

These annotations are used in `commonMain`, so your KMP entity does not need to depend on Android-only ObjectBox annotations.

### 2. objectbox-kmp-runtime

This is the shared SDK API.

It contains:

- `ObjectBoxKmp`
- `ObxStore`
- `ObxBox`
- query DSL
- migration APIs
- Flow change stream
- SDK exception types
- Android backend
- native fallback backend

So your common code talks to this runtime, not directly to platform-specific APIs.

### 3. objectbox-kmp-compiler

This is the KSP compiler.

It reads your shared entities and generates:

- typed query fields
- platform adapters
- Android ObjectBox entity wrappers
- registry code
- schema metadata

For example, from this:

```kotlin
@ObxEntity
data class Note(
    @ObxId val id: Long = 0,
    val title: String,
    val done: Boolean = false,
)
```

We generate fields like:

```kotlin
NoteFields.id
NoteFields.title
NoteFields.done
```

So you can write typed queries from common code.

### 4. objectbox-kmp-gradle-plugin

This is the wiring layer.

Because codegen in KMP is not just one task.

There is common KSP.
There is Android KSP.
There is iOS KSP.
There is schema metadata handoff.
There are generated source directories.
There are platform task dependencies.

If every consumer has to configure all of this manually, then the SDK is already painful.

So the plugin handles this:

```kotlin
plugins {
    id("com.codeint.objectbox-kmp")
}

objectBoxKmp {
    generatedPackage.set("com.example.shared.generated")
}
```

That is the kind of setup we want.

---

## CRUD From commonMain

Let’s look at real usage.

Create:

```kotlin
val id = store.write {
    notes.put(Note(title = "Sprint planning"))
}
```

Read:

```kotlin
val note = store.read {
    notes.get(id)
}
```

Update:

```kotlin
store.write {
    val existing = notes.get(id)
    if (existing != null) {
        notes.put(
            existing.copy(
                title = "${existing.title} updated",
                done = true,
            ),
        )
    }
}
```

Delete:

```kotlin
store.write {
    notes.remove(id)
}
```

This is normal Kotlin code.

No Android-only code here.
No Swift-specific logic here.
No separate repository implementation for both platforms.

---

## Typed Query DSL

CRUD is easy.

But if an SDK only supports CRUD, then it is not really enough for real apps.

So we added a query DSL.

Example:

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

Count:

```kotlin
val doneCount = store.read {
    notes.query()
        .equal(NoteFields.done, true)
        .count()
}
```

Query delete:

```kotlin
val removed = store.write {
    notes.query()
        .equal(NoteFields.done, true)
        .remove()
}
```

Now let’s make it more interesting.

Grouped query:

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

This is the kind of API that starts feeling useful in actual app code.

---

## Flow Observation

Now one more thing.

Modern Android and KMP apps are reactive.

You do not just write data and forget.
You want to observe changes.

So the SDK exposes changes as Kotlin Flow:

```kotlin
store.changes.collect { change ->
    println("${change.entityName} ${change.operation} ${change.id}")
}
```

In the sample app, both Android and iOS use shared logic to render live output when data changes.

This means the SDK is not just giving database operations.
It is also giving a clean way to react to database changes from common Kotlin code.

---

## Migrations

Migrations are always sensitive.

If migration is not clear, developers will waste hours debugging store opening issues, broken schema versions, or missing data.

So we added app-level migration hooks:

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

Important point:

ObjectBox still owns structural schema evolution through its model files and stable UIDs.

Our SDK migrations are for app-level data transformations around those schema changes.

So we are not pretending to replace ObjectBox internals.
We are building the KMP layer around it.

---

## Android Status

Android uses real ObjectBox.

On Android, the SDK generates platform code and connects to ObjectBox using generated adapters.

The flow is something like this:

1. You define `@ObxEntity` in `commonMain`
2. KSP generates Android adapter code
3. Android ObjectBox processor generates `MyObjectBox`
4. SDK opens the real ObjectBox store
5. Your shared code calls `ObjectBoxKmp.open()`

Android initialization looks like this:

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

After this, the shared code does not care that it is running on Android.

It simply opens the SDK store and works.

---

## iOS Status

Now let’s be very clear here.

The iOS sample app builds.
The shared KMP framework builds.
The same common API is available.
The generated registry is used.

But currently iOS is using the SDK fallback backend.

That means the API layer is ready, the sample is ready, the KMP structure is ready, but real ObjectBox native persistence on iOS is still the next milestone.

The plan is to integrate ObjectBox native core through Kotlin/Native interop and keep the same `commonMain` API.

Why are we saying this clearly?

Because open source should not hide important details.

If you use this today:

- Android gives real ObjectBox persistence
- iOS validates the shared SDK API through fallback storage
- production-grade iOS ObjectBox persistence is still open for contribution

And honestly, this is exactly where contributors can help.

---

## Sample Apps

We also added sample apps.

Not just blank screens.

Both Android and iOS samples show:

- Core CRUD
- Query examples
- Complex grouped queries
- Query delete
- Flow/live output
- Shared KMP logic

Android has a native sample UI.
iOS has a SwiftUI sample UI.

The point is simple:

If someone opens the repository, they should understand the SDK quickly.

No guessing.
No hidden setup.
No empty demo.

---

## Build It

Clone the repository:

```bash
git clone https://github.com/sahilthakar10/objectbox-kmp-sdk.git
cd objectbox-kmp-sdk
```

Build Android:

```bash
./gradlew :apps:android:assembleDebug
```

Build the iOS XCFramework:

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

---

## What Kind Of Contributors Are We Looking For?

If you are into KMP, Android, iOS, KSP, Gradle plugins, ObjectBox, Kotlin/Native, or SDK design, this project has many interesting areas.

Some good contribution areas:

- ObjectBox native integration for iOS
- better KMP publishing setup
- unit tests and integration tests
- more supported property types
- relation support
- better query DSL coverage
- compiler diagnostics
- sample app improvements
- documentation improvements
- migration test cases
- real-world examples

This is not one of those projects where contribution means only fixing spelling mistakes.

There is real engineering work here.

KSP.
Gradle.
Kotlin Multiplatform.
ObjectBox.
Android backend.
iOS native path.
SDK architecture.

If these things excite you, you should definitely check it out.

---

## Why Open Source This?

Because this problem should not be solved again and again by every KMP developer.

If ten teams want ObjectBox in KMP, ten teams should not write ten half-working wrappers.

One good open-source SDK is better.

One place where people can contribute.
One place where the compiler edge cases are fixed.
One place where query DSL improves.
One place where Android and iOS paths become stronger over time.

That is the goal.

---

## Final Thoughts

Kotlin Multiplatform is growing.

More teams are moving business logic into shared Kotlin.
But storage is still one of those areas where developers often end up writing platform-specific code.

ObjectBox is already strong as a local database.

Now the question is:

Can we make the KMP developer experience around ObjectBox clean enough that developers can write database logic from `commonMain`?

That is what this SDK is trying to do.

The repository is live here:

https://github.com/sahilthakar10/objectbox-kmp-sdk

If you like the idea:

- star the repo
- try the Android sample
- build the iOS sample
- open issues
- send PRs
- help us make real iOS ObjectBox persistence happen

This can become a useful KMP database SDK only if more developers try it and contribute.

So yeah, check it out.

And if you find any edge case, don’t just silently close the tab.

Open an issue.

Let’s build it properly.

---

Tags: Kotlin Multiplatform, ObjectBox, Android, iOS, KSP
