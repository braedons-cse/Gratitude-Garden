package com.cse5236.gratitudegarden.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse5236.gratitudegarden.ui.components.PillButton
import com.cse5236.gratitudegarden.ui.me.MeViewModel
import com.cse5236.gratitudegarden.ui.sprites.CoinIcon
import com.cse5236.gratitudegarden.ui.sprites.MaturePlant
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.PlantPalette
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgAccent
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInk
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep

@Composable
fun MeScreen(onSignOut: () -> Unit) {
    val vm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PgBgSage)
            .padding(20.dp),
    ) {
        Text(text = "Me", fontFamily = Caprasimo, fontSize = 28.sp, color = PgPrimaryDeep)
        Spacer(Modifier.height(16.dp))

        // Profile row
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                MaturePlant(colors = PlantPalette.Rose, size = 48.dp)
            }
            Column {
                Text(
                    text = ui.name.ifBlank { "Gardener" },
                    fontFamily = Caprasimo,
                    fontSize = 22.sp,
                    color = PgInk,
                )
                Text(
                    text = "Level ${ui.level} gardener",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = PgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(modifier = Modifier.weight(1f), value = "${ui.streak}", label = "Day streak") {
                PgIcon(name = PgIconName.Flame, color = PgAccent, size = 22.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.totalEntries}", label = "Thoughts") {
                PgIcon(name = PgIconName.Mic, color = PgPrimaryDeep, size = 20.dp)
            }
            StatCard(modifier = Modifier.weight(1f), value = "${ui.coins}", label = "Coins") {
                CoinIcon(size = 22.dp)
            }
        }

        Spacer(Modifier.weight(1f))

        PillButton(
            text = "Log out",
            onClick = onSignOut,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(text = value, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = PgInk)
        Text(text = label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = PgInkMuted)
    }
}
