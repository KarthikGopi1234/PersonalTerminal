package dev.personalterminal.ui.watch

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Watch
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Palettes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch

private val movements = listOf("automatic", "manual", "quartz", "solar", "spring drive", "smart")

@Composable
fun WatchEditScreen(app: PersonalTerminalApp, nav: NavHostController, watchId: Long) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Watch?>(null) }
    var loaded by remember { mutableStateOf(watchId == 0L) }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var reference by remember { mutableStateOf("") }
    var movement by remember { mutableStateOf("") }
    var caseSize by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("cyan") }
    var notes by remember { mutableStateOf("") }
    val photo = rememberPhotoPickerState(initialPath = null, prefix = "watch")
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(watchId) {
        if (watchId != 0L) app.watches.watch(watchId)?.let { w ->
            original = w; brand = w.brand; model = w.model; nickname = w.nickname; reference = w.reference; movement = w.movement
            caseSize = w.caseSizeMm?.toString()?.removeSuffix(".0") ?: ""; color = w.color; notes = w.notes
            if (photo.photoPath == null) photo.photoPath = w.photoPath
        }
        loaded = true
    }

    if (photoPickerCamera(app, photo)) return

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PromptLine(if (watchId == 0L) "watch add" else "watch edit")
        if (!loaded) { Comment("loading…"); return@Column }

        // Profile photo: camera, recent photos or any image file
        PhotoPickerRow(app, photo, placeholder = "⌚", placeholderColor = p.named(color))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TermTextField(value = brand, onValueChange = { brand = it }, label = "brand", placeholder = "Seiko", modifier = Modifier.weight(1f))
            TermTextField(value = model, onValueChange = { model = it }, label = "model", placeholder = "SKX007", modifier = Modifier.weight(1f))
        }
        TermTextField(value = nickname, onValueChange = { nickname = it }, label = "nickname", placeholder = "optional, e.g. the diver")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TermTextField(value = reference, onValueChange = { reference = it }, label = "reference", placeholder = "ref no.", modifier = Modifier.weight(1f))
            TermTextField(value = caseSize, onValueChange = { caseSize = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = "case mm", placeholder = "40", keyboardType = KeyboardType.Decimal, modifier = Modifier.weight(1f))
        }
        Column {
            Text("movement:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                movements.take(3).forEach { m -> MoveChip(m, movement == m) { movement = if (movement == m) "" else m } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                movements.drop(3).forEach { m -> MoveChip(m, movement == m) { movement = if (movement == m) "" else m } }
            }
        }
        Column {
            Text("accent:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Palettes.colorNames.forEach { c ->
                    val col = p.named(c)
                    Box(Modifier.size(28.dp).background(col, RoundedCornerShape(4.dp)).border(if (color == c) 2.dp else 0.dp, if (color == c) p.fg else col, RoundedCornerShape(4.dp)).clickable { color = c },
                        contentAlignment = Alignment.Center) { if (color == c) Text("✓", color = p.bg, fontWeight = FontWeight.Bold) }
                }
            }
        }
        TermTextField(value = notes, onValueChange = { notes = it }, label = "notes", placeholder = "service history, strap, story…", singleLine = false, imeAction = ImeAction.Default)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton(if (watchId == 0L) "add to collection" else "save", filled = true, color = p.cyan, modifier = Modifier.weight(1f), enabled = brand.isNotBlank() || model.isNotBlank() || nickname.isNotBlank(), onClick = {
                scope.launch {
                    val w = (original ?: Watch(brand = "", model = "")).copy(
                        brand = brand.trim(), model = model.trim(), nickname = nickname.trim(), reference = reference.trim(), movement = movement,
                        caseSizeMm = caseSize.toFloatOrNull(), color = color, notes = notes.trim(), photoPath = photo.photoPath,
                    )
                    app.watches.saveWatch(w)
                    nav.popBackStack()
                }
            })
            TermButton("cancel", color = p.fgDim, onClick = { nav.popBackStack() })
        }
        if (original != null) {
            TerminalPanel(title = "danger zone", titleColor = p.red, borderColor = p.red.copy(alpha = 0.5f)) {
                if (!confirmDelete) TermButton("rm watch", color = p.red, onClick = { confirmDelete = true })
                else TermButton("confirm delete (removes wear logs)", color = p.red, filled = true, onClick = {
                    scope.launch { app.watches.deleteWatch(original!!); nav.popBackStack(dev.personalterminal.ui.navigation.Routes.WATCHES, false) }
                })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MoveChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = Term.palette
    Text(
        label, color = if (selected) p.bg else p.fgDim, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(if (selected) p.cyan else p.bgAlt, RoundedCornerShape(4.dp)).border(1.dp, if (selected) p.cyan else p.border, RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
