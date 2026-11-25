# Input policy: pen draws, fingers navigate, configurable

**Status:** accepted

**Context:** Android 12 (the tablet) has no `FLAG_CANCELED` for palm touches; that arrives in API 33. The phone has no pen.
**Decision:** A "Draw with finger" setting. When off, only `TOOL_TYPE_STYLUS` (and `TOOL_TYPE_ERASER`) draws, fingers pan and zoom, and `ACTION_CANCEL` drops the stroke. It defaults to off when an input device reports `SOURCE_STYLUS` (the tablet) and on otherwise (the phone).
**Alternatives:** Heuristic palm rejection by contact size — unreliable on API 31.
**Consequences:** No palm heuristics to tune. The app needs a settings screen from the start.
