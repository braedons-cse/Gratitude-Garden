package com.cse5236.gratitudegarden.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgBgCream
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInk
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep
import com.cse5236.gratitudegarden.ui.theme.pressScale

/** White, moss-bordered text field with a leading icon and optional label. */
@Composable
fun PgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: PgIconName,
    modifier: Modifier = Modifier,
    label: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = PgInkSoft,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(
            modifier = Modifier
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
                Box(
                    modifier = Modifier.clickable(
                        onClickLabel = if (visible) "Hide password" else "Show password",
                    ) { visible = !visible },
                ) {
                    PgIcon(
                        name = PgIconName.Eye,
                        color = PgInkMuted,
                        size = 18.dp,
                        contentDescription = if (visible) "Hide password" else "Show password",
                    )
                }
            }
        }
    }
}

/** Cozy game pill button with a deep-green ledge below the face. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    Box(modifier = modifier.height(56.dp).pressScale(interaction, pressedScale = 0.96f)) {
        if (primary) {
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
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
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
fun RowScope.SocialButton(label: String, mark: String) {
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
            modifier = Modifier.size(22.dp).clip(CircleShape).background(PgBgSage),
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
        Text(text = label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = PgInk)
    }
}
