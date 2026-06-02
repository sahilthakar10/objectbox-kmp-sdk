package com.codeint.objectboxkmp.sample.shared

import com.codeint.objectboxkmp.runtime.ObjectBoxKmp
import com.codeint.objectboxkmp.runtime.ObxBox
import com.codeint.objectboxkmp.runtime.ObxChange
import com.codeint.objectboxkmp.runtime.ObxChangeOperation
import com.codeint.objectboxkmp.runtime.ObxStore
import com.codeint.objectboxkmp.runtime.box
import com.codeint.objectboxkmp.sample.shared.generated.NoteFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class CommonCrudSample {
    private val store: ObxStore
    private val notes: ObxBox<Note>
    private var selectedNoteId: Long? = null
    private var nextNoteNumber = 1

    init {
        ensureObjectBoxKmpConfigured()
        store = ObjectBoxKmp.open()
        notes = store.box()
    }

    fun create(): String {
        return store.write {
            val title = "Note $nextNoteNumber"
            val id = notes.put(Note(title = title))
            selectedNoteId = id
            nextNoteNumber++
            render("CREATE id=$id")
        }
    }

    fun readSelected(): String {
        return store.read {
            val id = selectedNoteId ?: return@read render("READ no selected note")
            render("READ ${notes.get(id)}")
        }
    }

    fun updateSelected(): String {
        return store.write {
            val id = selectedNoteId ?: return@write render("UPDATE no selected note")
            val existing = notes.get(id) ?: return@write render("UPDATE missing id=$id")
            notes.put(
                existing.copy(
                    title = "${existing.title} (updated)",
                    done = !existing.done,
                ),
            )
            render("UPDATE id=$id")
        }
    }

    fun deleteSelected(): String {
        return store.write {
            val id = selectedNoteId ?: return@write render("DELETE no selected note")
            val removed = notes.remove(id)
            selectedNoteId = notes.getAll().lastOrNull()?.id
            render("DELETE id=$id removed=$removed")
        }
    }

    fun createThree(): String {
        return store.write {
            repeat(3) {
                val title = "Note $nextNoteNumber"
                val id = notes.put(Note(title = title))
                selectedNoteId = id
                nextNoteNumber++
            }
            render("CREATE 3")
        }
    }

    fun clearAll(): String {
        return store.write {
            val ids = notes.getAll().map { it.id }
            ids.forEach(notes::remove)
            selectedNoteId = null
            render("CLEAR removed=${ids.size}")
        }
    }

    fun findOpenNotes(): String {
        return store.read {
            val found = notes.query()
                .equal(NoteFields.done, false)
                .orderBy(NoteFields.id)
                .find()
            render(
                operation = "QUERY done=false matched=${found.size}",
                listedNotes = found,
                listTitle = "Matching Notes:",
            )
        }
    }

    fun searchTitles(): String {
        return store.read {
            val found = notes.query()
                .contains(NoteFields.title, "Note")
                .orderBy(NoteFields.title)
                .limit(5)
                .find()
            render(
                operation = "QUERY title contains Note limit=5 matched=${found.size}",
                listedNotes = found,
                listTitle = "Matching Notes:",
            )
        }
    }

    fun countDoneNotes(): String {
        return store.read {
            val count = notes.query()
                .equal(NoteFields.done, true)
                .count()
            render("QUERY done=true count=$count")
        }
    }

    fun deleteDoneNotes(): String {
        return store.write {
            val removed = notes.query()
                .equal(NoteFields.done, true)
                .remove()
            selectedNoteId = notes.getAll().lastOrNull()?.id
            render("QUERY DELETE done=true removed=$removed")
        }
    }

    fun seedComplexNotes(): String {
        return store.write {
            val samples = listOf(
                Note(title = "Sprint plan", done = false),
                Note(title = "Release checklist", done = true),
                Note(title = "Bug triage", done = false),
                Note(title = "Design review", done = true),
                Note(title = "Customer follow-up", done = false),
                Note(title = "Archive old notes", done = true),
            )

            samples.forEach { note ->
                selectedNoteId = notes.put(note)
            }
            nextNoteNumber += samples.size
            render("SEED complex notes added=${samples.size}")
        }
    }

    fun complexAndRangeQuery(): String {
        return store.read {
            val maxId = notes.getAll().maxOfOrNull { note -> note.id } ?: 0L
            val minId = (maxId - 8).coerceAtLeast(1L)
            val found = notes.query()
                .equal(NoteFields.done, false)
                .between(NoteFields.id, minId, maxId)
                .contains(NoteFields.title, "e")
                .orderBy(NoteFields.title)
                .limit(5)
                .find()

            render(
                operation = "COMPLEX AND done=false id=$minId..$maxId title contains e limit=5 matched=${found.size}",
                listedNotes = found,
                listTitle = "Complex AND Results:",
            )
        }
    }

    fun complexGroupedOrQuery(): String {
        return store.read {
            val found = notes.query()
                .anyOf {
                    startsWith(NoteFields.title, "Sprint")
                    contains(NoteFields.title, "review")
                    endsWith(NoteFields.title, "notes")
                }
                .notEqual(NoteFields.done, false)
                .orderBy(NoteFields.id, descending = true)
                .offset(0)
                .limit(3)
                .find()

            render(
                operation = "COMPLEX OR (Sprint* OR *review* OR *notes) AND done!=false limit=3 matched=${found.size}",
                listedNotes = found,
                listTitle = "Complex OR Results:",
            )
        }
    }

    fun complexDeleteQuery(): String {
        return store.write {
            val removed = notes.query()
                .allOf {
                    contains(NoteFields.title, "Archive")
                    equal(NoteFields.done, true)
                }
                .remove()

            selectedNoteId = notes.getAll().lastOrNull()?.id
            render("COMPLEX DELETE title contains Archive AND done=true removed=$removed")
        }
    }

    fun renderedStateFlow(): Flow<String> {
        return store.changes
            .onStart {
                emit(
                    ObxChange(
                        entityName = "Note",
                        operation = ObxChangeOperation.Put,
                        id = null,
                    ),
                )
            }
            .map { change ->
                val idText = change.id?.let { id -> " id=$id" }.orEmpty()
                render("FLOW ${change.entityName}.${change.operation}$idText")
            }
    }

    fun observeRenderedState(onEach: (String) -> Unit): ObxSampleSubscription {
        val parentJob = SupervisorJob()
        val scope = CoroutineScope(parentJob + Dispatchers.Default)
        scope.launch {
            renderedStateFlow().collect { value -> onEach(value) }
        }
        return ObxSampleSubscription(parentJob)
    }

    fun currentState(): String = render("READY")

    private fun render(
        operation: String,
        listedNotes: List<Note> = notes.getAll(),
        listTitle: String = "All Notes:",
    ): String {
        return buildString {
            appendLine("ObjectBox KMP Common CRUD Sample")
            appendLine(operation)
            appendLine()
            appendLine("Selected ID: ${selectedNoteId ?: "-"}")
            appendLine("Count: ${notes.count()}")
            appendLine()
            appendLine(listTitle)
            if (listedNotes.isEmpty()) {
                appendLine("-")
            } else {
                listedNotes.forEach { note ->
                    appendLine("${note.id}. ${note.title} | done=${note.done}")
                }
            }
        }
    }
}

class ObxSampleSubscription internal constructor(
    private val job: Job,
) {
    fun cancel() {
        job.cancel()
    }
}

internal expect fun ensureObjectBoxKmpConfigured()
