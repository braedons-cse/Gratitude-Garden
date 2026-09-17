package com.gratitudegarden.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.ui.components.GgTextField
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.Plant
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgBgCream
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.util.passwordProblem

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

    // Mirrors the Supabase Auth password policy so the rule is enforced where the user can
    // see it. null once the password is acceptable. See util/PasswordRules.kt.
    val passwordProblem = passwordProblem(password)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 28.dp),
    ) {
        // Back chip
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.6f))
                .clickable(onClickLabel = "Back to login", onClick = onBackToLogin),
            contentAlignment = Alignment.Center,
        ) {
            GgIcon(
                name = GgIconName.Back,
                color = com.gratitudegarden.app.ui.theme.GgInk,
                size = 18.dp,
                contentDescription = "Back to login",
            )
        }

        Spacer(Modifier.height(14.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(GgMoss),
                contentAlignment = Alignment.Center,
            ) {
                Plant(colors = PlantPalette.Tulip, stage = 1, size = 42.dp)
            }
            Column {
                Text(
                    text = "Plant a seed",
                    fontFamily = Caprasimo,
                    fontSize = 28.sp,
                    color = GgPrimaryDeep,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "One kind word a day grows a garden.",
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp,
                    color = GgInkSoft,
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        GgTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Rosa",
            label = "What should we call you?",
            icon = GgIconName.User,
        )
        Spacer(Modifier.height(12.dp))
        GgTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "you@garden.app",
            label = "Email",
            icon = GgIconName.Mail,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        GgTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Create a password",
            label = "Password",
            icon = GgIconName.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        // Stated up front rather than only on failure: with four character classes required,
        // a user who learns the rule one rejection at a time gives up. Muted until they have
        // typed something that does not satisfy it.
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (password.isEmpty() || passwordProblem == null) {
                "8+ characters, with an uppercase, lowercase, number and symbol."
            } else {
                "Needs $passwordProblem."
            },
            color = if (password.isEmpty() || passwordProblem == null) GgInkSoft else ErrorRed,
            fontFamily = Nunito,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(start = 4.dp)
                // The text swaps between hint and error as the user types, and the only other
                // signal is the submit button greying out -- which TalkBack reports on focus,
                // long after the change. Polite (not Assertive) so it waits for a pause in
                // typing instead of interrupting every keystroke.
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        Spacer(Modifier.height(16.dp))

        // Consent
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (consent) GgPrimary else Color.White)
                    .semantics { contentDescription = "Accept the terms and privacy policy" }
                    .toggleable(value = consent, role = Role.Checkbox) { consent = it },
                contentAlignment = Alignment.Center,
            ) {
                if (consent) GgIcon(name = GgIconName.Check, color = GgBgCream, size = 14.dp)
            }
            Text(
                text = "I'm cool with the terms and privacy policy.",
                fontFamily = Nunito,
                fontSize = 12.5.sp,
                color = GgInkSoft,
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
            onClick = {
                if (!loading && consent && passwordProblem == null) {
                    onSignUp(name, email, password)
                }
            },
            enabled = !loading && consent && passwordProblem == null,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(text = "Already growing? ", fontFamily = Nunito, fontSize = 14.sp, color = GgInkSoft)
            Text(
                text = "Log in",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GgPrimary,
                modifier = Modifier.clickable(onClick = onBackToLogin),
            )
        }
    }
}
