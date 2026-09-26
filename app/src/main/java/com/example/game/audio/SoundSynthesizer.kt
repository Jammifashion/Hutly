package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Web Audio-like synthesizer using Android AudioTrack to generate procedural sound effects
 * (Gluckern, Klirren, Jubel, Verschüttet, Merge) without requiring external audio files.
 */
object SoundSynthesizer {
    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 44100
    private var isMuted = false

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        return isMuted
    }

    fun isMuted(): Boolean = isMuted

    /**
     * Gluckern: Liquid bubbling tone generated when liquid pours into a glass.
     */
    fun playGluckern() {
        if (isMuted) return
        scope.launch {
            val durationMs = 80
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            val baseFreq = 260f + Random.nextFloat() * 80f
            val endFreq = baseFreq + 120f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val progress = i.toFloat() / numSamples
                val currentFreq = baseFreq + (endFreq - baseFreq) * progress
                val envelope = sin(progress * PI.toFloat()).coerceIn(0f, 1f)

                // Modulated bubble formula
                val wave = sin(2f * PI.toFloat() * currentFreq * t) +
                        0.35f * sin(4f * PI.toFloat() * currentFreq * t)
                buffer[i] = (wave * envelope * 24000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcm(buffer)
        }
    }

    /**
     * Klirren: Crystal glass clink with sharp attack and lingering harmonic ring.
     */
    fun playKlirren() {
        if (isMuted) return
        scope.launch {
            val durationMs = 350
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            val f1 = 2400f
            val f2 = 3380f
            val f3 = 4800f

            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val decay = exp(-t * 14f)
                val wave = 0.5f * sin(2f * PI.toFloat() * f1 * t) +
                        0.35f * sin(2f * PI.toFloat() * f2 * t) +
                        0.15f * sin(2f * PI.toFloat() * f3 * t)
                buffer[i] = (wave * decay * 28000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcm(buffer)
        }
    }

    /**
     * Jubel: Brassy celebratory fanfare arpeggio (C5, E5, G5, C6) with cheer energy.
     */
    fun playJubel() {
        if (isMuted) return
        scope.launch {
            val noteDurationMs = 120
            val notes = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f, 1318.51f)
            val totalDurationMs = noteDurationMs * notes.size + 200
            val numSamples = (SAMPLE_RATE * (totalDurationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            for (n in notes.indices) {
                val freq = notes[n]
                val startSample = (n * noteDurationMs * SAMPLE_RATE / 1000)
                val noteSamples = (noteDurationMs * 1.5f * SAMPLE_RATE / 1000).toInt()

                for (i in 0 until noteSamples) {
                    val idx = startSample + i
                    if (idx >= numSamples) break
                    val t = i.toFloat() / SAMPLE_RATE
                    val decay = exp(-t * 5f)
                    val wave = 0.6f * sin(2f * PI.toFloat() * freq * t) +
                            0.25f * sin(4f * PI.toFloat() * freq * t) +
                            0.15f * sin(6f * PI.toFloat() * freq * t)
                    val sampleVal = (wave * decay * 22000).toInt()
                    val existing = buffer[idx].toInt()
                    buffer[idx] = (existing + sampleVal).coerceIn(-32767, 32767).toShort()
                }
            }
            playPcm(buffer)
        }
    }

    /**
     * Verschüttet: Splashing water noise burst when overflowing or spilling.
     */
    fun playVerschuettet() {
        if (isMuted) return
        scope.launch {
            val durationMs = 280
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            var lastSample = 0f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val decay = exp(-t * 9f)
                val whiteNoise = (Random.nextFloat() * 2f - 1f)
                // Simple low-pass filter for water splash body
                lastSample = 0.7f * lastSample + 0.3f * whiteNoise
                buffer[i] = (lastSample * decay * 26000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcm(buffer)
        }
    }

    /**
     * Merge: Shimmering magical chime sweep when 3 hats combine in the shelf.
     */
    fun playMergeChime() {
        if (isMuted) return
        scope.launch {
            val durationMs = 500
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val progress = i.toFloat() / numSamples
                val freq = 550f + 1600f * progress
                val decay = 1f - progress * 0.7f
                val shimmer = sin(2f * PI.toFloat() * freq * t) * (1f + 0.3f * sin(30f * PI.toFloat() * t))
                buffer[i] = (shimmer * decay * 24000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcm(buffer)
        }
    }

    /**
     * Click / Pop feedback for UI actions.
     */
    fun playClick() {
        if (isMuted) return
        scope.launch {
            val durationMs = 30
            val numSamples = (SAMPLE_RATE * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                val decay = exp(-t * 70f)
                val wave = sin(2f * PI.toFloat() * 950f * t)
                buffer[i] = (wave * decay * 20000).toInt().coerceIn(-32767, 32767).toShort()
            }
            playPcm(buffer)
        }
    }

    private fun playPcm(buffer: ShortArray) {
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackSize = maxOf(buffer.size * 2, minBufSize)

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
                .setBufferSizeInBytes(trackSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()
            // Release after playback finished
            track.setNotificationMarkerPosition(buffer.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    try {
                        t?.stop()
                        t?.release()
                    } catch (_: Exception) {}
                }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            })
        } catch (_: Exception) {
            // Audio playback failure recovery without crashing
        }
    }
}
