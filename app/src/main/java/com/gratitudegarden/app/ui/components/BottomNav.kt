package com.gratitudegarden.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgPrimary

data class NavItem(val route: String, val icon: GgIconName, val label: String)

val HomeNavItems = listOf(
    NavItem("garden", GgIconName.Home, "Garden"),
    NavItem("shop", GgIconName.Shop, "Shop"),
    NavItem("journal", GgIconName.Leaf, "Journal"),
    NavItem("me", GgIconName.Cog, "Me"),
)

/**
 * Custom bottom bar driven by the home pager. [selectedIndex] follows the pager's
 * current page and [onSelect] scrolls it, so tap and swipe stay in lock-step.
 * Styling is intentionally the original plain icon+label look (selected tab tints
 * primary green) — the motion lives in the pager, not the bar.
 */
@Composable
fun BottomNav(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val tint = if (isSelected) GgPrimary else GgInkMuted
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = item.label,
                    ) { onSelect(index) },
            ) {
                GgIcon(name = item.icon, color = tint, size = 22.dp)
                Text(
                    text = item.label,
                    fontFamily = Nunito,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = tint,
                )
            }
        }
    }
}
