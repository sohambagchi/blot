package app.screenscratch.core.model.format

import app.screenscratch.core.FORMAT_VERSION
import app.screenscratch.core.model.Note
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Reads and writes a note's `note.json`. The layout is documented in the note.json format ADR. */
object NoteJson {
    /** The file's name inside a note directory. */
    const val FILE_NAME = "note.json"

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** The `note.json` text for [note], with `"format"` set to [FORMAT_VERSION]. */
    fun encode(note: Note): String {
        val body = json.encodeToJsonElement(Note.serializer(), note).jsonObject
        val file = buildJsonObject {
            put("format", JsonPrimitive(FORMAT_VERSION))
            for ((k, v) in body) put(k, v)
        }
        return json.encodeToString(JsonObject.serializer(), file) + "\n"
    }

    /**
     * Parses `note.json` text. Unknown fields are ignored.
     *
     * @throws NoteFormatException if the text is not a valid note, or its format is newer than
     *   this reader's [FORMAT_VERSION].
     */
    fun decode(text: String): Note {
        val note = try {
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: throw NoteFormatException("not a JSON object")
            val format = (root["format"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
                ?: throw NoteFormatException("missing or non-integer \"format\"")
            if (format < 1) throw NoteFormatException("format $format")
            if (format > FORMAT_VERSION) {
                throw NoteFormatException("format $format is newer than this app's $FORMAT_VERSION")
            }
            json.decodeFromJsonElement(Note.serializer(), root)
        } catch (e: SerializationException) {
            throw NoteFormatException(e.message ?: "malformed note", e)
        } catch (e: IllegalArgumentException) {
            throw NoteFormatException(e.message ?: "malformed note", e)
        }
        val dup = note.elements.groupingBy { it.id }.eachCount().entries.firstOrNull { it.value > 1 }
        if (dup != null) throw NoteFormatException("element id ${dup.key} appears ${dup.value} times")
        return note
    }
}

/** `note.json` could not be read as a note. */
class NoteFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
