package com.gratitudegarden.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgBgCream
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep
import com.gratitudegarden.app.ui.theme.pressScale

/**
 * Tags the node only when a tag was supplied, so callers that don't test a
 * control don't put a stray tag in the semantics tree.
 */
private fun Modifier.optionalTestTag(tag: String?): Modifier =
    if (tag == null) this else this.testTag(tag)

/** White, moss-bordered text field with a leading icon and optional label. */
@Composable
fun GgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: GgIconName,
    modifier: Modifier = Modifier,
    label: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    // Lands on the inner BasicTextField, so UI tests can type into it directly.
    testTag: String? = null,
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
                color = GgInkSoft,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(Color.White)
                .border(1.5.dp, GgMoss, shape)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GgIcon(name = icon, color = GgPrimary, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontFamily = Nunito,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = GgInkMuted,
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
                        color = GgInk,
                    ),
                    visualTransformation = if (isPassword && !visible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    cursorBrush = SolidColor(GgPrimary),
                    modifier = Modifier.fillMaxWidth().optionalTestTag(testTag),
                )
            }
            if (isPassword) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.clickable(
                        onClickLabel = if (visible) "Hide password" else "Show password",
                    ) { visible = !visible },
                ) {
                    GgIcon(
                        name = GgIconName.Eye,
                        color = GgInkMuted,
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
    // Lands on the clickable face, which is where the click and enabled
    // semantics live -- the outer Box is only the ledge/press-scale wrapper.
    testTag: String? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    // Dimmed when disabled: without it a disabled button looks exactly like a live one.
    Box(
        modifier = modifier
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .pressScale(interaction, pressedScale = 0.96f),
    ) {
        if (primary) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .align(Alignment.BottomCenter)
                    .clip(shape)
                    .background(GgPrimaryDeep),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.TopCenter)
                .clip(shape)
                .background(if (primary) GgPrimary else Color.White)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .optionalTestTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (primary) GgBgCream else GgInk,
            )
        }
    }
}
