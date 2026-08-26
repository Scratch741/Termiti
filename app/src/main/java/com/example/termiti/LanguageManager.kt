// ============================================================
// LanguageManager.kt
// ============================================================
package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/**
 * Loads and manages language packs from assets/lang/ (one .json file per language).
 *
 * Each JSON file has this structure:
 * {
 *   "meta": { "code": "cs", "name": "Čeština", "flag": "🇨🇿", "author": "...", "version": 1 },
 *   "strings": { "ok": "OK", "cancel": "Zrušit", ... }
 * }
 *
 * Adding a community translation = dropping a new .json file into assets/lang/.
 * Missing keys fall back to the Czech ("cs") pack silently.
 *
 * Call [init] from MainActivity.onCreate before using anything else.
 */
object LanguageManager {

    private const val PREFS_NAME  = "termiti_settings"
    private const val KEY_LANG    = "language_code"
    private const val DEFAULT_CODE = "cs"

    private lateinit var prefs: SharedPreferences

    /** All language packs discovered in assets/lang/. */
    val availablePacks = mutableListOf<LanguagePack>()

    /** Currently active pack (reactive — Composables reading this recompose on change). */
    val currentPackState = mutableStateOf<LanguagePack?>(null)

    val current: LanguagePack get() = currentPackState.value
        ?: availablePacks.firstOrNull { it.language.code == DEFAULT_CODE }
        ?: LanguagePack.fallback()

    val currentStrings: AppStrings get() = current.strings

    // ── Card text lookup ───────────────────────────────────────────────────────
    // Reading currentPackState inside a Composable makes card text reactive:
    // switching language recomposes any card showing its name/description.

    /** Localized card name for [id]; falls back to [fallback] (built-in Czech) if untranslated. */
    fun cardName(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.cards[id] ?: return fallback
        return t.name.ifBlank { fallback }
    }

    /** Localized card description for [id]; falls back to [fallback] (built-in Czech) if untranslated. */
    fun cardDesc(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.cards[id] ?: return fallback
        return t.desc.ifBlank { fallback }
    }

    /** Localized passive-ability title for [id]; falls back to [fallback] (built-in Czech). */
    fun abilityTitle(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.abilities[id] ?: return fallback
        return t.name.ifBlank { fallback }
    }

    /** Localized passive-ability description for [id]; falls back to [fallback] (built-in Czech). */
    fun abilityDesc(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.abilities[id] ?: return fallback
        return t.desc.ifBlank { fallback }
    }

    // ── Campaign text lookup ────────────────────────────────────────────────────

    /** Localized campaign location name for [id]; falls back to [fallback] (built-in Czech). */
    fun campaignLocationName(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.campaignLocations[id] ?: return fallback
        return t.name.ifBlank { fallback }
    }

    /** Localized campaign location description for [id]; falls back to [fallback] (built-in Czech). */
    fun campaignLocationDesc(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.campaignLocations[id] ?: return fallback
        return t.desc.ifBlank { fallback }
    }

    /** Localized campaign opponent name for [id]; falls back to [fallback] (built-in Czech). */
    fun campaignOpponentName(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.campaignOpponents[id] ?: return fallback
        return t.name.ifBlank { fallback }
    }

    /** Localized campaign opponent title for [id]; falls back to [fallback] (built-in Czech). */
    fun campaignOpponentTitle(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.campaignOpponents[id] ?: return fallback
        return t.title.ifBlank { fallback }
    }

    /** Localized campaign opponent description for [id]; falls back to [fallback] (built-in Czech). */
    fun campaignOpponentDesc(id: String, fallback: String): String {
        val pack = currentPackState.value ?: return fallback
        val t = pack.campaignOpponents[id] ?: return fallback
        return t.desc.ifBlank { fallback }
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAllPacks(context)
        val savedCode = prefs.getString(KEY_LANG, DEFAULT_CODE) ?: DEFAULT_CODE
        currentPackState.value = availablePacks.firstOrNull { it.language.code == savedCode }
            ?: availablePacks.firstOrNull { it.language.code == DEFAULT_CODE }
            ?: availablePacks.firstOrNull()
    }

    // ── Switch language ───────────────────────────────────────────────────────

    fun setLanguage(pack: LanguagePack) {
        currentPackState.value = pack
        prefs.edit().putString(KEY_LANG, pack.language.code).apply()
    }

    fun setLanguageByCode(code: String) {
        availablePacks.firstOrNull { it.language.code == code }?.let { setLanguage(it) }
    }

    // ── Load packs from assets/lang/ ─────────────────────────────────────────

    private fun loadAllPacks(context: Context) {
        availablePacks.clear()
        val fallback = LanguagePack.fallback()

        try {
            val files = context.assets.list("lang") ?: emptyArray()
            for (fileName in files.sorted()) {
                if (!fileName.endsWith(".json")) continue
                try {
                    val json = context.assets.open("lang/$fileName")
                        .bufferedReader().use { it.readText() }
                    val pack = LanguagePack.fromJson(JSONObject(json), fallback)
                    availablePacks.add(pack)
                } catch (e: Exception) {
                    android.util.Log.w("LanguageManager", "Failed to load lang pack: $fileName", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LanguageManager", "Failed to list lang/ assets", e)
        }

        // Always ensure Czech fallback exists
        if (availablePacks.none { it.language.code == DEFAULT_CODE }) {
            availablePacks.add(0, fallback)
        }
    }
}
