package com.nicgames.offthetop

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContent { val model: AppModel = viewModel(); GameApp(model) }
    }
}

@Composable fun GameApp(model: AppModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val motion = remember(model) { MotionInput(context, model::motionSample) }
    val phase = model.round.also { model.revision }?.phase
    // No reset on feedback or countdown -> playing: that used to lose natural returns.
    val tracking = model.screen == Screen.PRACTICE || model.screen == Screen.ROUND && phase in listOf(Phase.COUNTDOWN, Phase.PLAYING)
    DisposableEffect(model.gentle, model.screen, tracking, model.touchOnly) {
        model.tilt.activationDegrees = if (model.gentle) 20f else 28f
        model.resetTilt()
        onDispose { }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { resumed = true; model.resetTilt() }
            if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
                if (model.screen == Screen.ROUND) model.pause()
                model.silence()
                model.resetTilt()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); motion.stop() }
    }
    DisposableEffect(resumed, tracking, model.touchOnly) {
        if (resumed && !model.touchOnly && tracking) motion.start()
        else motion.stop()
        onDispose { motion.stop() }
    }
    LaunchedEffect(resumed, model.screen, phase) {
        while (resumed && model.screen == Screen.ROUND && model.round?.phase in listOf(Phase.COUNTDOWN, Phase.PLAYING)) { model.tick(); delay(40) }
    }
    BackHandler(model.screen != Screen.HOME) {
        if (model.screen == Screen.ROUND && phase != Phase.FINISHED) model.pause() else model.home()
    }
    PressTheme(model.theme) {
        PageBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(horizontal = 22.dp, vertical = 12.dp)) {
                when (model.screen) {
                    Screen.HOME -> HomeScreen(model)
                    Screen.PRACTICE -> PracticeScreen(model, motion.available)
                    Screen.ROUND -> RoundScreen(model)
                    Screen.SETTINGS -> SettingsScreen(model, motion.available)
                    Screen.HELP -> HelpScreen(model)
                    Screen.HISTORY -> HistoryScreen(model)
                }
            }
        }
    }
}

@Composable private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PressButton("‹ Back", onBack)
        Spacer(Modifier.width(16.dp))
        Text(title, fontFamily = Headline, fontSize = 24.sp, color = LocalPress.current.ink)
    }
}

