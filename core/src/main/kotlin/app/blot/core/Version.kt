package app.blot.core

/**
 * The newest `note.json` format version this build reads and writes. Bumped when a change can't
 * be ignored safely by an older reader: a new element, enum value or required field, or a changed
 * meaning or encoding. New optional fields don't bump it. A note is written with the lowest
 * version that holds it ([app.blot.core.model.format.NoteJson.formatOf]).
 *
 * 2 added the shape kinds after rectangle and ellipse, and line routes.
 */
const val FORMAT_VERSION: Int = 2
