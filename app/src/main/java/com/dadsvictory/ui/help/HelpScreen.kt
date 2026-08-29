package com.dadsvictory.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.dadsvictory.domain.content.Support
import com.dadsvictory.domain.content.SupportContact
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SafetyBanner
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.dialNumber
import com.dadsvictory.ui.openUrl
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * "Need help?"
 *
 * Country-specific, because a wrong emergency number is worse than none. The four
 * routes are the ones the brief asked for, in escalating order, and the most
 * urgent one is at the top where it can be found without reading.
 */
@Composable
fun HelpScreen(
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val country = state.profile.country
    val emergency = Support.emergencyFor(country)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Need help?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Showing help for ${country.flag} ${country.label}. You can change this in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SafetyBanner(
                title = Support.HelpRoute.DANGER.title,
                body = Support.EMERGENCY_GUIDANCE,
                action = {
                    if (emergency.phone != null) {
                        BigButton(
                            text = "Call ${emergency.phone}",
                            onClick = { dialNumber(context, emergency.phone!!) },
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Text(
                            emergency.detail,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
            )
        }

        item {
            SafetyBanner(
                title = Support.HelpRoute.WITHDRAWAL.title,
                body = Support.HelpRoute.WITHDRAWAL.blurb + "\n\n" +
                    Support.ALCOHOL_WITHDRAWAL_WARNING + "\n\n" + Support.EMERGENCY_GUIDANCE,
            )
        }

        item { SectionHeader(Support.HelpRoute.TALK.title) }
        item { InfoNote(Support.HelpRoute.TALK.blurb) }
        items(Support.ofKind(country, SupportContact.Kind.TALK), key = { it.id }) { contact ->
            ContactCard(contact, onDial = { dialNumber(context, it) }, onOpen = { openUrl(context, it) })
        }

        item { SectionHeader(Support.HelpRoute.MEDICAL.title) }
        item { InfoNote(Support.HelpRoute.MEDICAL.blurb) }
        items(Support.ofKind(country, SupportContact.Kind.URGENT), key = { it.id }) { contact ->
            ContactCard(contact, onDial = { dialNumber(context, it) }, onOpen = { openUrl(context, it) })
        }

        if (state.profile.quitNicotine) {
            item { SectionHeader("Help with stopping nicotine") }
            items(Support.ofKind(country, SupportContact.Kind.QUIT_NICOTINE), key = { it.id }) { contact ->
                ContactCard(contact, onDial = { dialNumber(context, it) }, onOpen = { openUrl(context, it) })
            }
        }

        if (state.profile.quitAlcohol) {
            item { SectionHeader("Help with alcohol") }
            items(Support.ofKind(country, SupportContact.Kind.ALCOHOL), key = { it.id }) { contact ->
                ContactCard(contact, onDial = { dialNumber(context, it) }, onOpen = { openUrl(context, it) })
            }
        }

        val info = Support.ofKind(country, SupportContact.Kind.INFO)
        if (info.isNotEmpty()) {
            item { SectionHeader("More information") }
            items(info, key = { it.id }) { contact ->
                ContactCard(contact, onDial = { dialNumber(context, it) }, onOpen = { openUrl(context, it) })
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            InfoNote(Support.NOT_A_DOCTOR)
            InfoNote(
                "Opening a website hands the link to your browser, and tapping a number opens your " +
                    "dialler with it filled in — this app never places a call itself, and it has no " +
                    "internet access of its own.",
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun ContactCard(
    contact: SupportContact,
    onDial: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    VictoryCard {
        Column {
            Text(contact.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                contact.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val phone = contact.phone
            val url = contact.url
            if (phone != null || url != null) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (phone != null) {
                        SecondaryButton(
                            text = "Call",
                            onClick = { onDial(phone) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (url != null) {
                        SecondaryButton(
                            text = "Open website",
                            onClick = { onOpen(url) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
