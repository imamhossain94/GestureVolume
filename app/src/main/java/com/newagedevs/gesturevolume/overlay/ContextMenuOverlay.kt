package com.newagedevs.gesturevolume.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newagedevs.gesturevolume.ui.components.ActionIconImage
import com.newagedevs.gesturevolume.ui.screens.handler_appearance.AppearanceMotion
import com.newagedevs.gesturevolume.utils.HandlerActionCatalog

/**
 * The long-press menu, drawn beside the bar.
 *
 * Placement is decided the way the View-based menu decided it: the card opens on whichever side of
 * the bar has room, preferring the side away from the nearer screen edge, and sits centred on the
 * bar vertically before being pulled back inside the frame. All of it happens in the layout pass,
 * when the card's real size is known, so a tall menu on a bar dragged low is never cut off.
 *
 * The full-screen root is what catches the tap outside the card that dismisses it: the window is
 * not focusable, so there is no back-button route and an outside tap is the only way out.
 *
 * @param anchor the bar's window rectangle, in pixels relative to the usable frame.
 * @param frame the usable frame, in pixels.
 */
@Composable
fun ContextMenuOverlay(
    entries: List<HandlerActionCatalog.Entry>,
    anchor: IntRect,
    frame: IntSize,
    onSelect: (HandlerActionCatalog.Entry) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = AppearanceMotion.Pop,
        label = "menuScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = AppearanceMotion.Fade,
        label = "menuAlpha"
    )

    // Which side the card opened on, for the transform origin. Decided in layout, read in draw.
    var openedRight by remember { mutableStateOf(true) }
    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val cardW = placeable.width
                    val cardH = placeable.height
                    val spaceRight = frame.width - anchor.right
                    val spaceLeft = anchor.left
                    val needed = cardW + gapPx * 2
                    val right = when {
                        spaceRight >= needed && spaceLeft >= needed ->
                            (anchor.left + anchor.width / 2) < frame.width / 2
                        spaceRight >= needed -> true
                        spaceLeft >= needed -> false
                        else -> spaceRight >= spaceLeft
                    }
                    openedRight = right
                    val x = (if (right) anchor.right + gapPx else anchor.left - cardW - gapPx)
                        .coerceIn(gapPx, (frame.width - cardW - gapPx).coerceAtLeast(gapPx))
                    val y = (anchor.top + anchor.height / 2 - cardH / 2)
                        .coerceIn(gapPx, (frame.height - cardH - gapPx).coerceAtLeast(gapPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(x, y)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(if (openedRight) 0f else 1f, 0.5f)
                }
                .widthIn(min = 160.dp, max = 260.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MENU_SURFACE)
                .border(1.dp, MENU_STROKE, RoundedCornerShape(18.dp))
                // Swallows the tap so the root's dismiss does not fire for a press on the card.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            entries.forEach { entry ->
                val label = stringResource(entry.labelRes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(entry) }
                        .padding(start = 14.dp, top = 12.dp, end = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionIconImage(
                        icon = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MENU_ON_SURFACE
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = label,
                        color = MENU_ON_SURFACE,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Menu chrome. Fixed dark surface: the overlay has no theme of its own to follow. */
private val MENU_SURFACE = Color(0xF71E1E22)
private val MENU_STROKE = Color(0x26FFFFFF)
private val MENU_ON_SURFACE = Color(0xF0FFFFFF)
