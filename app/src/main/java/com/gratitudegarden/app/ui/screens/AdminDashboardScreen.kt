package com.gratitudegarden.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gratitudegarden.app.data.ADMIN_ITEMS_TABLE
import com.gratitudegarden.app.data.ADMIN_USER_TABLES
import com.gratitudegarden.app.data.AdminField
import com.gratitudegarden.app.data.AdminFieldType
import com.gratitudegarden.app.data.AdminScope
import com.gratitudegarden.app.data.AdminTableSpec
import com.gratitudegarden.app.data.cell
import com.gratitudegarden.app.ui.admin.AdminMode
import com.gratitudegarden.app.ui.admin.AdminUser
import com.gratitudegarden.app.ui.admin.AdminViewModel
import com.gratitudegarden.app.ui.components.PillButton
import com.gratitudegarden.app.ui.theme.Caprasimo
import com.gratitudegarden.app.ui.theme.Nunito
import com.gratitudegarden.app.ui.theme.PgAccent
import com.gratitudegarden.app.ui.theme.PgBgSage
import com.gratitudegarden.app.ui.theme.PgInk
import com.gratitudegarden.app.ui.theme.PgInkMuted
import com.gratitudegarden.app.ui.theme.PgInkSoft
import com.gratitudegarden.app.ui.theme.PgMoss
import com.gratitudegarden.app.ui.theme.PgPrimary
import com.gratitudegarden.app.ui.theme.PgPrimaryDeep
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

