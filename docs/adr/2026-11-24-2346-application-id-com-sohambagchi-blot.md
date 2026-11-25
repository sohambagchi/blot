# The application id is com.sohambagchi.blot

**Status:** accepted

**Context:** depot now accepts only `com.sohambagchi.<app>` for release builds and `com.sohambagchi.<app>.debug` for debug builds; [2026-11-24-2125-rename-to-blot](2026-11-24-2125-rename-to-blot.md) chose `app.blot`.
**Decision:** `applicationId` is `com.sohambagchi.blot`, with debug builds suffixed `.debug`. The namespace and Kotlin packages stay `app.blot`.
**Alternatives:** Keep `app.blot` — depot refuses it, so the tablet could no longer update.
**Consequences:** Android sees a new app: it installs fresh beside `app.blot`, asks for the notes folder again, rebuilds its library index and thumbnails, and starts from default settings. Notes live in the chosen folder, so none move or change. The old app is uninstalled by hand once the new one has the folder.
