package com.cse5236.gratitudegarden.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cse5236.gratitudegarden.ui.components.PgTextField
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
fun MeScreen(onSignOut: () -> Unit, onOpenAdmin: () -> Unit = {}) {
    val vm: MeViewModel = viewModel(factory = MeViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()

    // 0 = hidden, 1 = "are you sure" warning, 2 = password confirmation.
    var deleteStep by remember { mutableStateOf(0) }
    var password by remember { mutableStateOf("") }

    fun cancelDelete() {
        deleteStep = 0
        password = ""
        vm.clearDeleteError()
    }

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

        // Admin-only entry point. Hidden entirely for normal users.
        if (ui.isAdmin) {
            PillButton(
                text = "Admin Dashboard",
                onClick = onOpenAdmin,
                primary = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        PillButton(
            text = "Log out",
            onClick = onSignOut,
            primary = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // Destructive, deliberately understated — self-service account deletion.
        Text(
            text = "Delete account",
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = PgAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .clickable { deleteStep = 1 }
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
    }

    // Step 1 — confirm intent.
    if (deleteStep == 1) {
        AlertDialog(
            onDismissRequest = { cancelDelete() },
            title = { Text("Delete your account?", fontFamily = Caprasimo, color = PgInk) },
            text = {
                Text(
                    "This permanently deletes your account and erases everything — your garden, " +
                        "journal entries, coins, and stats. This can't be undone.",
                    fontFamily = Nunito,
                    color = PgInkSoft,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearDeleteError(); deleteStep = 2 }) {
                    Text("Continue", color = PgAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }) {
                    Text("Cancel", color = PgInkSoft, fontFamily = Nunito)
                }
            },
        )
    }

    // Step 2 — re-authenticate with the password, then delete for real.
    if (deleteStep == 2) {
        AlertDialog(
            onDismissRequest = { if (!ui.deleting) cancelDelete() },
            title = { Text("Confirm deletion", fontFamily = Caprasimo, color = PgInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter your password to permanently delete this account. There's no going back.",
                        fontFamily = Nunito,
                        color = PgInkSoft,
                    )
                    PgTextField(
                        value = password,
                        onValueChange = { password = it; vm.clearDeleteError() },
                        placeholder = "Password",
                        icon = PgIconName.Lock,
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    )
                    ui.deleteError?.let {
                        Text(
                            text = it,
                            fontFamily = Nunito,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = PgAccent,
                        )
                    }
                }
            },
            confirmButton = {
                val canDelete = !ui.deleting && password.isNotBlank()
                TextButton(onClick = { vm.deleteAccount(password) }, enabled = canDelete) {
                    Text(
                        text = if (ui.deleting) "Deleting…" else "Delete forever",
                        color = if (canDelete) PgAccent else PgInkMuted,
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelDelete() }, enabled = !ui.deleting) {
                    Text("Cancel", color = PgInkSoft, fontFamily = Nunito)
                }
            },
        )
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
