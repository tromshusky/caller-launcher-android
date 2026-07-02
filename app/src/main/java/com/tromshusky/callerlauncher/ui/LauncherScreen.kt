package com.tromshusky.callerlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LauncherScreen(state: LauncherState) {
    val listState = rememberLazyListState()

    // Keep the selected item fully visible when navigating with the arrow keys,
    // scrolling as little as possible instead of jumping it to the top.
    LaunchedEffect(state.selectedIndex, state.apps.size) {
        if (state.apps.isEmpty()) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == state.selectedIndex }
        if (item == null) {
            listState.animateScrollToItem(state.selectedIndex)
        } else {
            val delta = when {
                item.offset < layout.viewportStartOffset ->
                    item.offset - layout.viewportStartOffset
                item.offset + item.size > layout.viewportEndOffset ->
                    item.offset + item.size - layout.viewportEndOffset
                else -> 0
            }
            if (delta != 0) listState.animateScrollBy(delta.toFloat())
        }
    }

    // When the list is scrolled (e.g. by touch) so that the selection would leave the
    // screen, snap the selection back onto the centermost item.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling || true) {
                    val layout = listState.layoutInfo
                    val visible = layout.visibleItemsInfo
                    if (visible.isNotEmpty()) {
                        val viewportStart = layout.viewportStartOffset
                        val viewportEnd = layout.viewportEndOffset
                        val viewportCenter = (viewportStart + viewportEnd) / 2

                        // Find the visible item whose center is closest to the viewport center
                        val closest = visible.minByOrNull { item ->
                            val itemCenter = item.offset + item.size / 2
                            kotlin.math.abs(itemCenter - viewportCenter)
                        }

                        closest?.let {
                            val centerIndex = it.index
                            if (centerIndex != state.selectedIndex) {
                                state.selectIndex(centerIndex)
                            }
                        }
                    }
                }
            }
    }

    val dialing = state.dialedNumber.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        ClockHeader()

        if (dialing) {
            NumberField(state.dialedNumber)
        }

        Spacer(modifier = Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxHeightDp = this.maxHeight
            val density = LocalDensity.current

            // Set a fixed item height that matches AppRow's visual height (adjust if needed)
            val itemHeightDp: Dp = 64.dp

            // compute vertical padding so an item can be centered
            val verticalPaddingDp = (maxHeightDp / 2) - (itemHeightDp / 2)

            // convert to px for scroll offset usage
            val verticalPaddingPx = with(density) { verticalPaddingDp.roundToPx() }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = verticalPaddingDp)
            ) {
                itemsIndexed(state.apps) { index, app ->
                    AppRow(
                        app = app,
                        selected = index == state.selectedIndex && !dialing
                    )
                }
            }

            // Update the LaunchedEffect that scrolls to selectedIndex to use the padding offset
            LaunchedEffect(state.selectedIndex, state.apps.size) {
                if (state.apps.isEmpty()) return@LaunchedEffect
                val layout = listState.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == state.selectedIndex }
                if (item == null) {
                    // position the item at the same offset as the top padding so it appears centered
                    listState.animateScrollToItem(state.selectedIndex, scrollOffset = verticalPaddingPx)
                } else {
                    // keep the selected item fully visible but prefer centering when possible
                    val viewportStart = layout.viewportStartOffset
                    val viewportEnd = layout.viewportEndOffset
                    val viewportHeight = viewportEnd - viewportStart
                    val centerOffset = viewportHeight / 2 - item.size / 2

                    val delta = when {
                        item.offset < viewportStart ->
                            item.offset - viewportStart - centerOffset
                        item.offset + item.size > viewportEnd ->
                            item.offset + item.size - viewportEnd + centerOffset
                        else -> 0
                    }
                    if (delta != 0) listState.animateScrollBy(delta.toFloat())
                }
            }
        }

    }
}

@Composable
private fun ClockHeader() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE dd-MM-yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = timeFormat.format(now),
            fontSize = 76.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = dateFormat.format(now).replaceFirstChar { it.uppercase() },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun NumberField(number: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = number,
                fontSize = 30.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = "make call",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun AppRow(app: AppInfo, selected: Boolean) {
    val base = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)

    val framed = if (selected) {
        base
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            )
    } else {
        base
    }

    Box(modifier = framed.padding(horizontal = 12.dp, vertical = 12.dp)) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = app.label,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
