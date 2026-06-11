package com.cse5236.gratitudegarden.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse5236.gratitudegarden.ui.sprites.PgIcon
import com.cse5236.gratitudegarden.ui.sprites.PgIconName
import com.cse5236.gratitudegarden.ui.theme.Caprasimo
import com.cse5236.gratitudegarden.ui.theme.Nunito
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.ui.theme.PgInkSoft
import com.cse5236.gratitudegarden.ui.theme.PgMoss
import com.cse5236.gratitudegarden.ui.theme.PgPrimary
import com.cse5236.gratitudegarden.ui.theme.PgPrimaryDeep

/** Placeholder — the shop (seeds/decor/backdrops) hangs off the purchase RPCs next. */
@Composable
fun ShopScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(PgBgSage).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(PgMoss),
                contentAlignment = Alignment.Center,
            ) {
                PgIcon(name = PgIconName.Shop, color = PgPrimaryDeep, size = 34.dp)
            }
            Text(text = "Garden Shop", fontFamily = Caprasimo, fontSize = 26.sp, color = PgPrimaryDeep)
            Text(
                text = "Seeds, decor, and backdrops are coming soon.\nEarn coins by planting kind thoughts.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = PgInkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }
    }
}