@Composable private fun HomeScreen(model: AppModel) {
    val p = LocalPress.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(0.36f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("OFF\nTHE TOP", color = p.ink, fontFamily = Headline, fontSize = 48.sp, lineHeight = 48.sp)
            Box(Modifier.width(72.dp).height(4.dp).background(p.accent))
            Spacer(Modifier.height(8.dp))
            PressButton("How to play", { model.screen = Screen.HELP }, Modifier.fillMaxWidth())
            PressButton("Settings", { model.screen = Screen.SETTINGS }, Modifier.fillMaxWidth())
            Text("Recent rounds", Modifier.heightIn(min = 48.dp).fillMaxWidth().clickable(role = Role.Button) { model.screen = Screen.HISTORY }.padding(vertical = 10.dp),
                color = p.ink, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(0.64f).fillMaxHeight()) {
            Text("Choose a deck", color = p.muted, fontSize = 16.sp, modifier = Modifier.padding(bottom = 14.dp).testTag("deck-heading"))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                itemsIndexed(model.decks) { index, deck ->
                    val accent = p.accent
                    Row(Modifier.fillMaxWidth().border(2.dp, p.edge).background(p.card).clickable(role = Role.Button) { model.choose(deck) }
                        .semantics { contentDescription = "Choose ${deck.title}, ${deck.words.size} cards" }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DeckMark(index, accent, Modifier.size(48.dp).background(p.wash).padding(6.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(deck.title, color = p.ink, fontFamily = Headline, fontSize = 23.sp)
                            Text(listOf("Nature", "Objects & food", "Actions & places")[index], fontSize = 14.sp, color = p.muted)
                        }
                        Text("${deck.words.size} cards", fontFamily = Body, color = p.muted, fontSize = 13.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Time", color = p.muted, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                DurationPicker(model, Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun DeckMark(index: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        when (index) {
            0 -> {
                drawOval(color, Offset(w * .1f, w * .15f), Size(w * .32f, w * .55f))
                drawOval(color, Offset(w * .5f, w * .03f), Size(w * .32f, w * .55f))
                drawLine(color, Offset(w * .47f, w * .32f), Offset(w * .47f, w * .98f), w * .07f)
            }
            1 -> {
                drawRect(color, Offset(w * .08f, w * .25f), Size(w * .58f, w * .63f))
                drawCircle(color, w * .21f, Offset(w * .73f, w * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .09f))
            }
            else -> {
                drawCircle(color, w * .13f, Offset(w * .58f, w * .12f))
                drawLine(color, Offset(w * .52f, w * .3f), Offset(w * .38f, w * .59f), w * .1f)
                drawLine(color, Offset(w * .49f, w * .36f), Offset(w * .8f, w * .5f), w * .08f)
                drawLine(color, Offset(w * .4f, w * .55f), Offset(w * .68f, w * .88f), w * .1f)
                drawLine(color, Offset(w * .4f, w * .55f), Offset(w * .16f, w * .87f), w * .1f)
            }
        }
    }
}

@Composable private fun DurationPicker(model: AppModel, modifier: Modifier = Modifier) {
    val p = LocalPress.current
    Row(modifier.border(1.dp, p.edge)) {
        listOf(30, 60, 90, 120).forEach { value ->
            Box(Modifier.weight(1f).background(if (model.seconds == value) p.accent else p.card).clickable { model.changeSeconds(value) }
                .heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("${value}s", fontWeight = FontWeight.Bold, color = if (model.seconds == value) p.onAccent else p.ink)
            }
        }
    }
}

@Composable private fun PracticeScreen(model: AppModel, sensors: Boolean) {
    val p = LocalPress.current
    var setupOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(model.selected.title, model::home)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                PhoneMark(Modifier.size(110.dp))
                Spacer(Modifier.height(14.dp))
                Text("Phone to forehead", fontFamily = Headline, fontSize = 25.sp, color = p.ink)
                Text("Hold still. Screen facing friends.", color = p.muted, modifier = Modifier.padding(top = 8.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Instruction("↓", "Correct", p.accent, model.practicedCorrect)
                Instruction("↑", "Pass", p.ink, model.practicedPass)
                Text(if (sensors && !model.touchOnly) shortTiltStatus(model) else "Touch controls",
                    Modifier.fillMaxWidth().testTag("practice-feedback"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = p.muted)
                if (sensors && !model.touchOnly) {
                    TextButton(onClick = { setupOpen = !setupOpen }) { Text(if (setupOpen) "Hide setup" else "Tilt setup") }
                    if (setupOpen) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.tiltReading, Modifier.weight(1f).testTag("tilt-reading"), fontSize = 14.sp, color = p.muted)
                        PressButton("Reset tilt", model::resetTilt)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${model.seconds}s · ${model.unseen(model.selected)} unseen", Modifier.weight(1f).testTag("round-options"), color = p.muted)
            PressButton("Start round  →", model::start, Modifier.widthIn(min = 220.dp), primary = true)
        }
    }
}

@Composable private fun PhoneMark(modifier: Modifier) {
    val p = LocalPress.current
    Canvas(modifier) {
        val w = size.width
        drawCircle(p.wash, w * .48f)
        drawRect(p.shadow, Offset(w * .14f, w * .34f), Size(w * .76f, w * .42f))
        drawRect(p.card, Offset(w * .1f, w * .3f), Size(w * .76f, w * .42f))
        drawRect(p.accent, Offset(w * .1f, w * .3f), Size(w * .76f, w * .42f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        drawLine(p.accent, Offset(w * .35f, w * .51f), Offset(w * .61f, w * .51f), 3.dp.toPx())
    }
}

private fun shortTiltStatus(model: AppModel): String = when (model.tiltStatus) {
    "HOLD AT YOUR FOREHEAD" -> "Hold still"
    "READY TO TILT" -> "Ready"
    else -> "Return to start"
}

@Composable private fun Instruction(arrow: String, label: String, color: Color, done: Boolean) {
    Row(Modifier.fillMaxWidth().background(LocalPress.current.card).border(1.dp, LocalPress.current.edge)
        .semantics { contentDescription = "$label ${if (done) "tested" else "tilt"}" }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Text(label, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        if (done) Text("✓", color = color, fontSize = 22.sp)
    }
}

@Composable private fun RoundScreen(model: AppModel) {
    val r = model.round.also { model.revision } ?: return
    val p = LocalPress.current
    when (r.phase) {
        Phase.FINISHED -> ResultsScreen(model, r.answers, r.score)
        Phase.PAUSED -> {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Paused", fontFamily = Headline, fontSize = 52.sp, color = p.ink)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PressButton("End & review", model::finish)
                    PressButton("Resume round", model::resume, primary = true)
                }
            }
        }
        Phase.COUNTDOWN, Phase.READY -> {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f)); PressButton("Pause", model::pause)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${r.countdown}", fontFamily = Headline, fontSize = 92.sp, lineHeight = 100.sp, color = p.accent, modifier = Modifier.testTag("countdown"))
                    Text("Hold still at your forehead", color = p.muted)
                }
            }
        }
        Phase.PLAYING -> {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ ${r.score}", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 24.sp,
                        modifier = Modifier.weight(1f).testTag("live-score").semantics { contentDescription = "${r.score} correct" })
                    PressButton("Pause", model::pause)
                    Text("${ceil(r.remainingMs / 1000.0).toInt()}s", Modifier.weight(1f).testTag("timer"), textAlign = TextAlign.End,
                        color = if (r.remainingMs <= 10_000) p.accent else p.ink, fontFamily = Headline, fontSize = 30.sp)
                }
                val progress = r.remainingMs.toFloat() / (r.durationSeconds * 1000f)
                Box(Modifier.fillMaxWidth().height(4.dp).background(p.ink.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(p.accent))
                }
                val feedback = r.feedback
                val cardColor = when (feedback) { Outcome.CORRECT -> p.accent; Outcome.PASS -> p.ink; else -> p.card }
                val feedbackInk = if (feedback == Outcome.CORRECT) p.onAccent else p.paper
                Box(Modifier.weight(1f).fillMaxWidth().border(2.dp, p.edge).background(cardColor).padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    if (feedback != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (feedback == Outcome.CORRECT) "✓ GOT IT!" else "↷ PASS", fontFamily = Headline, fontSize = 48.sp, color = feedbackInk)
                    } else AutoWord(r.currentWord.orEmpty(), model::revealWord)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressButton("↑ PASS", { model.mark(Outcome.PASS) }, Modifier.weight(1f).testTag("pass"), enabled = feedback == null)
                    Text(if (model.touchOnly || model.tilt.armed) "" else shortTiltStatus(model), Modifier.weight(1f).testTag("live-tilt-status"), textAlign = TextAlign.Center, fontSize = 14.sp, color = p.muted)
                    PressButton("↓ GOT IT", { model.mark(Outcome.CORRECT) }, Modifier.weight(1f).testTag("correct"), primary = true, enabled = feedback == null)
                }
            }
        }
    }
}

