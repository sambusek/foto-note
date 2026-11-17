package com.sambusek.fotonote

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Surface fornisce sfondo e colore del contenuto (testi e icone) giusti per il tema chiaro o scuro
    Surface(Modifier.fillMaxSize(), color = cs.surface, contentColor = cs.onSurface) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
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
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        bottomBar?.invoke()
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
) {
    if (selected) FilledTonalIconButton(onClick, enabled = enabled, shape = SmallCorners) { Icon(icon, description) }
    else IconButton(onClick, enabled = enabled) { Icon(icon, description) }
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

/** Riga di elenco toccabile, con freccia. */
@Composable
fun ListRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Caption(subtitle)
        }
        Icon(AppIcons.Next, null, tint = cs.onSurfaceVariant)
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
