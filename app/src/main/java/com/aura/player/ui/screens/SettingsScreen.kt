package com.aura.player.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.player.data.AccentPalettes
import com.aura.player.data.GlassQualityNames
import com.aura.player.diag.CrashLog
import com.aura.player.ui.LocalAura
import com.aura.player.ui.LocalBottomPad
import com.aura.player.ui.components.GlassAlert
import com.aura.player.ui.components.GlassDropdownPill
import com.aura.player.ui.components.GlassIconButton
import com.aura.player.ui.components.GlassToggle
import com.aura.player.ui.components.MenuItemSpec
import com.aura.player.ui.components.PlainIconButton
import com.aura.player.ui.components.ScreenTitle
import com.aura.player.ui.components.SectionHeader
import com.aura.player.ui.components.SoftButton
import com.aura.player.ui.components.PrimaryButton
import com.aura.player.ui.glass.GlassLevel
import com.aura.player.ui.glass.glass
import com.aura.player.ui.theme.AuraColors
import com.aura.player.ui.theme.AuraType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val env = LocalAura.current
    val st = env.settings
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val quality by st.quality.collectAsStateWithLifecycle()
    val accent by st.accent.collectAsStateWithLifecycle()
    val ambience by st.ambience.collectAsStateWithLifecycle()
    val reduce by st.reduceMotion.collectAsStateWithLifecycle()
    val haptics by st.haptics.collectAsStateWithLifecycle()
    val scanning by env.repo.scanning.collectAsStateWithLifecycle()
    val count by remember { env.repo.dao.trackCount() }.collectAsStateWithLifecycle(0)
    var folders by remember { mutableStateOf(env.repo.grantedFolders()) }
    var crashReport by remember { mutableStateOf(CrashLog.read(ctx)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            folders = env.repo.grantedFolders()
            scope.launch { env.repo.rescan(); env.overlay.showToast("Folder added") }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPad.current), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item("title") { ScreenTitle("Settings") }

        item("appearance") {
            Group("Appearance") {
                Row2("Accent", Icons.Rounded.Palette) {
                    GlassDropdownPill(AccentPalettes[accent].name) { rect ->
                        env.overlay.menu(rect, AccentPalettes.mapIndexed { i, p -> MenuItemSpec(p.name, checked = i == accent) { st.setAccent(i) } })
                    }
                }
                Row2("Glass quality", Icons.Rounded.BlurOn) {
                    GlassDropdownPill(GlassQualityNames[quality]) { rect ->
                        env.overlay.menu(rect, GlassQualityNames.mapIndexed { i, n -> MenuItemSpec(n, checked = i == quality) { st.setQuality(i) } })
                    }
                }
                Row2("Artwork ambience", Icons.Rounded.AutoAwesome) { GlassToggle(ambience, st::setAmbience) }
                Row2("Reduce motion", Icons.Rounded.SlowMotionVideo) { GlassToggle(reduce, st::setReduceMotion) }
                Row2("Haptics", Icons.Rounded.Vibration) { GlassToggle(haptics, st::setHaptics) }
            }
        }

        item("library") {
            Group("Library") {
                Row2("$count songs", Icons.Rounded.Refresh, subtitle = if (scanning) "Scanning…" else "Device library") {
                    SoftButton(if (scanning) "…" else "Rescan", { scope.launch { env.repo.rescan(); env.overlay.showToast("Library updated") } })
                }
                folders.forEach { uri ->
                    Row2(
                        Uri.decode(uri.lastPathSegment ?: uri.toString()).substringAfterLast(':').ifEmpty { "Folder" },
                        Icons.Rounded.FolderOpen,
                    ) {
                        PlainIconButton(Icons.Rounded.Close, {
                            ctx.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            folders = env.repo.grantedFolders()
                            scope.launch { env.repo.rescan() }
                        })
                    }
                }
                Box(Modifier.padding(16.dp)) {
                    PrimaryButton("Add music folder", { picker.launch(null) }, Modifier.fillMaxWidth(), Icons.Rounded.FolderOpen)
                }
                if (!env.repo.hasAudioPermission()) {
                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        SoftButton("Allow device music access", { env.requestAudio() }, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        item("diagnostics") {
            Group("Diagnostics") {
                Row2(
                    "Last crash report", Icons.Rounded.BugReport,
                    subtitle = if (crashReport != null) "A crash was recorded" else "None recorded",
                ) {
                    SoftButton("View", {
                        val report = crashReport
                        if (report == null) {
                            env.overlay.showToast("No crash recorded")
                        } else {
                            env.overlay.dialog { dismiss ->
                                CrashReportDialog(report, dismiss, onClear = { CrashLog.clear(ctx); crashReport = null })
                            }
                        }
                    })
                }
            }
        }

        item("data") {
            Group("Data") {
                Row2("Clear play history", Icons.Rounded.Delete) {
                    SoftButton("Clear", {
                        env.overlay.dialog { dismiss ->
                            GlassAlert("Clear play history?", "Recently played will be emptied.", "Clear", true, dismiss) {
                                scope.launch { env.repo.dao.clearHistory(); env.repo.dao.clearSearches(); env.overlay.showToast("History cleared") }
                            }
                        }
                    })
                }
                Row2("About AURA", Icons.Rounded.Info, subtitle = "Version 1.0.0") {
                    SoftButton("Open", {
                        env.overlay.dialog { dismiss ->
                            GlassAlert("AURA 1.0", "A Liquid Glass music player. Backdrop blur is active on Android 12+ at Balanced or High quality; older devices use frosted glass.", "OK", false, dismiss) {}
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(title.uppercase(), style = AuraType.Label, color = AuraColors.TextTertiary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(30.dp), GlassLevel.Regular).padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun Row2(title: String, icon: ImageVector, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).glass(CircleShape, GlassLevel.Strong), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AuraColors.Cyan, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AuraType.Body, color = AuraColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = AuraType.Caption, color = AuraColors.TextSecondary)
        }
        trailing()
    }
}

/** Shows the stack trace recorded by [CrashLog] so a real device crash can be copied straight into a bug report. */
@Composable
private fun CrashReportDialog(report: String, dismiss: () -> Unit, onClear: () -> Unit) {
    val env = LocalAura.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Last crash report", style = AuraType.Title, color = AuraColors.TextPrimary)
        Box(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            Text(
                report.take(6000),
                style = AuraType.Caption.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp),
                color = AuraColors.TextSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("Copy", {
                clipboard.setText(AnnotatedString(report))
                env.overlay.showToast("Copied")
            }, Modifier.weight(1f))
            SoftButton("Clear", { onClear(); dismiss() }, Modifier.weight(1f))
        }
    }
}