@Composable internal fun AutoWord(word: String, onShown: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val style = remember(word, maxWidth, maxHeight, density) {
            val bounds = Constraints(maxWidth = with(density) { maxWidth.roundToPx() }, maxHeight = with(density) { maxHeight.roundToPx() })
            var candidate = 76
            var chosen: TextStyle
            do {
                chosen = TextStyle(fontFamily = Headline, fontSize = candidate.sp, lineHeight = (candidate * 1.08f).sp, textAlign = TextAlign.Center)
                val fits = !measurer.measure(word.uppercase(), chosen, constraints = bounds, maxLines = 3).hasVisualOverflow
                if (fits || candidate <= 6) break
                candidate -= 2
            } while (true)
            chosen
        }
        Text(word.uppercase(), style = style, color = LocalPress.current.ink, maxLines = 3,
            onTextLayout = { if (!it.hasVisualOverflow) onShown(word) }, modifier = Modifier.testTag("word"))
    }
}

@Composable private fun ResultsScreen(model: AppModel, answers: List<Answer>, score: Int) {
    val p = LocalPress.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(.38f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Results", fontFamily = Headline, fontSize = 28.sp, color = p.ink, modifier = Modifier.testTag("results-heading"))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$score", fontFamily = Headline, fontSize = 76.sp, lineHeight = 80.sp, color = p.accent, modifier = Modifier.testTag("final-score"))
                Text(" correct", color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("${answers.count { it.outcome == Outcome.PASS }} passed · ${answers.count { it.outcome == Outcome.UNANSWERED }} unanswered", color = p.muted)
            Spacer(Modifier.height(12.dp))
            PressButton("Play again", { model.choose(model.selected) }, Modifier.fillMaxWidth(), primary = true)
            PressButton("Change deck", model::home, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(.62f).fillMaxHeight()) {
            Text("Answers", fontFamily = Headline, fontSize = 22.sp, color = p.ink, modifier = Modifier.padding(vertical = 6.dp))
            Text("Tap to correct", color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Rule()
            LazyColumn(Modifier.weight(1f)) {
                if (answers.isEmpty()) item { Text("No cards played", Modifier.padding(16.dp), color = p.muted) }
                itemsIndexed(answers) { index, answer -> AnswerRow(answer, Modifier.testTag("answer-$index"), { model.review(index) }) }
            }
        }
    }
}

@Composable private fun AnswerRow(answer: Answer, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPress.current
    val color = when (answer.outcome) { Outcome.CORRECT -> p.accent; Outcome.PASS -> p.ink; else -> p.muted }
    val label = when (answer.outcome) { Outcome.CORRECT -> "✓"; Outcome.PASS -> "↷"; else -> "—" }
    val description = when (answer.outcome) { Outcome.CORRECT -> "Correct"; Outcome.PASS -> "Passed"; else -> "Unanswered" }
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "${answer.word}, $description" }
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(answer.word, Modifier.weight(1f).padding(end = 12.dp), color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Rule()
    }
}

