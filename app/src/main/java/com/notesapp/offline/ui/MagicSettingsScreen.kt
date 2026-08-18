package com.notesapp.offline.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notesapp.offline.data.EffectType
import com.notesapp.offline.data.LockMode
import com.notesapp.offline.data.MagicEffect
import com.notesapp.offline.data.MagicRepository
import com.notesapp.offline.data.MagicStore
import com.notesapp.offline.ui.theme.AccentA
import com.notesapp.offline.ui.theme.AccentB
import com.notesapp.offline.ui.theme.Danger
import com.notesapp.offline.ui.theme.GlassRadius
import com.notesapp.offline.ui.theme.glassPanel
import kotlinx.coroutines.launch
import java.io.File

/**
 * The hidden "Magic Settings" screen — direct functional + visual port of
 * the web app's #magicSettings panel: Lock Method, the classic lock's
 * background photo, the Home Screen disguise's wallpaper/Notes-icon/decoy
 * app customization, and the Effects list (with the same glass-card /
 * ACTIVE-pill styling as the original, pulled from its CSS).
 */
@Composable
fun MagicSettingsScreen(
    repo: MagicRepository,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenEffect: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bgColor = if (isDarkTheme) Color.Black else Color.White
    val fgColor = if (isDarkTheme) Color.White else Color.Black

    var store by remember { mutableStateOf(MagicStore()) }
    var loaded by remember { mutableStateOf(false) }
    var decoyAppsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        store = repo.load()
        loaded = true
    }

    fun persist(updated: MagicStore) {
        store = updated
        scope.launch { repo.save(updated) }
    }

    val lockBgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val path = copyImageToInternal(context, uri, repo.mediaDir, "lock_bg")
                // Only overwrite the saved setting on an actual successful
                // copy — if it silently failed, leave whatever background
                // was already set (if any) alone rather than blanking it.
                if (path != null) {
                    persist(store.copy(lockBackgroundPath = path))
                }
            }
        }
    }

    // Single shared picker for every Home Screen-mode image slot (the
    // wallpaper, the disguised Notes icon, and each decoy app's icon) —
    // `pendingIconTarget` says which one the next result should apply to.
    // "wallpaper" and "notes" are the two fixed slots; anything else is
    // taken as the decoy app name being re-skinned.
    var pendingIconTarget by remember { mutableStateOf<String?>(null) }
    val hsIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingIconTarget
        pendingIconTarget = null
        if (uri != null && target != null) {
            scope.launch {
                val path = copyImageToInternal(context, uri, repo.mediaDir, "hs_${target}")
                if (path != null) {
                    when (target) {
                        "wallpaper" -> persist(store.copy(homeWallpaperPath = path))
                        "notes" -> persist(store.copy(notesIconPath = path))
                        else -> persist(store.copy(appIconOverrides = store.appIconOverrides + (target to path)))
                    }
                }
            }
        }
    }
    fun pickHsIcon(target: String) {
        pendingIconTarget = target
        hsIconPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (!loaded) return

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // Top bar — back arrow, centered title, symmetric spacer (matches
        // the web app's icon-btn / h1 / 40px-spacer layout).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .glassPanel(radius = GlassRadius.lg, tint = fgColor)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fgColor)
            }
            Text(
                "Settings",
                color = fgColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(start = 18.dp)
            )
            Box(modifier = Modifier.size(40.dp))
        }

        Text(
            "Each effect's body can use \$\$\$\$ as a placeholder — it's swapped for the word tied to whatever code you enter on the lock screen.",
            color = fgColor.copy(alpha = 0.34f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionLabel("Lock Method", fgColor)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockModePill(
                            label = "Classic Lock",
                            selected = store.lockMode == LockMode.CLASSIC,
                            fgColor = fgColor,
                            onClick = { persist(store.copy(lockMode = LockMode.CLASSIC)) }
                        )
                        LockModePill(
                            label = "Home Screen",
                            selected = store.lockMode == LockMode.HOME_SCREEN,
                            fgColor = fgColor,
                            onClick = { persist(store.copy(lockMode = LockMode.HOME_SCREEN)) }
                        )
                    }

                    if (store.lockMode == LockMode.HOME_SCREEN) {
                        SectionLabel("Home screen wallpaper", fgColor, topPadding = 20.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PhotoThumb(path = store.homeWallpaperPath, fgColor = fgColor)
                            GlassPill("Change photo", fgColor) { pickHsIcon("wallpaper") }
                        }

                        SectionLabel("Disguised Notes icon", fgColor, topPadding = 20.dp)
                        Text(
                            "This is the icon on the fake home screen's last page that actually opens your real notes.",
                            color = fgColor.copy(alpha = 0.34f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PhotoThumb(path = store.notesIconPath, fgColor = fgColor)
                            GlassPill("Change icon", fgColor) { pickHsIcon("notes") }
                        }

                        SectionLabel("Decoy apps", fgColor, topPadding = 20.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { decoyAppsExpanded = !decoyAppsExpanded },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Rename or re-skin any of the ${homeScreenDecoyApps.size} filler apps shown around the disguised Notes icon.",
                                color = fgColor.copy(alpha = 0.34f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (decoyAppsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (decoyAppsExpanded) "Collapse" else "Expand",
                                tint = fgColor.copy(alpha = 0.56f)
                            )
                        }
                    }
                }
            }

            if (store.lockMode == LockMode.HOME_SCREEN && decoyAppsExpanded) {
                items(homeScreenDecoyApps, key = { it.name }) { app ->
                    DecoyAppRow(
                        app = app,
                        iconPath = store.appIconOverrides[app.name],
                        displayName = store.appNameOverrides[app.name] ?: app.name,
                        fgColor = fgColor,
                        onPickIcon = { pickHsIcon(app.name) },
                        onNameChange = { newName ->
                            persist(store.copy(appNameOverrides = store.appNameOverrides + (app.name to newName)))
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionLabel("Lock screen background", fgColor, topPadding = 20.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PhotoThumb(path = store.lockBackgroundPath, fgColor = fgColor)
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassPill("Change photo", fgColor) {
                                    lockBgPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                                if (store.lockBackgroundPath != null) {
                                    GlassPill("Remove", Danger) {
                                        persist(store.copy(lockBackgroundPath = null))
                                    }
                                }
                            }
                        }
                    }

                    SectionLabel("Effects", fgColor, topPadding = 24.dp)
                }
            }

            if (store.effects.isEmpty()) {
                item {
                    Text(
                        "No effects yet. Tap + to create one.",
                        color = fgColor.copy(alpha = 0.34f),
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp, horizontal = 20.dp)
                    )
                }
            } else {
                items(store.effects, key = { it.id }) { fx ->
                    EffectCard(
                        effect = fx,
                        isActive = store.activeEffectId == fx.id,
                        fgColor = fgColor,
                        onClick = { onOpenEffect(fx.id) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

        // FAB — creates a blank effect and jumps straight into its editor,
        // matching the web app's newEffectBtn behavior.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AccentA, AccentB)))
                .clickable {
                    scope.launch {
                        val created = repo.createEffect()
                        onOpenEffect(created.id)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New effect", tint = Color(0xFF0A0A12))
        }
    }
}

@Composable
private fun SectionLabel(text: String, fgColor: Color, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text.uppercase(),
        color = fgColor.copy(alpha = 0.56f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(top = topPadding, bottom = 10.dp)
    )
}

@Composable
private fun LockModePill(label: String, selected: Boolean, fgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AccentA, AccentB)))
                } else {
                    Modifier.glassPanel(radius = 100.dp, tint = fgColor)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF0A0A12) else fgColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GlassPill(label: String, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .glassPanel(radius = 100.dp, tint = textColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhotoThumb(path: String?, fgColor: Color) {
    val bmp = remember(path) {
        path?.let { p -> runCatching { android.graphics.BitmapFactory.decodeFile(p) }.getOrNull() }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .glassPanel(radius = 16.dp, tint = fgColor),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
            )
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = fgColor.copy(alpha = 0.34f))
        }
    }
}

