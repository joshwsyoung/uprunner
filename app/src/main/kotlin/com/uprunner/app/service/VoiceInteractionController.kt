package com.uprunner.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.uprunner.core.tts.TtsFormatter
import com.uprunner.core.voice.TelemetryIntent
import com.uprunner.core.voice.TelemetryIntentMatcher
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Spec §4: hardware-button voice trigger + two-tier query resolution, owned by the
 * Foreground Service (spec §7 groups MediaSession under the service). Tap the headphone
 * button -> open cue -> listen -> instant telemetry answer or a placeholder "no AI coach
 * yet" response for anything else.
 *
 * Two things here are deliberate placeholders, not the final spec implementation:
 * - Audio cues use [ToneGenerator] (a system tone synthesizer) instead of SoundPool with real
 *   "pop-dudum"/"click-chime" sound assets — I have no way to author/verify actual audio
 *   asset files in this environment. Swap in real SoundPool + assets whenever those are
 *   designed.
 * - Spoken responses use Android's built-in [TextToSpeech] engine, not Piper (M4 — a
 *   dedicated native JNI build). All text still passes through [TtsFormatter] first, so the
 *   Piper swap later is just replacing this class's `speak()` internals, not the call sites.
 *
 * Also unverified: reliable global media-button interception is notoriously inconsistent
 * across OEMs/Android versions without an actual active playback session. This needs
 * real-device testing (Appetize's cloud emulator can't simulate a Bluetooth headset tap).
 */
class VoiceInteractionController(private val context: Context) {

    private var mediaSession: MediaSessionCompat? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    private var audioFocusRequest: AudioFocusRequest? = null

    fun start() {
        textToSpeech = TextToSpeech(context) { }

        mediaSession = MediaSessionCompat(context, "UprunnerVoiceSession").apply {
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                    .setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                    .build(),
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        onHeadsetHookTapped()
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            isActive = true
        }
    }

    fun stop() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        textToSpeech?.shutdown()
        textToSpeech = null
        toneGenerator.release()
        abandonAudioFocus()
    }

    private fun onHeadsetHookTapped() {
        if (isListening) {
            speechRecognizer?.stopListening()
            closeListening()
        } else {
            playOpenCue()
            startListening()
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer
        isListening = true
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                closeListening()
                val transcript = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                handleTranscript(transcript)
            }

            override fun onError(error: Int) {
                closeListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer.startListening(intent)
    }

    private fun closeListening() {
        isListening = false
        playCloseCue()
    }

    private fun handleTranscript(transcript: String) {
        val telemetry = RunTrackingRepository.telemetry.value
        val responseText = when (TelemetryIntentMatcher.match(transcript)) {
            TelemetryIntent.Pace -> telemetry.currentPaceSecPerKm?.let { "Your pace is ${formatPaceForSpeech(it)}" }
                ?: "Still working out your pace."
            TelemetryIntent.Distance -> "You've gone ${formatDistanceForSpeech(telemetry.totalDistanceMeters)}"
            TelemetryIntent.Time -> "You've been running for ${formatElapsedForSpeech(telemetry.elapsedTimeMillis)}"
            null -> "I don't have an AI coach connected yet, that part's still being built."
        }
        speak(responseText)
    }

    private fun speak(text: String) {
        requestAudioFocus()
        textToSpeech?.speak(TtsFormatter.format(text), TextToSpeech.QUEUE_FLUSH, null, "uprunner-voice-response")
    }

    private fun playOpenCue() {
        requestAudioFocus()
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    private fun playCloseCue() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    private fun formatPaceForSpeech(paceSecPerKm: Double): String {
        val totalSeconds = paceSecPerKm.roundToInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d/km", minutes, seconds)
    }

    private fun formatDistanceForSpeech(meters: Double): String =
        String.format(Locale.US, "%.2f km", meters / 1000.0)

    private fun formatElapsedForSpeech(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "$minutes minutes and $seconds seconds" else "$seconds seconds"
    }
}
