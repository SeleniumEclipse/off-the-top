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
    val detector = remember { TiltDetector() }
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val motion = remember {
        MotionInput(context) { x, y, z ->
            if (!model.touchOnly && (model.screen == Screen.PRACTICE || model.screen == Screen.ROUND && model.round?.phase == Phase.PLAYING && model.round?.feedback == null)) {
                val event = detector.sample(x, y, z, AppModel.now())
                if (event != null) {
                    if (model.screen == Screen.PRACTICE) model.practice(event) else model.mark(event)
                }
            }
        }
    }
    val phase = model.round.also { model.revision }?.phase
    val feedback = model.round?.feedback
    LaunchedEffect(model.gentle, model.screen, phase, model.touchOnly, feedback) {
        detector.threshold = if (model.gentle) 0.52f else 0.68f
        detector.reset()
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { resumed = true; detector.reset() }
            if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
                if (model.screen == Screen.ROUND) model.pause()
                model.silence()
                detector.reset()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); motion.stop() }
    }
    DisposableEffect(resumed, model.screen, phase, model.touchOnly) {
        if (resumed && !model.touchOnly && (model.screen == Screen.PRACTICE || model.screen == Screen.ROUND && phase == Phase.PLAYING)) motion.start()
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
        Surface(Modifier.fillMaxSize(), color = LocalPress.current.paper) {
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

@Composable private fun Header(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PressButton("‹ Back", onBack)
        Spacer(Modifier.width(16.dp))
        Text(title, fontFamily = Headline, fontSize = 24.sp, color = LocalPress.current.ink)
        Spacer(Modifier.weight(1f))
        subtitle?.let { Kicker(it) }
    }
}

@Composable private fun HomeScreen(model: AppModel) {
    val p = LocalPress.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(0.40f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker("THE FOREHEAD GUESSING GAME")
            Text("OFF\nTHE TOP", color = p.ink, fontFamily = Headline, fontSize = 48.sp, lineHeight = 48.sp)
            Rule(colored = true)
            Text("Good clues. Wild guesses.\nOne phone. Everyone plays.", color = p.muted, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton("How to play", { model.screen = Screen.HELP }, Modifier.weight(1f))
                PressButton("Settings", { model.screen = Screen.SETTINGS }, Modifier.weight(1f))
            }
            Text("Recent rounds  →", Modifier.heightIn(min = 48.dp).fillMaxWidth().clickable { model.screen = Screen.HISTORY }.padding(vertical = 10.dp),
                color = p.ink, fontWeight = FontWeight.Bold)
            Kicker("1,844 CARDS  /  ALL OFFLINE", color = p.muted)
        }
        Column(Modifier.weight(0.60f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker("PICK YOUR DECK")
                Spacer(Modifier.weight(1f))
                Kicker("3 DECKS · NO ADS")
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                itemsIndexed(model.decks) { index, deck ->
                    val accent = listOf(p.green, p.red, p.purple)[index]
                    Row(Modifier.fillMaxWidth().border(2.dp, p.ink).background(p.card).clickable { model.choose(deck) }
                        .semantics { contentDescription = "Choose ${deck.title}, ${deck.words.size} cards" }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        DeckMark(index, accent, Modifier.size(42.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(deck.title, color = p.ink, fontFamily = Headline, fontSize = 23.sp)
                            Kicker(deck.subtitle, color = accent)
                            Text(deck.examples, fontSize = 13.sp, color = p.muted, modifier = Modifier.padding(top = 3.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${deck.words.size}", fontFamily = Body, fontWeight = FontWeight.Bold, color = accent, fontSize = 22.sp)
                            Kicker("CARDS")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Kicker("ROUND")
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
    Row(modifier.border(1.dp, p.ink)) {
        listOf(30, 60, 90, 120).forEach { value ->
            Box(Modifier.weight(1f).background(if (model.seconds == value) p.ink else p.card).clickable { model.changeSeconds(value) }
                .heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                Text("${value}s", fontWeight = FontWeight.Bold, color = if (model.seconds == value) p.paper else p.ink)
            }
        }
    }
}

@Composable private fun PracticeScreen(model: AppModel, sensors: Boolean) {
    val p = LocalPress.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(model.selected.title, model::home, "${model.selected.words.size} CARDS")
        Rule(colored = true)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phone to forehead.\nFriends give the clues.", fontFamily = Headline, fontSize = 27.sp, lineHeight = 30.sp, color = p.ink)
                Text("Hold it sideways, screen facing your friends. Describe or act out the card without saying its words.", color = p.muted)
                Text(if (sensors && !model.touchOnly) "Try both tilts here first. Nothing is scored." else "Touch mode: a friend taps PASS or GOT IT. Tilt is optional.", color = p.ink, fontWeight = FontWeight.SemiBold)
                Kicker("${model.unseen(model.selected)} UNSEEN · ${model.seconds} SECOND ROUND")
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Instruction("↓", "SCREEN TO FLOOR", "Got it!", p.green, model.practicedCorrect)
                Instruction("↑", "SCREEN TO CEILING", "Pass", p.red, model.practicedPass)
                Text(if (sensors && !model.touchOnly) model.practiceFeedback else "Large touch buttons stay available during every round.",
                    Modifier.fillMaxWidth().background(p.card).padding(10.dp).testTag("practice-feedback"), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = p.ink)
                Text("Return upright between tilts. Keep movements gentle.", fontSize = 13.sp, color = p.muted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("You’ll get a 3-second countdown.", Modifier.weight(1f), color = p.muted)
            PressButton("Start round  →", model::start, Modifier.widthIn(min = 220.dp), primary = true)
        }
    }
}

@Composable private fun Instruction(arrow: String, title: String, label: String, color: Color, done: Boolean) {
    Row(Modifier.fillMaxWidth().border(1.dp, color).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✓" else arrow, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Column { Kicker(title, color = color); Text(label, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}

@Composable private fun RoundScreen(model: AppModel) {
    val r = model.round.also { model.revision } ?: return
    val p = LocalPress.current
    when (r.phase) {
        Phase.FINISHED -> ResultsScreen(model, r.answers, r.score)
        Phase.PAUSED -> {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Kicker("TAKE A BREATHER")
                Text("ON HOLD", fontFamily = Headline, fontSize = 52.sp, color = p.ink)
                Text("The clock is stopped. Your current card is hidden.", color = p.muted)
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
                    Kicker(model.selected.title.uppercase()); Spacer(Modifier.weight(1f)); PressButton("Pause", model::pause)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PHONE TO FOREHEAD", fontFamily = Headline, fontSize = 29.sp, color = p.ink)
                    Text("${r.countdown}", fontFamily = Headline, fontSize = 92.sp, lineHeight = 100.sp, color = p.purple, modifier = Modifier.testTag("countdown"))
                    Text("Screen facing your friends. Hold it upright.", color = p.muted)
                }
                Rule(colored = true)
            }
        }
        Phase.PLAYING -> {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Kicker(model.selected.title.uppercase()); Text("${r.score} CORRECT", color = p.green, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("live-score")) }
                    PressButton("Pause", model::pause)
                    Text("${ceil(r.remainingMs / 1000.0).toInt()}s", Modifier.weight(1f).testTag("timer"), textAlign = TextAlign.End,
                        color = if (r.remainingMs <= 10_000) p.red else p.ink, fontFamily = Headline, fontSize = 30.sp)
                }
                val progress = r.remainingMs.toFloat() / (r.durationSeconds * 1000f)
                Box(Modifier.fillMaxWidth().height(4.dp).background(p.ink.copy(alpha = .12f))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(if (r.remainingMs <= 10_000) p.red else p.purple))
                }
                val feedback = r.feedback
                val cardColor = when (feedback) { Outcome.CORRECT -> Day.green; Outcome.PASS -> Day.red; else -> p.card }
                Box(Modifier.weight(1f).fillMaxWidth().border(2.dp, p.ink).background(cardColor).padding(horizontal = 24.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    if (feedback != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (feedback == Outcome.CORRECT) "✓ GOT IT!" else "↷ PASS", fontFamily = Headline, fontSize = 48.sp, color = Color.White)
                        Text("Bring the screen upright", color = Color.White, fontWeight = FontWeight.Bold)
                    } else AutoWord(r.currentWord.orEmpty(), model::revealWord)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    PressButton("↑ PASS", { model.mark(Outcome.PASS) }, Modifier.weight(1f).testTag("pass"), enabled = feedback == null)
                    Text(if (model.touchOnly) "FRIENDS TAP TO SCORE" else "↓ FLOOR = GOT IT\n↑ CEILING = PASS", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = p.muted, fontWeight = FontWeight.Bold)
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
            Kicker("ROUND COMPLETE")
            Text("NICE\nGUESSING.", fontFamily = Headline, fontSize = 31.sp, lineHeight = 33.sp, color = p.ink)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$score", fontFamily = Headline, fontSize = 76.sp, lineHeight = 80.sp, color = p.green, modifier = Modifier.testTag("final-score"))
                Text(" correct", color = p.muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text("${answers.count { it.outcome == Outcome.PASS }} passed · ${answers.count { it.outcome == Outcome.UNANSWERED }} unanswered", color = p.muted)
            Rule(colored = true)
            PressButton("Play again", { model.choose(model.selected) }, Modifier.fillMaxWidth(), primary = true)
            PressButton("Change deck", model::home, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(.62f).fillMaxHeight()) {
            Kicker("${model.selected.title.uppercase()} · ${model.seconds}s")
            Text("THE ROUND, RECAPTURED", fontFamily = Headline, fontSize = 22.sp, color = p.ink, modifier = Modifier.padding(vertical = 6.dp))
            Text("Wrong tilt? Tap an answer to change its result.", color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Rule()
            LazyColumn(Modifier.weight(1f)) {
                if (answers.isEmpty()) item { Text("No cards played yet. Give it another go!", Modifier.padding(16.dp), color = p.muted) }
                itemsIndexed(answers) { index, answer -> AnswerRow(answer, Modifier.testTag("answer-$index"), { model.review(index) }) }
            }
        }
    }
}

@Composable private fun AnswerRow(answer: Answer, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPress.current
    val color = when (answer.outcome) { Outcome.CORRECT -> p.green; Outcome.PASS -> p.red; else -> p.muted }
    val label = when (answer.outcome) { Outcome.CORRECT -> "✓ CORRECT"; Outcome.PASS -> "↷ PASSED"; else -> "— NO ANSWER" }
    Column(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(answer.word, Modifier.weight(1f).padding(end = 12.dp), color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Rule()
    }
}

@Composable private fun SettingsScreen(model: AppModel, sensors: Boolean) {
    val p = LocalPress.current
    var resetDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Make it yours", model::home)
        Rule(colored = true)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("THE ROUND")
                DurationPicker(model)
                SettingToggle("Sound effects", "Countdown, correct, pass and time’s up.", model.sound, model::changeSound)
                SettingToggle("Vibration", "One buzz for correct; two for pass.", model.haptics, model::changeHaptics)
                Text("Use your phone’s media-volume buttons for sound.", fontSize = 13.sp, color = p.muted)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Kicker("CONTROLS & PAPER")
                SettingToggle("Touch-only mode", if (sensors) "Turn off motion; friends tap the buttons." else "No motion sensor found. Use the touch buttons.", model.touchOnly, model::setTouch)
                SettingToggle("Gentle tilts", "Less movement needed to register a tilt.", model.gentle, model::changeGentle)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Day", "Night").forEach { label -> PressButton(label, { model.changeTheme(label) }, Modifier.weight(1f), primary = model.theme == label) }
                }
                PressButton("Reshuffle all cards", { resetDialog = true }, Modifier.fillMaxWidth())
                Text("Cards do not repeat until a deck is used up. Reshuffling clears that memory, not your scores.", fontSize = 13.sp, color = p.muted)
            }
        }
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text("Make all cards available?") }, text = { Text("Previously played cards may appear again. Your round history is kept.") },
        confirmButton = { TextButton(onClick = { model.resetSeen(); resetDialog = false }) { Text("Reshuffle") } }, dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancel") } })
}

@Composable private fun SettingToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(detail, color = LocalPress.current.muted, fontSize = 13.sp)
        }
        Switch(checked, onChange, Modifier.semantics { contentDescription = title })
    }
}

@Composable private fun HelpScreen(model: AppModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("A little help up top", model::home)
        Rule(colored = true)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem("01 / GATHER ROUND", "Two or more people. Pick a deck and a 30, 60, 90 or 120-second round.")
                HelpItem("02 / NO PEEKING", "Hold the phone sideways on your forehead, screen out. After the countdown, friends give clues without saying the word, spelling it, or using ‘rhymes with’.")
                HelpItem("03 / SAY IT OUT LOUD", "Guess the card. For Do Your Thing, try acting without speaking for a charades round. Use any house rules your group agrees on.")
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpItem("04 / TILT TO SCORE", "Screen toward the floor = correct. Screen toward the ceiling = pass. Return upright before the next tilt. You can always use the touch buttons instead.")
                HelpItem("05 / PASS THE PHONE", "Each correct answer is one point; no penalty for passing. Review the round and tap any mistaken result to fix it. Play again and hand the phone to the next person.")
                HelpItem("GOOD TO KNOW", "Leaving the app pauses the timer. Resume gives you time to get ready. Last 20 rounds are saved on this phone. No internet, ads, camera, microphone or accounts. Original game and decks; not affiliated with Heads Up!.")
            }
        }
    }
}

@Composable private fun HelpItem(title: String, text: String) {
    Kicker(title, color = LocalPress.current.purple)
    Text(text, color = LocalPress.current.ink, fontSize = 16.sp)
}

@Composable private fun HistoryScreen(model: AppModel) {
    var expanded by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header("Recent rounds", model::home, "LAST 20 · ON THIS PHONE")
        Rule(colored = true)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.history.isEmpty()) item { Text("Your first round is still ahead of you. Pick a deck and play!", color = LocalPress.current.muted, modifier = Modifier.padding(24.dp)) }
            itemsIndexed(model.history) { _, record ->
                Column(Modifier.fillMaxWidth().border(1.dp, LocalPress.current.ink).background(LocalPress.current.card).padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == record.id) null else record.id }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${record.score}", fontFamily = Headline, fontSize = 32.sp, color = LocalPress.current.green)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(record.deck, color = LocalPress.current.ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text("${record.seconds}s · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.id))}", color = LocalPress.current.muted, fontSize = 13.sp)
                        }
                        Kicker(if (expanded == record.id) "HIDE −" else "REVIEW +")
                    }
                    if (expanded == record.id) record.answers.forEach { AnswerRow(it) }
                }
            }
        }
    }
}