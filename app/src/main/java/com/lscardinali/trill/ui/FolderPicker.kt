package com.lscardinali.trill.ui

import android.os.Environment
import android.view.KeyEvent as AKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lscardinali.trill.ui.theme.CC
import java.io.File

private val internalRoot: File = Environment.getExternalStorageDirectory()

private fun storageRoots(): List<File> =
    listOf(internalRoot) + File("/storage").listFiles().orEmpty()
        .filter { it.isDirectory && it.name != "self" && it.name != "emulated" && it.canRead() }

private fun isRoot(f: File) = storageRoots().any { it.absolutePath == f.absolutePath }

private fun label(f: File): String = when {
    f.absolutePath == internalRoot.absolutePath -> "Internal storage"
    f.parentFile?.absolutePath == "/storage" -> "SD card (${f.name})"
    else -> f.name
}

private fun prettyPath(f: File?): String {
    if (f == null) return "Choose a storage"
    val root = storageRoots().firstOrNull { f.absolutePath.startsWith(it.absolutePath) } ?: return f.absolutePath
    val rest = f.absolutePath.removePrefix(root.absolutePath).trim('/')
    return if (rest.isEmpty()) label(root) else "${label(root)} › ${rest.replace("/", " › ")}"
}

/**
 * Controller-first folder browser in Cocoon's style. Uses the all-files permission, so no system
 * picker is needed and the D-pad works everywhere.
 */
@Composable
fun FolderPicker(title: String, initial: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var dir by remember {
        mutableStateOf(initial?.let(::File)?.let { f -> generateSequence(f) { it.parentFile }.firstOrNull { it.isDirectory } })
    }
    var refresh by remember { mutableIntStateOf(0) }
    val entries = remember(dir, refresh) {
        val d = dir
        if (d == null) storageRoots()
        else d.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
            .sortedBy { it.name.lowercase() }
    }
    val firstItem = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun goUp() { val d = dir; dir = if (d == null || isRoot(d)) null else d.parentFile }

    BackHandler { if (dir == null) onDismiss() else goUp() }
    PadShortcuts { key, repeat ->
        if (repeat > 0) return@PadShortcuts true
        when (key) {
            AKeyEvent.KEYCODE_BUTTON_START, AKeyEvent.KEYCODE_BUTTON_X -> { dir?.let { onPick(it.absolutePath) }; true }
            AKeyEvent.KEYCODE_BUTTON_L1 -> { goUp(); true }
            else -> true
        }
    }
    LaunchedEffect(dir) {
        listState.scrollToItem(0)
        firstItem.requestSoon()
    }

    Scrim {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.86f).fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(28.dp)).background(CC.Bg).padding(vertical = 6.dp),
        ) {
            Header(title, onBack = { if (dir == null) onDismiss() else goUp() })
            Text(
                prettyPath(dir), color = CC.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(horizontal = 22.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val d = dir
                if (d != null) {
                    item(key = "up") {
                        OptionRow(Icons.Rounded.SubdirectoryArrowLeft, "Up one level", null, onClick = ::goUp, focusRequester = firstItem)
                    }
                    item(key = "new") {
                        NewFolderRow(d) { created -> dir = created; refresh++ }
                    }
                }
                items(entries, key = { it.absolutePath }) { f ->
                    val icon = when {
                        d != null -> Icons.Rounded.Folder
                        f.absolutePath == internalRoot.absolutePath -> Icons.Rounded.PhoneAndroid
                        else -> Icons.Rounded.SdCard
                    }
                    OptionRow(
                        icon, if (d == null) label(f) else f.name, null,
                        onClick = { dir = f },
                        focusRequester = if (d == null && f == entries.first()) firstItem else null,
                    )
                }
                if (d != null && entries.isEmpty()) {
                    item { Text("No subfolders here", color = CC.Dim, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, top = 4.dp)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                HintBar(if (dir != null) listOf("A" to "Open", "L1" to "Up", "B" to "Back") else listOf("A" to "Open", "B" to "Close"), modifier = Modifier.weight(1f))
                if (dir != null) {
                    Spacer(Modifier.width(8.dp))
                    PillButton("Use this folder", "☰", onClick = { dir?.let { onPick(it.absolutePath) } }, icon = Icons.Rounded.Check)
                }
            }
        }
    }
}

@Composable
private fun NewFolderRow(parent: File, onCreated: (File) -> Unit) {
    EditablePill(
        value = "",
        placeholder = "New folder…",
        icon = Icons.Rounded.CreateNewFolder,
        onCommit = { name ->
            val clean = name.replace(Regex("""[\\/:*?"<>|]"""), "").trim()
            if (clean.isNotEmpty()) {
                val f = File(parent, clean)
                if (f.isDirectory || f.mkdirs()) onCreated(f)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Settings row showing a folder; opens the picker. */
@Composable
fun FolderRow(title: String, path: String, emptyLabel: String, onClick: () -> Unit) {
    OptionRow(
        Icons.Rounded.Folder,
        title,
        if (path.isBlank()) emptyLabel else prettyPath(File(path)),
        onClick = onClick,
    ) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = CC.Sub)
    }
}
