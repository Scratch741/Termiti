package com.example.termiti

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/**
 * Procedurálně generované zvuky bez audio assetů.
 * Každý zvuk se spustí na vlastním vlákně, aby neblokoval UI.
 */
object SoundManager {

    var enabled = true

    // ── Veřejné API ──────────────────────────────────────────────────────────

    fun playCardPlay()  = playAsync { toneEnv(freq = 520f,  dur = 0.12f, vol = 0.35f) }
    fun playAttack()    = playAsync { sweep(freqFrom = 280f, freqTo = 90f, dur = 0.14f, vol = 0.40f) +
                                      toneEnv(freq = 95f,   dur = 0.10f, vol = 0.30f) }
    fun playBuild()     = playAsync { toneEnv(freq = 260f,  dur = 0.18f, vol = 0.30f) }
    fun playResource()  = playAsync { toneEnv(freq = 660f,  dur = 0.09f, vol = 0.20f) }
    fun playDiscard()   = playAsync { sweep(freqFrom = 440f, freqTo = 220f, dur = 0.12f, vol = 0.25f) }
    fun playWin()       = playAsync { fanfare(ascending = true)  }
    fun playLose()      = playAsync { fanfare(ascending = false) }
    fun playAiTurn()    = playAsync { toneEnv(freq = 330f,  dur = 0.08f, vol = 0.15f) }

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
