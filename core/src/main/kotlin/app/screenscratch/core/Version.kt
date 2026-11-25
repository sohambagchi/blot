package app.screenscratch.core

/**
 * The `note.json` format version this build writes and the newest it reads. Bumped when a
 * change can't be ignored safely by an older reader: a new element, enum value or required
 * field, or a changed meaning or encoding. New optional fields don't bump it.
 */
const val FORMAT_VERSION: Int = 1
