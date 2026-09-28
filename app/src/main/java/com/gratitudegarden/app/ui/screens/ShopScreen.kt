package com.gratitudegarden.app.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gratitudegarden.app.data.Item
import com.gratitudegarden.app.data.MAX_STREAK_FREEZES
import com.gratitudegarden.app.data.STREAK_FREEZE_PRICE
import com.gratitudegarden.app.ui.shop.ShopUiState
import com.gratitudegarden.app.ui.shop.ShopViewModel
import com.gratitudegarden.app.ui.sprites.BackdropScene
import com.gratitudegarden.app.ui.sprites.CoinIcon
import com.gratitudegarden.app.ui.sprites.GgIcon
import com.gratitudegarden.app.ui.sprites.GgIconName
import com.gratitudegarden.app.ui.sprites.Plant
import com.gratitudegarden.app.ui.sprites.PlantPalette
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.GgBgCream
import com.gratitudegarden.app.ui.theme.GgBgSage
import com.gratitudegarden.app.ui.theme.GgFrost
import com.gratitudegarden.app.ui.theme.GgFrostLight
import com.gratitudegarden.app.ui.theme.GgInk
import com.gratitudegarden.app.ui.theme.GgInkMuted
import com.gratitudegarden.app.ui.theme.GgInkSoft
import com.gratitudegarden.app.ui.theme.GgMoss
import com.gratitudegarden.app.ui.theme.GgPrimary
import com.gratitudegarden.app.ui.theme.GgPrimaryDeep

private enum class Tab(val label: String) { Seeds("Seeds"), Decor("Decor"), Backdrops("Backdrops") }

@Composable
fun ShopRoute(onRequestPlant: (String) -> Unit = {}) {
    val vm: ShopViewModel = viewModel(factory = ShopViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()
    ShopScreen(
        ui = ui,
        onBuy = vm::buy,
        onBuyFreeze = vm::buyFreeze,
        onEquip = vm::equip,
        // "Plant" now sends you to the Garden to choose the spot.
        onPlant = { item -> onRequestPlant(item.id) },
        onMessageShown = vm::consumeMessage,
        onRefresh = vm::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    ui: ShopUiState,
    onBuy: (Item) -> Unit,
    onPlant: (Item) -> Unit,
    onMessageShown: () -> Unit,
    onRefresh: () -> Unit = {},
    onBuyFreeze: () -> Unit = {},
    onEquip: (Item) -> Unit = {},
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Seeds) }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onMessageShown()
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(GgBgSage),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Garden Shop", fontFamily = Caprasimo, fontSize = 26.sp, color = GgPrimaryDeep)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoinIcon(size = 16.dp)
                Spacer(Modifier.size(5.dp))
                Text("${ui.coins}", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = GgInk)
            }
        }

        Spacer(Modifier.height(14.dp))
        FreezeOffer(ui = ui, onBuy = onBuyFreeze)

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab.entries.forEach { t ->
                TabPill(label = t.label, selected = tab == t, onClick = { tab = t })
            }
        }
        if (!ui.online) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Offline. Buying and planting need a connection.",
                fontFamily = Nunito,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = GgInkSoft,
                modifier = Modifier.testTag("shop_offline_note"),
            )
        }
        Spacer(Modifier.height(16.dp))

        when (tab) {
            Tab.Seeds -> SeedGrid(ui = ui, onBuy = onBuy, onPlant = onPlant)
            Tab.Backdrops -> BackdropList(ui = ui, onBuy = onBuy, onEquip = onEquip)
            else -> ComingSoon(tab.label)
        }

        Spacer(Modifier.height(24.dp))
    }
    }
}

