package com.example.termiti

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import kotlin.math.*
import kotlin.random.Random

/**
 * Procedurálně generované zvuky bez audio assetů + správa hudby na pozadí.
 */
object SoundManager {

    var enabled = true
    private var bgPlayer: MediaPlayer? = null
    private var currentTrackIndex = 0
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    // ── Hlasitost (0f–1f) ───────────────────────────────────────────────────
    var musicVolume: Float = 1.0f
        private set
    var sfxVolume: Float = 1.0f
        private set

    fun setMusicVolume(v: Float) {
        musicVolume = v.coerceIn(0f, 1f)
        bgPlayer?.setVolume(0.4f * musicVolume, 0.4f * musicVolume)
        prefs?.edit()?.putFloat("music_volume", musicVolume)?.apply()
    }

    fun setSfxVolume(v: Float) {
        sfxVolume = v.coerceIn(0f, 1f)
        prefs?.edit()?.putFloat("sfx_volume", sfxVolume)?.apply()
    }

    private fun sfx(v: Float) = (v * sfxVolume).coerceIn(0f, 1f)

    // ── SoundPool pro nízko-latentní SFX ────────────────────────────────────

    private var soundPool: SoundPool? = null
    private var sndCardDraw1: Int = 0
    private var sndCardDraw2: Int = 0
    private var sndCardPlayFile: Int = 0
    private var sndCardAttack1: Int = 0
    private var sndCardAttack2: Int = 0
    private var sndCardDiscard: Int = 0
    private var sndMineDestroy: Int = 0
    private var sndMenuTap: Int = 0
    private var sndDeckSelect: Int = 0
    private var sndBuild: Int = 0
    private var sndWinBattle: Int = 0
    private var sndLostBattle: Int = 0