@Composable private fun SettingsScreen(model: AppModel, sensors: Boolean) {
    val p = LocalPress.current
    var resetDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Settings", model::home)
        Rule()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Round length", color = p.muted)
                DurationPicker(model)
                SettingToggle("Sound effects", null, model.sound, model::changeSound)
                SettingToggle("Vibration", null, model.haptics, model::changeHaptics)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingToggle("Touch-only mode", if (sensors) "Tap instead of tilting" else "No motion sensor", model.touchOnly, model::setTouch)
                SettingToggle("Gentle tilts", "Smaller nods", model.gentle, model::changeGentle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Day", "Night").forEach { label -> PressButton(label, { model.changeTheme(label) }, Modifier.weight(1f), primary = model.theme == label) }
                }
                PressButton("Reshuffle all cards", { resetDialog = true }, Modifier.fillMaxWidth())
            }
        }
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("Make all cards available?") }, text = { Text("Previously played cards may appear again. Your round history is kept.") },
        confirmButton = { TextButton(onClick = { model.resetSeen(); resetDialog = false }) { Text("Reshuffle") } }, dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancel") } })
}

@Composable private fun SettingToggle(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            detail?.let { Text(it, color = LocalPress.current.muted, fontSize = 13.sp) }
        }
        Switch(checked, onChange, Modifier.semantics { contentDescription = title })
    }
}

@Composable private fun HelpScreen(model: AppModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("How to play", model::home)
        Rule()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem("Hold", "Phone sideways at your forehead, screen facing friends. Hold still during the countdown.")
                HelpItem("Guess", "Friends describe or act out the card without saying or spelling its words.")
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem("Tilt", "Down for correct, up to pass. Return to your starting angle. Or use the buttons.")
                HelpItem("Score", "One point per correct answer. No penalty for passing. Tap results to fix mistakes.")
            }
        }
        Text("Leaving the app pauses the timer. Resume resets your tilt.", color = LocalPress.current.muted, fontSize = 14.sp)
    }
}

@Composable private fun HelpItem(title: String, text: String) {
    Text(title, fontFamily = Headline, fontSize = 22.sp, color = LocalPress.current.accent)
    Text(text, color = LocalPress.current.ink, fontSize = 16.sp)
}

@Composable private fun HistoryScreen(model: AppModel) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Recent rounds", model::home)
        Rule()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.history.isEmpty()) item { Text("No rounds yet", color = LocalPress.current.muted, modifier = Modifier.padding(24.dp)) }
            itemsIndexed(model.history) { _, record ->
                Column(Modifier.fillMaxWidth().border(1.dp, LocalPress.current.edge).background(LocalPress.current.card).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == record.id) null else record.id }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${record.score}", fontFamily = Headline, fontSize = 32.sp, color = LocalPress.current.accent)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(record.deck, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("${record.seconds}s · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.id))}", color = LocalPress.current.muted, fontSize = 13.sp)
                        }
                        Text(if (expanded == record.id) "−" else "+", fontSize = 24.sp, color = LocalPress.current.muted)
                    }
                    if (expanded == record.id) record.answers.forEach { AnswerRow(it) }
                }
            }
        }
    }
}