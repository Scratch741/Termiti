// ============================================================
// Language.kt
// ============================================================
package com.example.termiti

/**
 * Represents a loaded language pack.
 * Populated from the "meta" block of a lang/&lt;code&gt;.json file.
 */
data class Language(
    val code:    String,   // e.g. "cs", "en", "de"
    val name:    String,   // e.g. "Cestina", "English", "Deutsch"
    val flag:    String,   // e.g. the flag emoji for this language
    val author:  String = "Community",
    val version: Int    = 1
)
