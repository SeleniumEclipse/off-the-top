package com.nicgames.offthetop

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowForward
import androidx.compose.material.icons.sharp.Check
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material.icons.sharp.KeyboardArrowDown
import androidx.compose.material.icons.sharp.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        PressButton("Back", onBack, icon = Icons.AutoMirrored.Sharp.ArrowBack)
        Spacer(Modifier.width(16.dp))
        Text(title, fontFamily = Headline, fontSize = 24.sp, color = LocalPress.current.ink)
    }
}

@Composable private fun HomeScreen(model: AppModel) {
    val p = LocalPress.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("home")) {
        val deckWidth = ((maxWidth - 32.dp) / 3).coerceAtLeast(175.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OFF THE TOP", color = p.ink, fontFamily = Headline, fontSize = 28.sp,
                    modifier = Modifier.weight(1f).testTag("app-title"))
                PressButton("How to play", { model.screen = Screen.HELP })
                PressButton("Settings", { model.screen = Screen.SETTINGS })
            }
            Row(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("deck-list")
                .semantics { contentDescription = "Choose a deck" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                model.decks.forEach { deck -> DeckStack(deck, { model.choose(deck) }, Modifier.width(deckWidth).fillMaxHeight()) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { model.screen = Screen.HISTORY }) { Text("Recent rounds", color = p.ink) }
                Spacer(Modifier.weight(1f))
                DurationPicker(model, Modifier.widthIn(max = 300.dp).weight(.8f, fill = false))
            }
        }
    }
}

@Composable private fun DurationPicker(model: AppModel, modifier: Modifier = Modifier) {
    val p = LocalPress.current
    Row(modifier.border(1.dp, p.edge)) {
        listOf(30, 60, 90, 120).forEach { value ->
            Box(Modifier.weight(1f).background(if (model.seconds == value) p.cardFace else p.card).clickable { model.changeSeconds(value) }
                .heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("${value}s", fontWeight = FontWeight.Bold, color = if (model.seconds == value) p.cardInk else p.ink)
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
                CategoryIcon(model.selected.id, Modifier.size(50.dp), p.ink)
                Spacer(Modifier.height(12.dp))
                Text("Hold at your forehead,\nfacing your friends.", fontFamily = Headline, fontSize = 25.sp, lineHeight = 32.sp, color = p.ink)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                Instruction(Icons.Sharp.KeyboardArrowDown, "Correct", p.correct, model.practicedCorrect)
                Instruction(Icons.Sharp.KeyboardArrowUp, "Pass", p.pass, model.practicedPass)
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
            PressButton("Start round", model::start, Modifier.widthIn(min = 220.dp), primary = true)
        }
    }
}

private fun shortTiltStatus(model: AppModel): String = when (model.tiltStatus) {
    "HOLD AT YOUR FOREHEAD" -> "Hold still"
    "READY TO TILT" -> "Ready"
    else -> "Return to start"
}

@Composable private fun Instruction(arrow: ImageVector, label: String, color: Color, done: Boolean) {
    Row(Modifier.fillMaxWidth().background(LocalPress.current.cardFace).border(1.dp, LocalPress.current.cardInk)
        .semantics { contentDescription = "$label ${if (done) "tested" else "tilt"}" }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(arrow, contentDescription = null, tint = color, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = LocalPress.current.cardInk, fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        if (done) Icon(Icons.Sharp.Check, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
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
                    Text(model.selected.title, color = p.muted)
                    Spacer(Modifier.weight(1f)); PressButton("Pause", model::pause)
                }
                PlayingCard(model.selected.id, Modifier.weight(1f).fillMaxWidth()) {
                    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("${r.countdown}", fontFamily = Headline, fontSize = (maxHeight.value * .65f).coerceIn(36f, 92f).sp,
                            color = p.correct, modifier = Modifier.testTag("countdown"))
                    }
                }
                Text("Hold still at your forehead", color = p.muted, modifier = Modifier.padding(vertical = 10.dp))
            }
        }
        Phase.PLAYING -> {
            val score = r.score
            val feedback = r.feedback
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.selected.title, color = p.muted, modifier = Modifier.weight(1f))
                    PressButton("Pause", model::pause)
                }
                PlayingCard(model.selected.id, Modifier.weight(1f).fillMaxWidth().testTag("playing-card"), feedback,
                    topEnd = {
                        Text("${ceil(r.remainingMs / 1000.0).toInt()}s", Modifier.testTag("timer"),
                            color = if (r.remainingMs <= 10_000) p.pass else p.cardInk, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    }, bottomStart = {
                        Row(Modifier.testTag("live-score").semantics(mergeDescendants = true) { contentDescription = "$score correct" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Sharp.Check, contentDescription = null, tint = p.correct, modifier = Modifier.size(24.dp))
                            Text("$score", color = p.correct, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    }) {
                    if (feedback != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        val feedbackInk = if (feedback == Outcome.CORRECT) p.correct else p.pass
                        Icon(if (feedback == Outcome.CORRECT) Icons.Sharp.Check else Icons.AutoMirrored.Sharp.ArrowForward,
                            contentDescription = null, tint = feedbackInk, modifier = Modifier.size(44.dp))
                        Text(if (feedback == Outcome.CORRECT) "Correct" else "Pass", fontFamily = Headline, fontSize = 40.sp, color = feedbackInk,
                            modifier = Modifier.testTag("feedback-${feedback.name.lowercase()}"))
                    } else AutoWord(r.currentWord.orEmpty(), p.cardInk, model::revealWord)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressButton("Pass", { model.mark(Outcome.PASS) }, Modifier.weight(1f).testTag("pass"), enabled = feedback == null, icon = Icons.Sharp.KeyboardArrowUp, pass = true)
                    Text(if (model.touchOnly || model.tilt.armed) "" else shortTiltStatus(model), Modifier.weight(1f).testTag("live-tilt-status"), textAlign = TextAlign.Center, fontSize = 14.sp, color = p.muted)
                    PressButton("Correct", { model.mark(Outcome.CORRECT) }, Modifier.weight(1f).testTag("correct"), primary = true, enabled = feedback == null, icon = Icons.Sharp.KeyboardArrowDown)
                }
            }
        }
    }
}

