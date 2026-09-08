package com.nicgames.offthetop

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

data class Deck(val id: String, val title: String, val subtitle: String, val examples: String, val words: List<String>)
data class RoundRecord(val id: Long, val deck: String, val seconds: Int, val answers: List<Answer>) {
    val score get() = answers.count { it.outcome == Outcome.CORRECT }
}
enum class Screen { HOME, PRACTICE, ROUND, SETTINGS, HELP, HISTORY }

class AppModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("off-the-top", Context.MODE_PRIVATE)
    val decks = listOf(
        deck("wild-world", "Wild World", "ANIMALS, EARTH & SKY", "Elephant · Rainbow · Venus Flytrap"),
        deck("everyday", "Everyday Things", "OBJECTS, FOOD & MORE", "Waffle · Wheelbarrow · Saxophone"),
        deck("do-your-thing", "Do Your Thing", "ACTIONS, JOBS & PLACES", "Missing the Bus · Pilot · Bowling"),
    )
    var screen by mutableStateOf(Screen.HOME)
    var selected by mutableStateOf(decks.first())
    var seconds by mutableIntStateOf(prefs.getInt("seconds", 60).takeIf { it in listOf(30, 60, 90, 120) } ?: 60)
        private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var touchOnly by mutableStateOf(prefs.getBoolean("touch", false)); private set
    var theme by mutableStateOf(prefs.getString("theme", "System") ?: "System"); private set
    var gentle by mutableStateOf(prefs.getBoolean("gentle", false)); private set
    var round by mutableStateOf<RoundEngine?>(null); private set
    var revision by mutableIntStateOf(0); private set
    var practiceFeedback by mutableStateOf("Hold still at your forehead to get ready")
    var practicedCorrect by mutableStateOf(false)
    var practicedPass by mutableStateOf(false)
    val tilt = TiltDetector()
    var tiltStatus by mutableStateOf("HOLD AT YOUR FOREHEAD"); private set
    var tiltReading by mutableStateOf("Learning your starting angle…"); private set
    var history by mutableStateOf(readHistory()); private set
    private var roundId = 0L
    private var previousSecond = -1
    private var previousPhase = Phase.READY
    private var saved = false
    private val shownThisRound = mutableSetOf<String>()
    private var displayedWord: String? = null
    private var tone: ToneGenerator? = null
    private val vibrator = application.getSystemService(Vibrator::class.java)

    private fun deck(id: String, title: String, subtitle: String, examples: String): Deck {
        val words = getApplication<Application>().assets.open("decks/$id.txt").bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        }
        require(words.size >= 500 && words.map { it.lowercase() }.distinct().size == words.size)
        return Deck(id, title, subtitle, examples, words)
    }

    fun changeSeconds(value: Int) { require(value in listOf(30, 60, 90, 120)); seconds = value; prefs.edit().putInt("seconds", value).apply() }
    fun changeSound(value: Boolean) { sound = value; prefs.edit().putBoolean("sound", value).apply() }
    fun changeHaptics(value: Boolean) { haptics = value; prefs.edit().putBoolean("haptics", value).apply() }
    fun setTouch(value: Boolean) { touchOnly = value; prefs.edit().putBoolean("touch", value).apply() }
    fun changeTheme(value: String) { theme = value; prefs.edit().putString("theme", value).apply() }
    fun changeGentle(value: Boolean) { gentle = value; prefs.edit().putBoolean("gentle", value).apply() }

    fun choose(deck: Deck) {
        selected = deck
        practiceFeedback = "Hold still at your forehead to get ready"
        practicedCorrect = false
        practicedPass = false
        screen = Screen.PRACTICE
    }

    fun start() {
        val seen = prefs.getStringSet("seen-${selected.id}", emptySet()).orEmpty()
        var available = selected.words.filterNot { it in seen }
        if (available.isEmpty()) {
            prefs.edit().remove("seen-${selected.id}").apply()
            available = selected.words
        }
        round = RoundEngine(available.shuffled(), seconds)
        shownThisRound.clear()
        displayedWord = null
        saved = false
        roundId = System.currentTimeMillis()
        previousSecond = -1
        previousPhase = Phase.READY
        screen = Screen.ROUND
        round?.begin(now())
        refresh()
    }

    fun tick() { if (screen == Screen.ROUND) { round?.tick(now()); refresh() } }
    fun mark(outcome: Outcome) {
        val r = round ?: return
        if (displayedWord == null || displayedWord != r.currentWord) return
        if (r.mark(outcome, now())) { displayedWord = null; signal(outcome) }
        refresh()
    }
    fun pause() { displayedWord = null; round?.pause(now()); refresh() }
    fun resume() { round?.resume(now()); refresh() }
    fun finish() { round?.finish(now()); refresh() }
    fun review(index: Int) { round?.correctAnswer(index); saveRound(); revision++ }
    fun home() { screen = Screen.HOME }
    fun unseen(deck: Deck): Int = deck.words.count { it !in prefs.getStringSet("seen-${deck.id}", emptySet()).orEmpty() }
    fun resetSeen() { prefs.edit().apply { decks.forEach { remove("seen-${it.id}") } }.apply(); revision++ }

    // Called after the actual word has a fitting text layout, not merely after a timer tick.
    fun revealWord(word: String) {
        val r = round ?: return
        if (r.phase != Phase.PLAYING || r.feedback != null || r.currentWord != word) return
        displayedWord = word
        if (shownThisRound.add(word)) {
            val seen = prefs.getStringSet("seen-${selected.id}", emptySet()).orEmpty().toMutableSet()
            seen += word
            prefs.edit().putStringSet("seen-${selected.id}", seen).apply()
        }
    }

    fun practice(outcome: Outcome) {
        if (outcome == Outcome.CORRECT) practicedCorrect = true else practicedPass = true
        practiceFeedback = if (outcome == Outcome.CORRECT) "GOT IT! Return to your starting angle." else "PASS! Return to your starting angle."
        signal(outcome)
    }

    fun resetTilt() {
        tilt.reset()
        updateTiltStatus()
    }

    /** Always track the return during countdown/feedback, but only score a visible card. */
    fun motionSample(x: Float, y: Float, z: Float, timestampMs: Long) {
        if (touchOnly) return
        val practice = screen == Screen.PRACTICE
        val r = round
        if (!practice && (screen != Screen.ROUND || r?.phase !in listOf(Phase.COUNTDOWN, Phase.PLAYING))) return
        val event = tilt.sample(x, y, z, timestampMs,
            calibrating = !practice && r?.phase == Phase.COUNTDOWN,
            acceptTilt = practice || r?.phase == Phase.PLAYING && r.feedback == null && displayedWord != null && displayedWord == r.currentWord)
        updateTiltStatus()
        if (event != null) { if (practice) practice(event) else mark(event) }
    }

    private fun updateTiltStatus() {
        tiltStatus = when {
            !tilt.calibrated -> "HOLD AT YOUR FOREHEAD"
            tilt.armed -> "READY TO TILT"
            else -> "RETURN TO YOUR STARTING ANGLE"
        }
        val degrees = kotlin.math.abs(tilt.relativeDegrees).toInt()
        tiltReading = when {
            !tilt.calibrated -> "Learning your starting angle…"
            degrees < 5 -> "At your starting angle"
            tilt.relativeDegrees < 0 -> "Down $degrees°"
            else -> "Up $degrees°"
        }
    }

    private fun refresh() {
        val r = round ?: return
        val second = if (r.phase == Phase.COUNTDOWN) r.countdown else ceil(r.remainingMs / 1000.0).toInt()
        if (r.phase == Phase.COUNTDOWN && (previousPhase != r.phase || previousSecond != second)) beep(ToneGenerator.TONE_PROP_BEEP, 90)
        if (r.phase == Phase.PLAYING && previousPhase == Phase.COUNTDOWN) beep(ToneGenerator.TONE_PROP_ACK, 150)
        if (r.phase == Phase.PLAYING && second in 1..5 && second != previousSecond) beep(ToneGenerator.TONE_PROP_BEEP, 65)
        if (r.phase == Phase.FINISHED && !saved) {
            saved = true
            saveRound()
            beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 550)
            vibrate(longArrayOf(0, 130, 80, 130, 80, 220))
        }
        previousSecond = second
        previousPhase = r.phase
        revision++
    }

    private fun signal(outcome: Outcome) {
        beep(if (outcome == Outcome.CORRECT) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 180)
        vibrate(if (outcome == Outcome.CORRECT) longArrayOf(0, 100) else longArrayOf(0, 65, 65, 65))
    }
    private fun beep(kind: Int, duration: Int) {
        if (!sound) return
        runCatching { if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65); tone?.startTone(kind, duration) }
    }
    private fun vibrate(pattern: LongArray) {
        if (haptics && vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
    fun silence() { tone?.stopTone(); vibrator?.cancel() }
    private fun saveRound() {
        val r = round ?: return
        val record = RoundRecord(roundId, selected.title, seconds, r.answers)
        history = (listOf(record) + history.filterNot { it.id == roundId }).take(20)
        val json = JSONArray()
        history.forEach { item ->
            val answers = JSONArray()
            item.answers.forEach { answers.put(JSONObject().put("word", it.word).put("outcome", it.outcome.name)) }
            json.put(JSONObject().put("id", item.id).put("deck", item.deck).put("seconds", item.seconds).put("answers", answers))
        }
        prefs.edit().putString("history", json.toString()).apply()
    }
    private fun readHistory(): List<RoundRecord> = runCatching {
        val array = JSONArray(prefs.getString("history", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val rows = item.getJSONArray("answers")
            RoundRecord(item.getLong("id"), item.getString("deck"), item.getInt("seconds"), (0 until rows.length()).map {
                val answer = rows.getJSONObject(it)
                Answer(answer.getString("word"), Outcome.valueOf(answer.getString("outcome")))
            })
        }
    }.getOrDefault(emptyList())

    override fun onCleared() { tone?.release(); super.onCleared() }
    companion object { fun now(): Long = SystemClock.elapsedRealtime() }
}

/** Prefer responsive accelerometer samples over vendor-dependent gravity sensor lag. */
class MotionInput(context: Context, private val onSample: (Float, Float, Float, Long) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    val available get() = sensor != null
    private val filter = MotionFilter()
    fun start() { filter.reset(); sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } }
    fun stop() { manager.unregisterListener(this) }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val time = event.timestamp / 1_000_000L
        val vector = if (sensor?.type == Sensor.TYPE_GRAVITY) event.values else filter.sample(event.values[0], event.values[1], event.values[2], time)
        onSample(vector[0], vector[1], vector[2], time)
    }
}