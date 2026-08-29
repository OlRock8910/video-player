package com.dadsvictory.ui.craving

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.dadsvictory.data.local.PhotoStore
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.CravingOutcome
import com.dadsvictory.domain.content.CravingPlan
import com.dadsvictory.domain.content.Scripture
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.theme.LocalReducedMotion
import com.dadsvictory.ui.theme.ScreenPadding
import com.dadsvictory.ui.theme.StreakNumberStyle

private enum class Phase { INTRO, BREATHE, WATER, MOVE, REASON, SCRIPTURE, DECIDE, WON, NEED_HELP }

private val STEP_ORDER = listOf(
    Phase.BREATHE, Phase.WATER, Phase.MOVE, Phase.REASON, Phase.SCRIPTURE, Phase.DECIDE,
)

/**
 * Craving emergency mode.
 *
 * The design principle: while a craving is peaking, nobody wants to read or make
 * decisions. So the screen asks for one thing at a time, the timer runs whether or
 * not he does the steps, and the only real decision — "did you get through it?" —
 * comes at the end, once the urge has usually already dropped.
 */
@Composable
fun CravingScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    var phase by rememberSaveable { mutableStateOf(Phase.INTRO) }
    var secondsRemaining by rememberSaveable { mutableIntStateOf(CravingPlan.TIMER_SECONDS) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    // Survives a rotation, so a screen rebuild while on the victory page cannot
    // count the same craving twice.
    var alreadyRecorded by rememberSaveable { mutableStateOf(false) }
    val seed = remember { state.rotationIndex + (state.cravingsDefeated * 7L) }

    // One tick a second while the timer is on screen. It keeps running as he moves
    // between steps, so the ten minutes are real time, not time spent reading.
    LaunchedEffect(timerRunning) {
        while (timerRunning && secondsRemaining > 0) {
            kotlinx.coroutines.delay(1_000)
            secondsRemaining -= 1
        }
        if (timerRunning && secondsRemaining <= 0) {
            timerRunning = false
            phase = Phase.WON
        }
    }

    // Reaching the end of the ten minutes counts as a craving defeated.
    LaunchedEffect(phase) {
        if (phase == Phase.WON && !alreadyRecorded) {
            alreadyRecorded = true
            viewModel.recordCravingWon(CravingPlan.TIMER_SECONDS - secondsRemaining, null)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = 16.dp),
    ) {
        when (phase) {
            Phase.INTRO -> IntroPhase(
                onStart = {
                    timerRunning = true
                    phase = Phase.BREATHE
                },
                onLeave = { navController.popBackStack() },
            )

            Phase.WON -> WonPhase(
                held = CravingPlan.TIMER_SECONDS - secondsRemaining,
                defeatedTotal = state.cravingsDefeated,
                onDone = { navController.popBackStack() },
            )

            Phase.NEED_HELP -> NeedHelpPhase(
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onKeepGoing = {
                    // A second round is a second craving to get through, so it is
                    // eligible to be counted in its own right.
                    secondsRemaining = CravingPlan.TIMER_SECONDS
                    alreadyRecorded = false
                    timerRunning = true
                    phase = Phase.BREATHE
                },
                onDone = { navController.popBackStack() },
            )

            else -> {
                CountdownHeader(secondsRemaining, seed)
                Spacer(Modifier.height(20.dp))

                val stepIndex = STEP_ORDER.indexOf(phase).coerceAtLeast(0)
                Text(
                    "Step ${stepIndex + 1} of ${STEP_ORDER.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                when (phase) {
                    Phase.BREATHE -> BreathePhase()
                    Phase.WATER -> WaterPhase()
                    Phase.MOVE -> MovePhase()
                    Phase.REASON -> ReasonPhase(state, viewModel)
                    Phase.SCRIPTURE -> ScripturePhase(state, seed)
                    Phase.DECIDE -> DecidePhase()
                    else -> Unit
                }

                Spacer(Modifier.height(24.dp))

                if (phase == Phase.DECIDE) {
                    BigButton(
                        text = "I BEAT THE CRAVING",
                        onClick = {
                            timerRunning = false
                            phase = Phase.WON
                        },
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "I STILL NEED HELP",
                        onClick = {
                            timerRunning = false
                            viewModel.recordCravingOutcome(
                                CravingOutcome.NEEDED_HELP,
                                CravingPlan.TIMER_SECONDS - secondsRemaining,
                                null,
                            )
                            phase = Phase.NEED_HELP
                        },
                    )
                } else {
                    BigButton(
                        text = "Next",
                        onClick = {
                            val next = STEP_ORDER.getOrNull(stepIndex + 1) ?: Phase.DECIDE
                            phase = next
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { phase = Phase.DECIDE },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip to the end") }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun IntroPhase(onStart: () -> Unit, onLeave: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(
        CravingPlan.OPENING,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        "Give it ten minutes. Cravings rise and fall on their own, and this one will too — " +
            "whether or not you do anything about it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    BigButton(text = "START THE 10 MINUTES", onClick = onStart)
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
        Text("Not right now")
    }
}

@Composable
private fun CountdownHeader(secondsRemaining: Int, seed: Long) {
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val text = "%d:%02d".format(minutes, seconds)
    val message = CravingPlan.messageForSecondsRemaining(secondsRemaining, seed)

    VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text,
                style = StreakNumberStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "$minutes minutes $seconds seconds remaining"
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { 1f - (secondsRemaining.toFloat() / CravingPlan.TIMER_SECONDS) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BreathePhase() {
    Text("Take 10 slow breaths.", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        CravingPlan.BREATHING_INSTRUCTION,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    BreathingCircle()
}

/**
 * A 12-second cycle: four seconds in, two holding, six out. The long out-breath is
 * the part that actually settles the nervous system, which is why it is the
 * longest phase rather than an even in-and-out.
 *
 * With "reduce motion" on, the circle stays still and the timing is given in words
 * instead, so the step still works for anyone who finds movement unpleasant.
 */
@Composable
private fun BreathingCircle() {
    val reducedMotion = LocalReducedMotion.current
    val colour = MaterialTheme.colorScheme.primary

    if (reducedMotion) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(180.dp)) {
                drawCircle(color = colour, radius = size.minDimension / 2.6f)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Breathe in for ${CravingPlan.BREATH_IN_SECONDS} seconds, " +
                    "hold for ${CravingPlan.BREATH_HOLD_SECONDS}, " +
                    "and out for ${CravingPlan.BREATH_OUT_SECONDS}. " +
                    "Repeat ${CravingPlan.BREATH_COUNT} times.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val cycleMillis = (CravingPlan.BREATH_IN_SECONDS + CravingPlan.BREATH_HOLD_SECONDS +
        CravingPlan.BREATH_OUT_SECONDS) * 1000
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleMillis
                0.55f at 0 using LinearEasing
                1f at CravingPlan.BREATH_IN_SECONDS * 1000 using LinearEasing
                1f at (CravingPlan.BREATH_IN_SECONDS + CravingPlan.BREATH_HOLD_SECONDS) * 1000 using LinearEasing
                0.55f at cycleMillis using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "breathScale",
    )

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(220.dp)
                .semantics { contentDescription = "Breathing guide circle" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(220.dp).scale(scale)) {
                drawCircle(color = colour, radius = size.minDimension / 2f)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "In for ${CravingPlan.BREATH_IN_SECONDS} · hold ${CravingPlan.BREATH_HOLD_SECONDS} · " +
                "out for ${CravingPlan.BREATH_OUT_SECONDS}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaterPhase() {
    Text("Drink a glass of water.", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text("💧", style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(12.dp))
    Text(CravingPlan.WATER_STEP, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun MovePhase() {
    Text("Move.", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Pick one. It does not matter which — the point is that your body does something different.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    for (option in CravingPlan.MOVE_OPTIONS) {
        VictoryCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(option.emoji)
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(option.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        option.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * The step that does the most work: his own words and his own photo, put in front
 * of him at the moment he is least able to recall them unprompted.
 */
@Composable
private fun ReasonPhase(state: VictoryUiState, viewModel: VictoryViewModel) {
    val context = LocalContext.current
    val reasons = state.profile.reasonLines()
    val messages by viewModel.familyMessages.collectAsState(initial = emptyList())
    var photo by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.settings.hasFamilyPhoto) {
        photo = if (state.settings.hasFamilyPhoto) PhotoStore.load(context) else null
    }

    Text("Remember your reason.", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(20.dp))

    photo?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Your photo — the people you're doing this for",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 280.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(16.dp))
    }

    // His own messages come first — they carry more weight than anything preset.
    for (message in messages) {
        VictoryCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                message.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    if (reasons.isEmpty() && messages.isEmpty()) {
        VictoryCard {
            Text(
                "You haven't written your reasons down yet. When this is over, add them in " +
                    "Family & reasons — they help most at exactly this moment.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        for (reason in reasons) {
            VictoryCard {
                Text(reason, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ScripturePhase(state: VictoryUiState, seed: Long) {
    val verse = Scripture.forCraving(seed)
    Text("Something to hold on to.", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(20.dp))
    VictoryCard {
        Column {
            Text(
                "\"${verse.text(state.settings.bibleVersion)}\"",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "— ${verse.reference} (${state.settings.bibleVersion.abbreviation})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecidePhase() {
    Text("How are you doing?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "There is no wrong answer here, and nothing bad happens either way.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WonPhase(held: Int, defeatedTotal: Int, onDone: () -> Unit) {
    Spacer(Modifier.height(32.dp))
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            CravingPlan.VICTORY,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            CravingPlan.VICTORY_SUB,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        VictoryCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Cravings defeated", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    (defeatedTotal + 1).toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "You held on for ${held / 60} minutes ${held % 60} seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        BigButton(text = "Back to the dashboard", onClick = onDone)
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun NeedHelpPhase(
    onOpenHelp: () -> Unit,
    onKeepGoing: () -> Unit,
    onDone: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Text("That's alright.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(
        "Asking for help is a tactic, not a weakness. Here is what is available right now.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))

    BigButton(text = "Show me who I can contact", onClick = onOpenHelp)
    Spacer(Modifier.height(10.dp))
    SecondaryButton(text = "Give me another 10 minutes", onClick = onKeepGoing)
    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text("Back to the dashboard")
    }

    Spacer(Modifier.height(20.dp))
    InfoNote(
        "Whatever happens next, this is still your journey and nothing you have built gets " +
            "taken away.",
    )
    Spacer(Modifier.height(24.dp))
}