/** Streak freezes sit above the tabs: they're for the streak, not the garden. */
@Composable
private fun FreezeOffer(ui: ShopUiState, onBuy: () -> Unit) {
    val full = ui.freezes >= MAX_STREAK_FREEZES
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(12.dp)
            .testTag("shop_freeze_offer"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(GgFrostLight),
            contentAlignment = Alignment.Center,
        ) {
            GgIcon(name = GgIconName.Snowflake, color = GgFrost, size = 28.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Streak freeze", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GgInk)
            Text(
                if (full) "You have $MAX_STREAK_FREEZES/$MAX_STREAK_FREEZES — fully stocked."
                else "Forgives a missed day. You have ${ui.freezes}/$MAX_STREAK_FREEZES.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = GgInkSoft,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.width(84.dp)) {
            ShopButton(
                text = if (ui.buyingFreeze) "…" else "🪙 $STREAK_FREEZE_PRICE",
                filled = true,
                enabled = ui.online && !full && !ui.buyingFreeze,
                onClick = onBuy,
            )
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = if (selected) GgBgCream else GgInkSoft,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) GgPrimary else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SeedGrid(ui: ShopUiState, onBuy: (Item) -> Unit, onPlant: (Item) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ui.seeds.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        SeedCard(
                            item = item,
                            owned = item.id in ui.owned,
                            level = ui.level,
                            busy = ui.busyItemId == item.id,
                            online = ui.online,
                            onBuy = { onBuy(item) },
                            onPlant = { onPlant(item) },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeedCard(
    item: Item,
    owned: Boolean,
    level: Int,
    busy: Boolean,
    online: Boolean,
    onBuy: () -> Unit,
    onPlant: () -> Unit,
) {
    val locked = !owned && level < item.levelRequired
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(GgMoss),
            contentAlignment = Alignment.Center,
        ) {
            Plant(
                colors = PlantPalette.forSlug(item.slug),
                stage = 3,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        Text(item.name, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GgInk)
        Text(
            item.rarity.replaceFirstChar { it.uppercase() },
            fontFamily = Nunito,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = rarityColor(item.rarity),
        )

        when {
            owned -> ShopButton(text = if (busy) "…" else "Plant", filled = false, enabled = online && !busy, onClick = onPlant)
            locked -> ShopButton(text = "Level ${item.levelRequired}", filled = false, enabled = false, onClick = {})
            // Offline the price still shows, so the shop reads the same; it just can't be tapped.
            else -> ShopButton(text = if (busy) "…" else "🪙 ${item.priceCoins}", filled = true, enabled = online && !busy, onClick = onBuy)
        }
    }
}

/** One per row: a backdrop is a wide scene, and the preview is the point of the card. */
@Composable
private fun BackdropList(ui: ShopUiState, onBuy: (Item) -> Unit, onEquip: (Item) -> Unit) {
    // The starter isn't for sale, but it's owned and can be put back.
    val shown = ui.backdrops.filter { it.isPurchasable || it.id in ui.owned }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        shown.forEach { item ->
            BackdropCard(
                item = item,
                owned = item.id in ui.owned,
                inUse = item.id == ui.activeBackdropId,
                level = ui.level,
                busy = ui.busyItemId == item.id,
                online = ui.online,
                onBuy = { onBuy(item) },
                onEquip = { onEquip(item) },
            )
        }
    }
}

@Composable
private fun BackdropCard(
    item: Item,
    owned: Boolean,
    inUse: Boolean,
    level: Int,
    busy: Boolean,
    online: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    val locked = !owned && level < item.levelRequired
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(12.dp)
            .testTag("shop_backdrop_${item.slug}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BackdropScene(
            slug = item.slug,
            modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GgInk)
                Text(
                    item.rarity.replaceFirstChar { it.uppercase() },
                    fontFamily = Nunito,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = rarityColor(item.rarity),
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.width(96.dp)) {
                when {
                    inUse -> ShopButton(text = "In use", filled = false, enabled = false, onClick = {})
                    owned -> ShopButton(text = if (busy) "…" else "Use", filled = false, enabled = online && !busy, onClick = onEquip)
                    locked -> ShopButton(text = "Level ${item.levelRequired}", filled = false, enabled = false, onClick = {})
                    else -> ShopButton(text = if (busy) "…" else "🪙 ${item.priceCoins}", filled = true, enabled = online && !busy, onClick = onBuy)
                }
            }
        }
    }
}

@Composable
private fun ShopButton(text: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontFamily = Nunito,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = when {
            !enabled && !filled -> GgInkMuted
            filled -> GgBgCream
            else -> GgPrimaryDeep
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    filled && enabled -> GgPrimary
                    filled -> GgMoss
                    else -> GgBgSage
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun ComingSoon(what: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$what coming soon", fontFamily = Caprasimo, fontSize = 20.sp, color = GgPrimaryDeep)
            Text(
                "For now, spend coins on seeds and grow your garden.",
                fontFamily = Nunito,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = GgInkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun rarityColor(rarity: String): Color = when (rarity.lowercase()) {
    "uncommon" -> Color(0xFF5C8A4D)
    "rare" -> Color(0xFF3D86B5)
    "epic" -> Color(0xFF9B7BC9)
    "legendary" -> Color(0xFFD88040)
    else -> GgInkMuted
}
