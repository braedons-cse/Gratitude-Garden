package com.gratitudegarden.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.ui.components.GgTextField
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.components.SocialButton
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.PottedPlant
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.GratitudeGardenTheme
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep

private val ErrorRed = Color(0xFFB3261E)

@Composable
fun LoginScreen(
    onLogIn: (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onSignUp: () -> Unit = {},
    loading: Boolean = false,
    error: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PottedPlant(size = 130.dp)
        }

        Text(
            text = "Welcome back",
            fontFamily = Caprasimo,
            fontSize = 32.sp,
            color = GgPrimaryDeep,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your garden's been waiting for you.",
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            color = GgInkSoft,
        )

        Spacer(Modifier.height(28.dp))

        GgTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            icon = GgIconName.Mail,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        GgTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            icon = GgIconName.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "Forgot password?",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = GgPrimary,
                modifier = Modifier.clickable(onClick = onForgotPassword),
            )
        }

        if (error != null) {
            Text(
                text = error,
                color = ErrorRed,
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            )
        }

        PillButton(
            text = if (loading) "Logging in…" else "Log in",
            onClick = { if (!loading) onLogIn(email, password) },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(GgMoss))
            Text(
                text = "OR",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = GgInkMuted,
            )
            Box(Modifier.weight(1f).height(1.dp).background(GgMoss))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialButton(label = "Google", mark = "G")
            SocialButton(label = "Apple", mark = "")
        }

        Spacer(Modifier.height(28.dp))

        Row(modifier = Modifier.padding(top = 4.dp)) {
            Text(
                text = "New to the garden? ",
                fontFamily = Nunito,
                fontSize = 14.sp,
                color = GgInkSoft,
            )
            Text(
                text = "Plant your first seed",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GgPrimary,
                modifier = Modifier.clickable(onClick = onSignUp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun LoginScreenPreview() {
    GratitudeGardenTheme { LoginScreen() }
}
