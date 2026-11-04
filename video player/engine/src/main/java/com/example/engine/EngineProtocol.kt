package com.example.engine

/**
 * The messages the main OffScroll app and this engine app exchange over a bound [android.os.Messenger].
 *
 * KEEP IN SYNC: an identical copy lives in the main app at
 * app/src/main/java/com/example/data/universal/engine/EngineProtocol.kt (only the package line differs).
 * Bump [PROTOCOL_VERSION] whenever a message or key changes in a way an older side can't understand.
 */
object EngineProtocol {
    const val ENGINE_PACKAGE = "com.aistudio.offscroll.engine"
    const val SERVICE_CLASS = "com.example.engine.EngineService"
    const val PERMISSION = "com.aistudio.offscroll.permission.USE_ENGINE"
    const val PROTOCOL_VERSION = 1

    // ---- app -> engine (Message.what). Message.arg1 is a request id the engine echoes back. ----
    /** Starts the engine (unpacks Python and ffmpeg the first time) and reports versions. */
    const val REQ_HELLO = 1
    /** KEY_URL -> KEY_INFO_JSON */
    const val REQ_FETCH_INFO = 2
    /** KEY_URL, KEY_SELECTOR, KEY_PROCESS_ID -> progress messages, then KEY_FILE_URI + KEY_FILE_EXT */
    const val REQ_DOWNLOAD = 3
    /** KEY_PROCESS_ID */
    const val REQ_CANCEL = 4
    /** Updates yt-dlp itself -> KEY_MESSAGE + KEY_VERSION */
    const val REQ_UPDATE = 5
    /** KEY_PROCESS_ID: the main app has copied the file, so the engine can delete it */
    const val REQ_CLEANUP = 6

    // ---- engine -> app ----
    const val REP_RESULT = 100
    const val REP_PROGRESS = 101
    const val REP_ERROR = 102

    // ---- Bundle keys ----
    const val KEY_URL = "url"
    const val KEY_SELECTOR = "selector"
    const val KEY_PROCESS_ID = "process_id"

    const val KEY_VERSION = "version"
    const val KEY_PROTOCOL = "protocol"
    const val KEY_VERSION_CODE = "version_code"
    const val KEY_MESSAGE = "message"
    const val KEY_INFO_JSON = "info_json"

    const val KEY_PERCENT = "percent"
    const val KEY_ETA = "eta"
    const val KEY_LINE = "line"

    const val KEY_FILE_URI = "file_uri"
    const val KEY_FILE_EXT = "file_ext"

    /** yt-dlp's own error text (the app turns it into a friendly sentence). Absent for engine-side problems. */
    const val KEY_RAW_ERROR = "raw_error"
    const val KEY_SUGGEST_UPDATE = "suggest_update"
    const val KEY_CANCELLED = "cancelled"
}
