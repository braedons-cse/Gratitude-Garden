package com.cse5236.gratitudegarden.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.sprites.PottedPlant
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.GratitudeGardenTheme
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgBgCream
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInk
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep

/**
 * Onboarding · Login. Warm, plant-led entry matching the Positivity Garden
 * design. Fields are real and editable; the action callbacks are wired by the
 * caller (navigation lands here on launch for now).
 */
@Composable
fun LoginScreen(
    onLogIn: (email: String, password: String) -> Unit = { _, _ -> },
    onForgotPassword: () -> Unit = {},
    onSignUp: () -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PgBgSage)
            .systemBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Hero ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            PottedPlant(size = 130.dp)
        }

        Text(
            text = "Welcome back",
            fontFamily = Caprasimo,
            fontSize = 32.sp,
            color = PgPrimaryDeep,
            letterSpacing = (-0.5).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your garden's been waiting for you.",
            fontFamily = Nunito,
            fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            color = PgInkSoft,
        )

        Spacer(Modifier.height(28.dp))

        // ── Fields ────────────────────────────────────────────────
        PgTextField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            icon = PgIconName.Mail,
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        PgTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            icon = PgIconName.Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
        )

        // ── Forgot password ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = "Forgot password?",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PgPrimary,
                modifier = Modifier.clickable(onClick = onForgotPassword),
            )
        }

        PillButton(
            text = "Log in",
            onClick = { onLogIn(email, password) },
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Divider ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(PgMoss))
            Text(
                text = "OR",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                color = PgInkMuted,
            )
            Box(Modifier.weight(1f).height(1.dp).background(PgMoss))
        }

        // ── Social ────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SocialButton(label = "Google", mark = "G")
            SocialButton(label = "Apple", mark = "")
        }

        Spacer(Modifier.weight(1f))

        // ── Footer ────────────────────────────────────────────────
        Row(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = "New to the garden? ",
                fontFamily = Nunito,
                fontSize = 14.sp,
                color = PgInkSoft,
            )
            Text(
                text = "Plant your first seed",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = PgPrimary,
                modifier = Modifier.clickable(onClick = onSignUp),
            )
        }
    }
}

// ── Components ────────────────────────────────────────────────────

@Composable
private fun PgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: PgIconName,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, PgMoss, shape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PgIcon(name = icon, color = PgPrimary, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = PgInkMuted,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = PgInk,
                ),
                visualTransformation = if (isPassword && !visible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                cursorBrush = SolidColor(PgPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isPassword) {
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.clickable { visible = !visible }) {
                PgIcon(name = PgIconName.Eye, color = PgInkMuted, size = 18.dp)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(modifier = modifier.height(56.dp)) {
        if (primary) {
            // Deep-green ledge peeking out below the face — the cozy game look.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomCenter)
                    .clip(shape)
                    .background(PgPrimaryDeep),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.TopCenter)
                .clip(shape)
                .background(if (primary) PgPrimary else Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (primary) PgBgCream else PgInk,
            )
        }
    }
}

@Composable
private fun RowScope.SocialButton(label: String, mark: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.5.dp, PgMoss, shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(PgBgSage),
            contentAlignment = Alignment.Center,
        ) {
            if (mark.isNotEmpty()) {
                Text(
                    text = mark,
                    fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = PgPrimaryDeep,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            color = PgInk,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable
private fun LoginScreenPreview() {
    GratitudeGardenTheme {
        LoginScreen()
    }
}
