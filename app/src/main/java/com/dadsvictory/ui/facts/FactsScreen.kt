package com.dadsvictory.ui.facts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.content.Fact
import com.dadsvictory.domain.content.Facts
import com.dadsvictory.domain.content.Sources
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SafetyBanner
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SourceLine
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.openUrl
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * "Why Quit?"
 *
 * Every card carries a source, and where a card states a number it also states the
 * period and the population that number describes. Anything the evidence does not
 * settle is written as unsettled.
 */
@Composable
fun FactsScreen(
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val facts = Facts.relevantTo(state.profile.quitNicotine, state.profile.quitAlcohol)
    val grouped = facts.groupBy { it.topic }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Why quit?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Facts, not scare stories. Everything here is attributed, and where scientists are " +
                    "still working something out, it says so.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { InfoNote(Sources.DISCLAIMER) }

        for (topic in Fact.Topic.entries) {
            val topicFacts = grouped[topic].orEmpty()
            if (topicFacts.isEmpty()) continue

            item { SectionHeader("${topic.emoji} ${topic.label}") }
            items(topicFacts, key = { it.id }) { fact ->
                FactCard(fact, onOpenSource = { openUrl(context, it) })
            }
        }

        item {
            SafetyBanner(
                title = "One thing that is not optional",
                body = com.dadsvictory.domain.content.Support.ALCOHOL_WITHDRAWAL_WARNING,
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Sources & health information",
                onClick = { navController.navigate(Routes.SOURCES) },
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun FactCard(fact: Fact, onOpenSource: (String) -> Unit) {
    VictoryCard {
        Column {
            Text(fact.headline, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text(fact.body, style = MaterialTheme.typography.bodyMedium)

            if (fact.period != null || fact.population != null) {
                Spacer(Modifier.height(10.dp))
                val labels = listOfNotNull(fact.period, fact.population).joinToString(" · ")
                Text(
                    labels,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val source = fact.source
            if (source != null) {
                SourceLine(
                    organisation = source.organisation,
                    detail = source.title,
                    onOpen = { onOpenSource(source.url) },
                )
            }
        }
    }
}

/** Every source in the app, in one list, with the licensing notes. */
@Composable
fun SourcesScreen(navController: NavHostController) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Sources & health information",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            SafetyBanner(title = "Please read", body = Sources.DISCLAIMER)
        }

        item { SectionHeader("Where the health information comes from") }

        items(Sources.ALL, key = { it.id }) { source ->
            VictoryCard(onClick = { openUrl(context, source.url) }) {
                Column {
                    Text(source.organisation, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(source.title, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        source.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        source.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item { SectionHeader("Bible translations") }

        item {
            VictoryCard {
                Text(Sources.SCRIPTURE_LICENSING, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item { SectionHeader("How the estimates are worked out") }

        item {
            VictoryCard {
                Column {
                    Text(
                        "Money saved is worked out from the weekly spend you entered during setup, " +
                            "counted proportionally to the time you have been free, with roughly one " +
                            "day's spend deducted for each slip you record. It is an estimate of your " +
                            "own figures, not a measurement.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "'Vapes avoided' and 'drinks avoided' come from the frequency you entered. " +
                            "This app deliberately does not estimate how much nicotine you took in, " +
                            "because it cannot know that.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Streaks count completed 24-hour periods since you started, or since your " +
                            "most recent slip. Measuring elapsed time rather than calendar dates means " +
                            "the count cannot jump about when the clocks change or you travel.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}