@Composable
fun AdminDashboardScreen(onBack: () -> Unit) {
    val vm: AdminViewModel = viewModel(factory = AdminViewModel.Factory)
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.message) {
        if (ui.message != null) {
            delay(2800)
            vm.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PgBgSage)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "‹ Back",
                fontFamily = Nunito,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = PgPrimaryDeep,
                modifier = Modifier.clickable(onClick = onBack),
            )
            Spacer(Modifier.width(14.dp))
            Text(text = "Admin Dashboard", fontFamily = Caprasimo, fontSize = 24.sp, color = PgPrimaryDeep)
        }

        // Mode tabs
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabChip("Users", ui.mode == AdminMode.USERS) { vm.setMode(AdminMode.USERS) }
            TabChip("Items Catalog", ui.mode == AdminMode.CATALOG) { vm.setMode(AdminMode.CATALOG) }
        }

        ui.error?.let { Banner(it, error = true) }
        ui.message?.let { Banner(it, error = false) }

        when {
            ui.loading -> Text("Loading…", fontFamily = Nunito, color = PgInkSoft)

            ui.mode == AdminMode.CATALOG -> {
                TableSection(
                    spec = ADMIN_ITEMS_TABLE,
                    rows = ui.items,
                    itemOptions = ui.itemOptions,
                    canCreate = true,
                    busy = ui.busy,
                    onCreate = { vm.create(ADMIN_ITEMS_TABLE, it) },
                    onSave = { row, v -> vm.save(ADMIN_ITEMS_TABLE, row, v) },
                    onDelete = { vm.delete(ADMIN_ITEMS_TABLE, it) },
                )
            }

            else -> {
                Text("Select a user", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PgInkSoft)
                ui.users.forEach { user ->
                    UserRow(user = user, selected = user.id == ui.selectedUserId) { vm.selectUser(user.id) }
                }

                if (ui.selectedUserId != null) {
                    Spacer(Modifier.height(4.dp))
                    ADMIN_USER_TABLES.forEach { spec ->
                        val canCreate = spec.scope != AdminScope.BY_GARDEN || ui.gardenId != null
                        TableSection(
                            spec = spec,
                            rows = ui.userTables[spec.table].orEmpty(),
                            itemOptions = ui.itemOptions,
                            canCreate = canCreate,
                            busy = ui.busy,
                            hint = if (spec.scope == AdminScope.BY_GARDEN && ui.gardenId == null)
                                "Create a garden first to add plants." else null,
                            onCreate = { vm.create(spec, it) },
                            onSave = { row, v -> vm.save(spec, row, v) },
                            onDelete = { vm.delete(spec, it) },
                        )
                    }

                    ui.users.firstOrNull { it.id == ui.selectedUserId }?.let { selected ->
                        DeleteAccountCard(
                            user = selected,
                            busy = ui.busy,
                            onConfirm = { vm.deleteUserAccount(selected) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Section + editors ────────────────────────────────────────────────────────

@Composable
private fun TableSection(
    spec: AdminTableSpec,
    rows: List<JsonObject>,
    itemOptions: List<Pair<String, String>>,
    canCreate: Boolean,
    busy: Boolean,
    hint: String? = null,
    onCreate: (Map<String, String?>) -> Unit,
    onSave: (JsonObject, Map<String, String?>) -> Unit,
    onDelete: (JsonObject) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = spec.title, fontFamily = Caprasimo, fontSize = 18.sp, color = PgInk)
            Spacer(Modifier.width(8.dp))
            Text(text = "(${rows.size})", fontFamily = Nunito, fontSize = 13.sp, color = PgInkMuted)
        }
        hint?.let { Text(it, fontFamily = Nunito, fontSize = 12.sp, color = PgInkMuted) }

        if (spec.single) {
            val row = rows.firstOrNull()
            if (row == null) {
                if (canCreate) {
                    Text("No row yet.", fontFamily = Nunito, fontSize = 12.sp, color = PgInkMuted)
                    RowEditor(spec, defaults(spec), isCreate = true, itemOptions, busy, onSubmit = onCreate, onDelete = null)
                }
            } else {
                RowEditor(spec, fromRow(spec, row), isCreate = false, itemOptions, busy,
                    onSubmit = { onSave(row, it) }, onDelete = { onDelete(row) })
            }
        } else {
            rows.forEach { row ->
                RowEditor(spec, fromRow(spec, row), isCreate = false, itemOptions, busy,
                    onSubmit = { onSave(row, it) }, onDelete = { onDelete(row) })
                Spacer(Modifier.height(2.dp))
            }
            if (canCreate) {
                var adding by remember { mutableStateOf(false) }
                if (adding) {
                    RowEditor(spec, defaults(spec), isCreate = true, itemOptions, busy,
                        onSubmit = { adding = false; onCreate(it) }, onDelete = null)
                    TextButton(onClick = { adding = false }) { Text("Cancel", color = PgInkMuted) }
                } else {
                    TextButton(onClick = { adding = true }) {
                        Text("+ Add ${spec.title}", color = PgPrimaryDeep, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowEditor(
    spec: AdminTableSpec,
    initial: Map<String, String?>,
    isCreate: Boolean,
    itemOptions: List<Pair<String, String>>,
    busy: Boolean,
    onSubmit: (Map<String, String?>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val state = remember(initial) {
        mutableStateMapOf<String, String>().apply {
            spec.fields.forEach { put(it.column, initial[it.column] ?: "") }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, PgMoss, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        spec.fields.forEach { f ->
            FieldInput(f, state[f.column].orEmpty(), itemOptions) { state[f.column] = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = if (isCreate) "Create" else "Save",
                onClick = { onSubmit(state.toMap()) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
            if (onDelete != null) {
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Text("Delete", color = PgAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from ${spec.title}?") },
            text = { Text("This permanently removes the row from Supabase.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) {
                    Text("Delete", color = PgAccent)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FieldInput(
    field: AdminField,
    value: String,
    itemOptions: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    when (field.type) {
        AdminFieldType.BOOL -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(field.label, fontFamily = Nunito, fontSize = 14.sp, color = PgInk, modifier = Modifier.weight(1f))
            Switch(checked = value == "true", onCheckedChange = { onChange(it.toString()) })
        }

        AdminFieldType.ENUM ->
            PickerField(field.label, field.enumValues.map { it to it }, value, field.nullable, onChange)

        AdminFieldType.ITEM_REF ->
            PickerField(field.label, itemOptions, value, field.nullable, onChange)

        AdminFieldType.INT -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        else -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(field.label + if (field.nullable) " (optional)" else "") },
            singleLine = field.column != "entry_text" && field.column != "description",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Read-only field that opens a dropdown of [options] (value to label). */
@Composable
private fun PickerField(
    label: String,
    options: List<Pair<String, String>>,
    value: String,
    allowEmpty: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "—" }
    Column {
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PgInkSoft)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, PgMoss, RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(display, fontFamily = Nunito, fontSize = 14.sp, color = PgInk, modifier = Modifier.weight(1f))
                Text("▾", color = PgInkMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowEmpty) {
                    DropdownMenuItem(text = { Text("—") }, onClick = { onChange(""); expanded = false })
                }
                options.forEach { (v, lbl) ->
                    DropdownMenuItem(text = { Text(lbl) }, onClick = { onChange(v); expanded = false })
                }
            }
        }
    }
}

// ── Small pieces ─────────────────────────────────────────────────────────────

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) PgPrimary else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            fontFamily = Nunito,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (selected) Color.White else PgInk,
        )
    }
}

@Composable
private fun UserRow(user: AdminUser, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PgPrimary.copy(alpha = 0.16f) else Color.White)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) PgPrimary else PgMoss, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PgInk)
            Text(
                text = user.id.take(8) + "…",
                fontFamily = Nunito,
                fontSize = 11.sp,
                color = PgInkMuted,
            )
        }
        if (user.isAdmin) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PgPrimaryDeep)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("ADMIN", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Color.White)
            }
        }
    }
}

/** Danger-zone card: fully delete the selected user's account (auth + cascade). */
@Composable
private fun DeleteAccountCard(user: AdminUser, busy: Boolean, onConfirm: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PgAccent.copy(alpha = 0.10f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "Danger zone", fontFamily = Caprasimo, fontSize = 18.sp, color = PgAccent)
        Text(
            text = "Permanently delete ${user.displayName}'s account. The login and every row " +
                "above (profile, settings, stats, wallet, entries, garden, inventory) are wiped " +
                "via cascade. This can't be undone.",
            fontFamily = Nunito,
            fontSize = 12.sp,
            color = PgInkSoft,
        )
        OutlinedButton(
            onClick = { confirm = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Delete entire account", color = PgAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete ${user.displayName}'s account?") },
            text = { Text("This removes the auth login and cascades to every owned row. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirm = false; onConfirm() }) {
                    Text("Delete account", color = PgAccent)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Banner(text: String, error: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (error) PgAccent.copy(alpha = 0.15f) else PgPrimary.copy(alpha = 0.15f))
            .padding(12.dp),
    ) {
        Text(text, fontFamily = Nunito, fontSize = 13.sp, color = if (error) PgAccent else PgPrimaryDeep)
    }
}

// ── Row <-> editor-state helpers ─────────────────────────────────────────────

private fun fromRow(spec: AdminTableSpec, row: JsonObject): Map<String, String?> =
    spec.fields.associate { it.column to row.cell(it.column) }

private fun defaults(spec: AdminTableSpec): Map<String, String?> =
    spec.fields.associate { f ->
        f.column to when (f.type) {
            AdminFieldType.BOOL -> "false"
            AdminFieldType.ENUM -> f.enumValues.firstOrNull().orEmpty()
            else -> ""
        }
    }
