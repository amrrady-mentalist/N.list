package com.notesapp.offline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun LockFlowHost(viewModel: LockFlowViewModel) {
    val screen by viewModel.screen.collectAsState()

    when (screen) {
        LockScreenState.BLACKOUT -> BlackoutScreen(onDoubleTap = viewModel::onBlackoutDoubleTap)
        LockScreenState.AMBIENT -> AmbientScreen(onSwipeUp = viewModel::onAmbientSwipeUp)
        LockScreenState.PIN -> PinScreen(viewModel)
    }
}

private fun doubleTapModifier(windowMillis: Long = 320L, onDoubleTap: () -> Unit): Modifier {
    var lastTap = 0L
    return Modifier.pointerInput(Unit) {
        detectTapGestures(onTap = {
            val now = System.currentTimeMillis()
            if (now - lastTap < windowMillis) {
                lastTap = 0L
                onDoubleTap()
            } else {
                lastTap = now
            }
        })
    }
}

@Composable
private fun BlackoutScreen(onDoubleTap: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(doubleTapModifier(onDoubleTap = onDoubleTap))
    )
}

@Composable
private fun AmbientScreen(onSwipeUp: () -> Unit) {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            kotlinx.coroutines.delay(15_000)
        }
    }

    var startY = 0f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { startY = it.y },
                    onDragEnd = {},
                    onVerticalDrag = { change, _ ->
                        val dy = change.position.y - startY
                        if (dy < -60f) onSwipeUp()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = date,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun PinScreen(viewModel: LockFlowViewModel) {
    val pin by viewModel.pinDigits.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 90.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.then(doubleTapModifier(onDoubleTap = viewModel::onPinAbortDoubleTap)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Enter PIN", color = Color.White.copy(alpha = 0.95f), fontSize = 20.sp)
                    PinDotsRow(pin.length)
                }
            }

            Box(modifier = Modifier.weight(1f))

            PinKeypad(onKey = viewModel::onPinKey)
        }
    }
}

@Composable
private fun PinDotsRow(filledCount: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(4) { i ->
            val filled = i < filledCount
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (filled) Color.White else Color.White.copy(alpha = 0.25f)
                    )
            )
        }
    }
}

@Composable
private fun PinKeypad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "empty", "0", "delete")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(keys) { key ->
            when (key) {
                "empty" -> Box(modifier = Modifier.aspectRatio(1f))
                "delete" -> Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onKey("delete") }) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                else -> Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onKey(key) }) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(key, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}
