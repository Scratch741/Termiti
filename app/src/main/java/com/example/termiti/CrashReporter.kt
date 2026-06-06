package com.example.termiti

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Globální zachytávač pádů.
 * – Při pádu uloží report do SharedPrefs (synchronně, před zabitím procesu).
 * – Při příštím startu aplikace odešle report na server.
 * – Uchovává kontext (aktuální obrazovka, poslední akce) pro snazší debugování.
 */
object CrashReporter {

    private const val PREF_NAME         = "crash_reporter"
    private const val KEY_PENDING_CRASH = "pending_crash"
    private const val REPORT_URL        = "http://138.2.136.49:8765/crash-report"

    /** Nastavte při přechodu na obrazovku, aby byl crash kontextový. */
    var lastScreen: String = "unknown"

    /** Nastavte před důležitými operacemi (zahrání karty, síť, …). */
    var lastAction: String = ""

    // ── Inicializace ──────────────────────────────────────────────────────────

    fun init(context: Context) {
        val appContext      = context.applicationContext
        val defaultHandler  = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { saveCrash(appContext, thread, throwable) } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Odešli případný crash z předchozí session
        uploadPendingCrash(appContext)
    }

    // ── Uložení crashe před zabitím procesu ──────────────────────────────────

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val report = JSONObject().apply {
            put("timestamp",   SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("version",     BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("device",      "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android",     "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("thread",      thread.name)
            put("screen",      lastScreen)
            put("lastAction",  lastAction)
            put("stacktrace",  sw.toString())
        }

        // commit() je synchronní — důležité, proces je za chvíli zabit
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_CRASH, report.toString())
            .commit()
    }

    // ── Upload při příštím startu ─────────────────────────────────────────────

    private fun uploadPendingCrash(context: Context) {
        val prefs   = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_PENDING_CRASH, null) ?: return

        // Ihned smaž — nechceme re-upload při každém startu
        prefs.edit().remove(KEY_PENDING_CRASH).apply()

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val conn = (URL(REPORT_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput       = true
                    connectTimeout = 8_000
                    readTimeout    = 8_000
                }
                conn.outputStream.use { it.write(pending.toByteArray(Charsets.UTF_8)) }
                conn.responseCode   // spustí request
                conn.disconnect()
            }.onFailure { e ->
                android.util.Log.w("CrashReporter", "Upload selhal: ${e.message}")
            }
        }
    }

    // ── Ruční logování ne-fatálních chyb ─────────────────────────────────────

    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        android.util.Log.e("CrashReporter/$tag", message, throwable)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val sw = StringWriter()
                throwable?.printStackTrace(PrintWriter(sw))

                val report = JSONObject().apply {
                    put("timestamp",   SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    put("type",        "non_fatal")
                    put("tag",         tag)
                    put("message",     message)
                    put("version",     BuildConfig.VERSION_NAME)
                    put("device",      "${Build.MANUFACTURER} ${Build.MODEL}")
                    put("screen",      lastScreen)
                    put("lastAction",  lastAction)
                    put("stacktrace",  sw.toString().ifEmpty { "(no stacktrace)" })
                }

                val conn = (URL(REPORT_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput       = true
                    connectTimeout = 8_000
                    readTimeout    = 8_000
                }
                conn.outputStream.use { it.write(report.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}
