package com.cse5236.gratitudegarden.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgInkMuted
import com.cse5236.gratitudegarden.ui.theme.PgPrimary

data class NavItem(val route: String, val icon: PgIconName, val label: String)

val HomeNavItems = listOf(
    NavItem("garden", PgIconName.Home, "Garden"),
    NavItem("shop", PgIconName.Shop, "Shop"),
    NavItem("journal", PgIconName.Leaf, "Journal"),
    NavItem("me", PgIconName.Cog, "Me"),
)

@Composable
fun BottomNav(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeNavItems.forEach { item ->
            val selected = current == item.route
            val tint = if (selected) PgPrimary else PgInkMuted
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                ) {
                    if (current != item.route) {
                        nav.navigate(item.route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            ) {
                PgIcon(name = item.icon, color = tint, size = 22.dp)
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