/** One row in the (expandable) Decoy Apps list — a small icon thumbnail
 *  the user can tap to re-skin, plus an editable name field, mirroring
 *  the same icon/rename controls the Notes-icon and wallpaper slots use. */
@Composable
private fun DecoyAppRow(
    app: HsDecoyApp,
    iconPath: String?,
    displayName: String,
    fgColor: Color,
    onPickIcon: () -> Unit,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bmp = remember(iconPath) {
        iconPath?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it) }.getOrNull() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(radius = GlassRadius.md, tint = fgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (bmp != null) Color.White else app.color)
                .clickable(onClick = onPickIcon),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(displayName.take(2).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        BasicTextField(
            value = displayName,
            onValueChange = onNameChange,
            textStyle = TextStyle(color = fgColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            cursorBrush = SolidColor(fgColor),
            singleLine = true,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp)
        )
        GlassPill("Icon", fgColor, onClick = onPickIcon)
    }
}

@Composable
private fun EffectCard(
    effect: MagicEffect,
    isActive: Boolean,
    fgColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metaText = if (effect.type == EffectType.LIST) {
        val n = effect.items.count { it.isNotBlank() }
        "List Force · $n " + if (n == 1) "item" else "items"
    } else {
        val n = effect.outs.size
        "$n " + if (n == 1) "out" else "outs"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(radius = GlassRadius.md, tint = fgColor)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                effect.name.ifBlank { "Untitled effect" },
                color = fgColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Text(
                    "ACTIVE",
                    color = AccentB,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .border(1.dp, AccentB, RoundedCornerShape(100))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            metaText,
            color = fgColor.copy(alpha = 0.34f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Copies a picked gallery image into the app's own storage so it survives
 *  even if the original content:// URI's permission grant doesn't.
 *
 *  Bug this fixes: the previous version called dest.absolutePath
 *  unconditionally after the copy block — if openInputStream(uri) ever
 *  returned null (no exception, just null; it happens), the ?.use{} block
 *  silently did nothing, no file was ever written, and this still reported
 *  success with a path pointing at a file that doesn't exist. Every later
 *  BitmapFactory.decodeFile() on that path then fails silently too — no
 *  crash anywhere, the background (and the settings-screen thumbnail) just
 *  quietly never appears. Now it explicitly checks the stream opened and
 *  the resulting file is non-empty before calling it a success. */
private suspend fun copyImageToInternal(context: Context, uri: Uri, dir: File, prefix: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val dest = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val input = context.contentResolver.openInputStream(uri)
                ?: return@runCatching null
            input.use { stream ->
                dest.outputStream().use { output -> stream.copyTo(output) }
            }
            if (dest.exists() && dest.length() > 0L) dest.absolutePath else {
                dest.delete()
                null
            }
        }.getOrNull()
    }
