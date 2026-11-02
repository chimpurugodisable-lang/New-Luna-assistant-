package com.luna.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.Locale

/**
 * Runs in the background with a notification, listening offline for "Hey Luna".
 * When it hears it: beep → listen for a command → do it → say what happened → listen again.
 */
class LunaService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_STOP = "com.luna.assistant.STOP"
        const val ACTION_LISTEN_NOW = "com.luna.assistant.LISTEN_NOW"
        private const val CHANNEL_ID = "luna_listening"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_GRAMMAR = "[\"hey luna\", \"luna\", \"[unk]\"]"

        @Volatile var isRunning = false
        var lastStatus = "Luna is off"
        var statusListener: ((String) -> Unit)? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var wakeService: SpeechService? = null
    private var wakeRecognizer: Recognizer? = null
    private var commandRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private var afterSpeech: (() -> Unit)? = null
    private var speechToken = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification("Getting ready…"),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            )
        } catch (e: Exception) {
            // Android won't let a microphone service start from the background — needs the app open.
            isRunning = false
            stopSelf()
            return
        }
        tts = TextToSpeech(this, this)
        loadModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_LISTEN_NOW -> main.post { onWake() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        main.removeCallbacksAndMessages(null)
        stopWake()
        commandRecognizer?.destroy()
        tts?.shutdown()
        model?.close()
        status("Luna is off")
        super.onDestroy()
    }

    // ---------- Wake word ("Hey Luna"), fully offline ----------

    private fun loadModel() {
        status("Loading voice model…")
        StorageService.unpack(this, "model-en-us", "model",
            { m: Model ->
                model = m
                startWake()
            },
            { e: IOException -> status("Couldn't load the voice model: ${e.message}") }
        )
    }

    private fun startWake() {
        val m = model ?: return
        if (busy) return
        stopWake()
        try {
            val rec = Recognizer(m, 16000f, WAKE_GRAMMAR)
            val svc = SpeechService(rec, 16000f)
            wakeRecognizer = rec
            wakeService = svc
            svc.startListening(wakeListener)
            status("Listening… say \"Hey Luna\"")
        } catch (e: IOException) {
            status("Microphone problem: ${e.message}")
        }
    }

    private fun stopWake() {
        wakeService?.stop()
        wakeService?.shutdown()
        wakeService = null
        wakeRecognizer?.close()
        wakeRecognizer = null
    }

    private val wakeListener = object : org.vosk.android.RecognitionListener {
        override fun onResult(hypothesis: String?) {
            val heard = runCatching { JSONObject(hypothesis ?: "{}").optString("text") }.getOrDefault("")
            if (heard.contains("luna")) main.post { onWake() }
        }
        override fun onPartialResult(hypothesis: String?) {}
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) { status("Listening error: ${exception?.message}") }
        override fun onTimeout() {}
    }

    // ---------- Command ----------

    private fun onWake() {
        if (busy) return
        busy = true
        stopWake()
        beep()
        main.postDelayed({ listenForCommand() }, 300)
    }

    private fun listenForCommand() {
        val lang = Prefs.language(this)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finish(ActionResult(Reply.NO_SPEECH_SERVICE), lang)
            return
        }
        status("I'm listening…")
        commandRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        commandRecognizer = recognizer
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                handleCommand(text, lang)
            }
            override fun onError(error: Int) = handleCommand(null, lang)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.speechTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.startListening(intent)
    }

    private fun handleCommand(text: String?, lang: LunaLanguage) {
        main.post {
            commandRecognizer?.destroy()
            commandRecognizer = null
            if (text.isNullOrBlank()) {
                finish(ActionResult(Reply.NOT_UNDERSTOOD), lang)
                return@post
            }
            status("You said: \"$text\"")
            val result = Actions(this).run(CommandParser.parse(text, lang))
            finish(result, lang)
        }
    }

    private fun finish(result: ActionResult, lang: LunaLanguage) {
        speak(result, lang) {
            busy = false
            startWake()
        }
    }

    // ---------- Speaking ----------

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { runAfterSpeech() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { runAfterSpeech() } }
        })
    }

    private fun speak(result: ActionResult, lang: LunaLanguage, then: () -> Unit) {
        val t = tts
        var spokenLang = lang
        if (t != null && ttsReady) {
            val ok = t.setLanguage(lang.locale)
            if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) {
                // No voice for this language on the phone yet: speak English, show the chosen language.
                t.setLanguage(Locale.UK)
                spokenLang = LunaLanguage.ENGLISH
            }
        }
        val shown = Phrases.get(lang, result.reply, result.arg)
        val spoken = Phrases.get(spokenLang, result.reply, result.arg)
        status("Luna: $shown")

        if (t == null || !ttsReady) { then(); return }
        afterSpeech = then
        val token = ++speechToken
        t.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "luna-reply")
        // Safety net in case the voice engine never reports it finished.
        main.postDelayed({ if (token == speechToken) runAfterSpeech() }, 10_000)
    }

    private fun runAfterSpeech() {
        val next = afterSpeech ?: return
        afterSpeech = null
        next()
    }

    private fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            main.postDelayed({ tone.release() }, 400)
        } catch (_: Exception) { }
    }

    // ---------- Notification & status ----------

    private fun status(text: String) {
        lastStatus = text
        main.post { statusListener?.invoke(text) }
        if (isRunning) {
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Luna listening", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LunaService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getService(
            this, 2, Intent(this, LunaService::class.java).setAction(ACTION_LISTEN_NOW), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_luna)
            .setContentTitle("Luna")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Talk", talk)
            .addAction(0, "Stop", stop)
            .build()
    }
}
