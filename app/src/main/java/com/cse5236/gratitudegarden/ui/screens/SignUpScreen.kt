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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse5236.gratitudegarden.ui.components.PgTextField
import com.cse5236.gratitudegarden.ui.components.PillButton
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.Plant
import com.cse5236.gratitudegarden.ui.sprites.PlantPalette
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgBgCream
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep

private val ErrorRed = Color(0xFFB3261E)

@Composable
fun SignUpScreen(
    onSignUp: (name: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onBackToLogin: () -> Unit = {},
    loading: Boolean = false,
    error: String? = null,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 28.dp),
    ) {
        // Back chip
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.6f))
                .clickable(onClick = onBackToLogin),
            contentAlignment = Alignment.Center,
        ) {
            PgIcon(name = PgIconName.Back, color = com.cse5236.gratitudegarden.ui.theme.PgInk, size = 18.dp)
        }

        Spacer(Modifier.height(14.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                Plant(colors = PlantPalette.Tulip, stage = 1, size = 42.dp)
            }
            Column {
                Text(
                    text = "Plant a seed",
                    fontFamily = Caprasimo,
                    fontSize = 28.sp,
                    color = PgPrimaryDeep,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "One kind word a day grows a garden.",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = PgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        PgTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Rosa",
            label = "What should we call you?",
            icon = PgIconName.User,
        )
        Spacer(Modifier.height(12.dp))
        PgTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "you@garden.app",
            label = "Email",
            icon = PgIconName.Mail,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        PgTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "At least 8 characters",
            label = "Password",
            icon = PgIconName.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        Spacer(Modifier.height(16.dp))

        // Consent
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (consent) PgPrimary else Color.White)
                    .clickable { consent = !consent },
                contentAlignment = Alignment.Center,
            ) {
                if (consent) PgIcon(name = PgIconName.Check, color = PgBgCream, size = 14.dp)
            }
            Text(
                text = "I'm cool with the terms and privacy policy.",
                fontFamily = Nunito,
                fontSize = 12.5.sp,
                color = PgInkSoft,
                lineHeight = 18.sp,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = error,
                color = ErrorRed,
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        PillButton(
            text = if (loading) "Planting…" else "Start growing",
            onClick = { if (!loading && consent) onSignUp(name, email, password) },
            enabled = !loading && consent,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(text = "Already growing? ", fontFamily = Nunito, fontSize = 14.sp, color = PgInkSoft)
            Text(
                text = "Log in",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = PgPrimary,
                modifier = Modifier.clickable(onClick = onBackToLogin),
            )
        }
    }
}