    fun initSounds(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences("termiti_settings", Context.MODE_PRIVATE)
        musicVolume = prefs!!.getFloat("music_volume", 0.5f)
        sfxVolume   = prefs!!.getFloat("sfx_volume",   1.0f)
        if (soundPool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()
        sndCardDraw1    = soundPool!!.load(context, R.raw.card_draw,            1)
        sndCardDraw2    = soundPool!!.load(context, R.raw.card_draw_2,          1)
        sndCardPlayFile = soundPool!!.load(context, R.raw.card_play,            1)
        sndCardAttack1  = soundPool!!.load(context, R.raw.card_attack,          1)
        sndCardAttack2  = soundPool!!.load(context, R.raw.card_attack_2,        1)
        sndCardDiscard  = soundPool!!.load(context, R.raw.card_discard,         1)
        sndMineDestroy  = soundPool!!.load(context, R.raw.mine_destroy,            1)
        sndMenuTap      = soundPool!!.load(context, R.raw.menu_tap,               1)
        sndDeckSelect   = soundPool!!.load(context, R.raw.deckbuilder_card_select, 1)
        sndBuild        = soundPool!!.load(context, R.raw.build,                   1)
        sndWinBattle    = soundPool!!.load(context, R.raw.win_battle,              1)
        sndLostBattle   = soundPool!!.load(context, R.raw.lost_battle,             1)
    }

    fun releaseSounds() {
        soundPool?.release()
        soundPool = null
    }

    private val trackIds = listOf(
        R.raw.bg_music,
        R.raw.bg_music_2,
        R.raw.bg_music_3,
        R.raw.bg_music_4,
        R.raw.bg_music_5,
        R.raw.bg_music_6,
        R.raw.bg_music_7,
        R.raw.bg_music_8
    )

    // ── Hudba na pozadí ──────────────────────────────────────────────────────

    fun startBackgroundMusic(context: Context) {
        if (!enabled) return
        if (bgPlayer != null) return
        
        // Začni náhodnou skladbou
        currentTrackIndex = trackIds.indices.random()
        playTrack(context, trackIds[currentTrackIndex])
    }

    private fun playTrack(context: Context, resId: Int) {
        try {
            stopBackgroundMusic()
            bgPlayer = MediaPlayer.create(context, resId)?.apply {
                setVolume(0.4f * musicVolume, 0.4f * musicVolume)
                setOnCompletionListener {
                    // Po skončení pusť další v pořadí
                    playNextTrack(context)
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playNextTrack(context: Context) {
        currentTrackIndex = (currentTrackIndex + 1) % trackIds.size
        playTrack(context, trackIds[currentTrackIndex])
    }

    fun stopBackgroundMusic() {
        bgPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        bgPlayer = null
    }

    /** Dočasně ztlumí hudbu (duck) na [duckFrac] jejího objemu po dobu [durationMs] ms. */
    fun duckMusic(duckFrac: Float = 0.12f, durationMs: Long = 5000L) {
        val ducked = 0.4f * musicVolume * duckFrac
        bgPlayer?.setVolume(ducked, ducked)
        Thread {
            Thread.sleep(durationMs)
            val restored = 0.4f * musicVolume
            bgPlayer?.setVolume(restored, restored)
        }.start()
    }

    fun pauseBackgroundMusic() {
        try {
            if (bgPlayer?.isPlaying == true) {
                bgPlayer?.pause()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun resumeBackgroundMusic() {
        try {
            if (enabled && bgPlayer != null) {
                bgPlayer?.start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Veřejné API ──────────────────────────────────────────────────────────

    /** Líz karty – náhodně vybere card_draw nebo card_draw_2. */
    fun playCardDraw() {
        if (!enabled) return
        val pool = soundPool ?: return
        val id = if (Random.nextBoolean()) sndCardDraw1 else sndCardDraw2
        pool.play(id, sfx(0.75f), sfx(0.75f), 1, 0, 1.0f)
    }

    /** Zahrání karty – file-based (card_play), fallback na procedurální zvuk. */
    fun playCardPlay() {
        if (!enabled) return
        val pool = soundPool
        if (pool != null && sndCardPlayFile != 0) {
            pool.play(sndCardPlayFile, sfx(0.65f), sfx(0.65f), 1, 0, 1.0f)
        } else {
            playAsync { toneEnv(freq = 520f, dur = 0.12f, vol = 0.35f) }
        }
    }
    /** Útočná karta – náhodně vybere card_attack nebo card_attack_2. */
    fun playAttack() {
        if (!enabled) return
        val pool = soundPool
        if (pool != null && sndCardAttack1 != 0) {
            val id = if (Random.nextBoolean()) sndCardAttack1 else sndCardAttack2
            pool.play(id, sfx(0.70f), sfx(0.70f), 1, 0, 1.0f)
        } else {
            playAsync { sweep(freqFrom = 280f, freqTo = 90f, dur = 0.14f, vol = 0.40f) +
                        toneEnv(freq = 95f, dur = 0.10f, vol = 0.30f) }
        }
    }
    /** Stavba hradu/hradeb – file-based (build). */
    fun playBuild() {
        if (!enabled) return
        val pool = soundPool
        if (pool != null && sndBuild != 0) {
            pool.play(sndBuild, sfx(0.70f), sfx(0.70f), 1, 0, 1.0f)
        } else {
            playAsync { toneEnv(freq = 260f, dur = 0.18f, vol = 0.30f) }
        }
    }
    fun playResource()  = playAsync { toneEnv(freq = 660f,  dur = 0.09f, vol = 0.20f) }

    /** Zničení dolu – mine_destroy. */
    fun playMineDestroy() {
        if (!enabled) return
        val pool = soundPool ?: return
        if (sndMineDestroy != 0) pool.play(sndMineDestroy, sfx(0.80f), sfx(0.80f), 1, 0, 1.0f)
    }

    /** Klik na tlačítko v menu. */
    fun playMenuTap() {
        if (!enabled) return
        val pool = soundPool ?: return
        if (sndMenuTap != 0) pool.play(sndMenuTap, sfx(0.65f), sfx(0.65f), 1, 0, 1.0f)
    }

    /** Přidání/odebrání karty v deck builderu. */
    fun playDeckSelect() {
        if (!enabled) return
        val pool = soundPool ?: return
        if (sndDeckSelect != 0) pool.play(sndDeckSelect, sfx(0.60f), sfx(0.60f), 1, 0, 1.0f)
    }
    /** Zahozená karta – file-based (card_discard). */
    fun playDiscard() {
        if (!enabled) return
        val pool = soundPool
        if (pool != null && sndCardDiscard != 0) {
            pool.play(sndCardDiscard, sfx(0.65f), sfx(0.65f), 1, 0, 1.0f)
        } else {
            playAsync { sweep(freqFrom = 440f, freqTo = 220f, dur = 0.12f, vol = 0.25f) }
        }
    }
    fun playWin() {
        if (!enabled) return
        duckMusic()
        val pool = soundPool
        if (pool != null && sndWinBattle != 0) {
            pool.play(sndWinBattle, sfx(0.90f), sfx(0.90f), 1, 0, 1.0f)
        } else {
            playAsync { fanfare(ascending = true) }
        }
    }
    fun playLose() {
        if (!enabled) return
        duckMusic()
        val pool = soundPool
        if (pool != null && sndLostBattle != 0) {
            pool.play(sndLostBattle, sfx(0.90f), sfx(0.90f), 1, 0, 1.0f)
        } else {
            playAsync { fanfare(ascending = false) }
        }
    }
    fun playAiTurn()    = playAsync { toneEnv(freq = 330f,  dur = 0.08f, vol = 0.15f) }

    /**
     * Přehraje libovolný zvukový soubor z res/raw (R.raw.xxx) jako one-shot.
     * Používá se pro karty s [Card.soundResId].
     */
    fun playCustom(resId: Int) {
        if (!enabled) return
        val ctx = appContext ?: return
        Thread {
            runCatching {
                val mp = MediaPlayer.create(ctx, resId) ?: return@runCatching
                mp.setVolume(sfx(0.75f), sfx(0.75f))
                mp.setOnCompletionListener { it.release() }
                mp.start()
            }
        }.start()
    }

    // ── Interní generátory ───────────────────────────────────────────────────

    private const val SAMPLE_RATE = 22050

    /** Sinus s obálkou (attack+decay). */
    private fun toneEnv(freq: Float, dur: Float, vol: Float): ShortArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = envelope(i, n)
            buf[i] = (sin(2 * PI * freq * t) * env * vol * Short.MAX_VALUE).toInt().toShort()
        }
        return buf
    }

    /** Bílý šum s obálkou. */
    private fun noise(dur: Float, vol: Float): ShortArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val rng = java.util.Random(42)
        return ShortArray(n) { i ->
            (rng.nextGaussian() * envelope(i, n) * vol * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Glissando (přejezd frekvence). */
    private fun sweep(freqFrom: Float, freqTo: Float, dur: Float, vol: Float): ShortArray {
        val n = (SAMPLE_RATE * dur).toInt()
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val frac = i.toDouble() / n
            val freq = freqFrom + (freqTo - freqFrom) * frac
            phase += 2 * PI * freq / SAMPLE_RATE
            buf[i] = (sin(phase) * envelope(i, n) * vol * Short.MAX_VALUE).toInt().toShort()
        }
        return buf
    }

    /** Jednoduché vítězné / porážkové fanfáry. */
    private fun fanfare(ascending: Boolean): ShortArray {
        val notes = if (ascending)
            listOf(523f, 659f, 784f, 1047f)
        else
            listOf(440f, 370f, 294f, 220f)
        return notes.map { f -> toneEnv(f, dur = 0.12f, vol = 0.35f) }.concat()
    }

    /** Krátkodobá obálka: lineární nástup 10 %, útlum posledních 30 %. */
    private fun envelope(i: Int, n: Int): Double {
        val attack  = n * 0.10
        val release = n * 0.30
        return when {
            i < attack       -> i / attack
            i > n - release  -> (n - i) / release
            else             -> 1.0
        }.coerceIn(0.0, 1.0)
    }

    /** Složí více ShortArray za sebou. */
    private operator fun ShortArray.plus(other: ShortArray): ShortArray {
        val result = ShortArray(size + other.size)
        copyInto(result)
        other.copyInto(result, size)
        return result
    }

    private fun List<ShortArray>.concat(): ShortArray {
        val total = sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        forEach { arr -> arr.copyInto(result, offset); offset += arr.size }
        return result
    }

    // ── Přehrání na pozadí ───────────────────────────────────────────────────

    private fun playAsync(generate: () -> ShortArray) {
        if (!enabled) return
        Thread {
            runCatching {
                val samples = generate()
                val minBuf  = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                // Čekáme, než se přehraje, pak uvolníme
                Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 50)
                track.stop()
                track.release()
            }
        }.start()
    }
}