@Composable internal fun AutoWord(word: String, onShown: (String) -> Unit) {
    AutoWord(word, LocalPress.current.ink, onShown)
}

@Composable internal fun AutoWord(word: String, color: Color, onShown: (String) -> Unit) {
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
        Text(word.uppercase(), style = style, color = color, maxLines = 3,
            onTextLayout = { if (!it.hasVisualOverflow) onShown(word) }, modifier = Modifier.testTag("word"))
    }
}

@Composable private fun ResultsScreen(model: AppModel, answers: List<Answer>, score: Int) {
    val p = LocalPress.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(.38f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Results", fontFamily = Headline, fontSize = 28.sp, color = p.ink, modifier = Modifier.testTag("results-heading"))
            CardFace(Modifier.fillMaxWidth().height(130.dp)) {
                CategoryIcon(model.selected.id, Modifier.align(Alignment.TopEnd).padding(12.dp).size(28.dp))
                Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Text("$score", fontFamily = Headline, fontSize = 68.sp, lineHeight = 74.sp, color = p.correct, modifier = Modifier.testTag("final-score"))
                    Text(" correct", color = p.cardMuted, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
            Text("${answers.count { it.outcome == Outcome.PASS }} passed · ${answers.count { it.outcome == Outcome.UNANSWERED }} unanswered", color = p.muted)
            Spacer(Modifier.height(12.dp))
            PressButton("Play again", { model.choose(model.selected) }, Modifier.fillMaxWidth(), primary = true)
            PressButton("Change deck", model::home, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(.62f).fillMaxHeight()) {
            Text("Answers", fontFamily = Headline, fontSize = 22.sp, color = p.ink, modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(10.dp))
            Rule()
            LazyColumn(Modifier.weight(1f).background(p.cardFace).padding(horizontal = 14.dp)) {
                if (answers.isEmpty()) item { Text("No cards played", Modifier.padding(16.dp), color = p.cardMuted) }
                itemsIndexed(answers) { index, answer -> AnswerRow(answer, Modifier.testTag("answer-$index"), { model.review(index) }) }
            }
        }
    }
}

@Composable private fun AnswerRow(answer: Answer, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPress.current
    val color = when (answer.outcome) { Outcome.CORRECT -> p.correct; Outcome.PASS -> p.pass; else -> p.cardMuted }
    val icon = when (answer.outcome) { Outcome.CORRECT -> Icons.Sharp.Check; Outcome.PASS -> Icons.AutoMirrored.Sharp.ArrowForward; else -> Icons.Sharp.Close }
    val description = when (answer.outcome) { Outcome.CORRECT -> "Correct"; Outcome.PASS -> "Passed"; else -> "Unanswered" }
    Column(modifier.background(p.cardFace).semantics(mergeDescendants = true) { contentDescription = "${answer.word}, $description" }
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Change result", onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(answer.word, Modifier.weight(1f).padding(end = 12.dp), color = p.cardInk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
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
                SettingToggle("Sound effects", model.sound, model::changeSound)
                SettingToggle("Vibration", model.haptics, model::changeHaptics)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingToggle("Touch-only mode", model.touchOnly, model::setTouch)
                if (!sensors) Text("No motion sensor", color = p.muted)
                SettingToggle("Gentle tilts", model.gentle, model::changeGentle)
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

@Composable private fun SettingToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 8.dp), color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
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
                        Icon(if (expanded == record.id) Icons.Sharp.KeyboardArrowUp else Icons.Sharp.KeyboardArrowDown,
                            contentDescription = if (expanded == record.id) "Collapse answers" else "Expand answers", tint = LocalPress.current.muted, modifier = Modifier.size(24.dp))
                    }
                    if (expanded == record.id) record.answers.forEach { AnswerRow(it) }
                }
            }
        }
    }
}