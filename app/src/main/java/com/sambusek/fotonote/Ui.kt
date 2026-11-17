package com.sambusek.fotonote

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Componenti comuni dell'interfaccia (Material 3, versione compatta).

private val SmallCorners = RoundedCornerShape(6.dp)
private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

/**
 * Struttura di una schermata: barra superiore compatta (indietro, titolo, azioni),
 * contenuto e, se serve, una barra in basso.
 */
@Composable
fun AppScreen(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    brand: Boolean = true,
    maxWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    // su tablet le schermate di testo restano di larghezza leggibile, centrate
    val narrow = Modifier.fillMaxWidth().then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier)
    val cs = MaterialTheme.colorScheme
    // Surface fornisce sfondo e colore del contenuto (testi e icone) giusti per il tema chiaro o scuro
    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (brand) Text(
            "Pastashoot",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = cs.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().height(if (brand) 48.dp else 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) IconButton(onBack) { Icon(AppIcons.Back, "Indietro") }
            else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(narrow.fillMaxHeight(), content = content)
        }
        if (bottomBar != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(narrow) { bottomBar() }
        }
    }
    }
}

/** Pulsante a icona; con [selected] è evidenziato come strumento attivo. */
@Composable
fun ToolIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (selected) FilledTonalIconButton(onClick, modifier, enabled = enabled, shape = SmallCorners) { Icon(icon, description) }
    else IconButton(onClick, modifier, enabled = enabled) { Icon(icon, description) }
}

/** Azione testuale della barra superiore: pulsante pieno se principale. */
@Composable
fun BarAction(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    if (primary) Button(
        onClick, enabled = enabled, shape = SmallCorners, contentPadding = ButtonPadding,
        modifier = Modifier.padding(horizontal = 4.dp).height(36.dp),
    ) { Text(text, maxLines = 1) }
    else TextButton(onClick, enabled = enabled, contentPadding = ButtonPadding) { Text(text, maxLines = 1) }
}

/** Pulsante di testo: pieno se principale, con contorno altrimenti. */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    if (primary) Button(onClick, modifier, enabled, shape = SmallCorners, contentPadding = ButtonPadding) {
        Text(text, maxLines = 1)
    } else OutlinedButton(onClick, modifier, enabled, shape = SmallCorners, contentPadding = ButtonPadding) {
        Text(text, maxLines = 1)
    }
}

/** Pulsante "⋮" con le azioni meno frequenti. */
@Composable
fun OverflowMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(AppIcons.More, "Altre azioni") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((text, action) in items) {
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() })
            }
        }
    }
}

/** Sezione: intestazione colorata e scheda con il contenuto. */
@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            content = content,
        )
    }
}

/** Riga con interruttore, toccabile per intero. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Campo di testo compatto (40 dp), a una riga o su più righe fino a [maxHeight]. */
@Composable
fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxHeight: androidx.compose.ui.unit.Dp = 120.dp,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(4.dp)
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 15.sp, color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.38f),
    )
    BasicTextField(
        value = value,
        onValueChange = { onChange(if (singleLine) it.replace("\n", " ") else it) },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(cs.primary),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp, max = if (singleLine) 40.dp else maxHeight)
            .clip(shape)
            .background(cs.surfaceContainerHighest)
            .border(1.dp, cs.outlineVariant, shape),
        decorationBox = { inner ->
            Box(
                Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = cs.onSurfaceVariant), maxLines = 1)
                inner()
            }
        },
    )
}

/** Testo secondario piccolo. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Messaggio centrato (caricamento, liste vuote, errori). */
@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = modifier.padding(24.dp),
    )
}

/** Pallino di stato: colore d'accento se a posto, rosso se manca qualcosa. */
@Composable
fun StatusDot(ok: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) cs.primary else cs.error))
}

/** Etichetta breve di stato (es. "Chiuso", "V02"); [strong] = piena nel colore d'accento. */
@Composable
fun Tag(text: String, strong: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (strong) cs.onPrimary else cs.onSecondaryContainer,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (strong) cs.primary else cs.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Riga di elenco toccabile, con eventuali etichette accanto al titolo e freccia. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ListRow(
    title: String,
    subtitle: String?,
    tags: List<Pair<String, Boolean>> = emptyList(),
    locked: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                for ((t, strong) in tags) Tag(t, strong)
                if (locked) Icon(
                    AppIcons.Lock, "Chiuso", tint = cs.primary,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }
            if (subtitle != null) Caption(subtitle)
        }
        if (selected) Icon(AppIcons.Check, "Selezionato", tint = cs.primary)
        else Icon(AppIcons.Next, null, tint = cs.onSurfaceVariant)
    }
}

@Composable
fun ListDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Dialogo di conferma. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text(dismiss) } },
    )
}

/** Dialogo con un campo di testo (es. il nome di un nuovo sopralluogo). */
@Composable
fun PromptDialog(
    title: String,
    message: String,
    placeholder: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.replace("\n", " ") },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

/** Dialogo per un testo lungo (es. la nota generale del sopralluogo). */
@Composable
fun TextDialog(
    title: String,
    message: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, minLines = 5, maxLines = 12,
                    placeholder = { Text("Scrivi o detta la nota…") }, shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(text.trim()) }) { Text("Salva") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

/** Riquadro informativo colorato, con icona ed eventuale azione. */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cs.secondaryContainer)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer,
            modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp),
        )
        if (action != null) TextButton(onAction) { Text(action) }
    }
}

/** Dialogo non chiudibile con l'avanzamento di un'operazione lunga. */
@Composable
fun ProgressDialog(message: String, fraction: Float) {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(12.dp),
        text = {
            Column {
                Text(message)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
    )
}

/** Miniatura quadrata di una foto. */
@Composable
fun PhotoThumb(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null) }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Contrassegno tondo su una miniatura (foto annotata, foto selezionata). */
@Composable
fun ThumbBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.padding(4.dp).size(22.dp).clip(CircleShape).background(cs.primary),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = cs.onPrimary, modifier = Modifier.size(14.dp)) }
}

/** Velo e bordo d'accento su una miniatura selezionata. */
@Composable
fun Modifier.selectedOverlay(selected: Boolean): Modifier =
    if (!selected) this
    else this.border(3.dp, MaterialTheme.colorScheme.primary).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))

/** Spazio tra le miniature delle griglie. */
val GridGap = 2.dp

/** Larghezza massima delle schermate di testo (impostazioni, elenchi, moduli). */
val ReadableWidth = 720.dp

/** Lato minimo delle miniature: più grandi sugli schermi larghi. */
val ThumbSize: Dp @Composable get() = if (LocalConfiguration.current.screenWidthDp >= 600) 132.dp else 92.dp

/** Campo compatto con, se ci sono [options], un menu a tendina per sceglierne una (si può anche scrivere). */
@Composable
fun ChoiceField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    options: List<String>,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return CompactField(value, onChange, placeholder, modifier)
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CompactField(value, onChange, placeholder, Modifier.weight(1f))
        Box {
            IconButton({ open = true }) { Icon(AppIcons.DropDown, "Scegli dall'elenco") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (o in options) DropdownMenuItem(
                    text = { Text(o, fontWeight = if (o == value) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onChange(o) },
                )
            }
        }
    }
}
