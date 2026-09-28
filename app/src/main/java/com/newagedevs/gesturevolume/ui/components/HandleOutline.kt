package com.newagedevs.gesturevolume.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.newagedevs.gesturevolume.data.local.SharedPref
import com.newagedevs.gesturevolume.utils.HandlerPresets
import com.newagedevs.gesturevolume.utils.HandlerShape

/**
 * The handle as it is drawn anywhere other than on the screen edge: the walkthrough's scenes and
 * the previews' demos.
 *
 * Cut from the same outline as the real bar — [HandlerShape.tabOutline], whose sweeps run into the
 * straight side without a crease — so a drawing of the handle is exactly as smooth as the one the
 * user has. Drawn by eye with a pair of cubics, the ends came out as hooks, with a kink where each
 * met the straight side, and the drawing was the ugliest handle in the app.
 */
@Immutable
data class HandleLook(
    /** [HandlerShape.ROUNDED] or [HandlerShape.TAB]. */
    val shape: String,
    val widthDp: Float,
    val heightDp: Float,
    val fill: Color,
    val stroke: Color,
    val strokeDp: Float,
    /** How far a tab's ends sweep, as a fraction of its height. */
    val flare: Float,
    /** A rounded bar's corners, in dp: top left, top right, bottom right, bottom left. */
    val cornersDp: List<Float>,
) {
    companion object {

        /** The handle the user has. */
        fun from(pref: SharedPref): HandleLook = HandleLook(
            shape = HandlerShape.sanitize(pref.getHandlerShape()),
            widthDp = pref.getHandlerWidthDp(),
            heightDp = pref.getHandlerHeightDp(),
            fill = Color(pref.getHandlerColor()).copy(alpha = pref.getHandlerBackgroundAlpha() / 255f),
            stroke = Color(pref.getHandlerStrokeColor()).copy(alpha = pref.getHandlerStrokeAlpha() / 255f),
            strokeDp = pref.getHandlerStrokeWidth(),
            flare = pref.getHandlerShapeFlare(),
            cornersDp = listOf(
                pref.getHandlerCornerRadiusTL(),
                pref.getHandlerCornerRadiusTR(),
                pref.getHandlerCornerRadiusBR(),
                pref.getHandlerCornerRadiusBL(),
            ),
        )

        /** The Dock, the advanced default, for a drawing with no settings to read. */
        val DOCK: HandleLook = HandlerPresets.DEFAULT.let { p ->
            HandleLook(
                shape = p.shape,
                widthDp = p.width,
                heightDp = p.height,
                fill = p.bgColor.copy(alpha = p.bgAlpha / 255f),
                stroke = p.strokeColor.copy(alpha = p.strokeAlpha / 255f),
                strokeDp = p.strokeWidth,
                flare = p.flare,
                cornersDp = listOf(p.topLeft, p.topRight, p.bottomRight, p.bottomLeft),
            )
        }
    }
}

/**
 * Makes this path a tab [width] deep and [height] tall whose straight side lies along [edgeX],
 * from [top] down: the real bar's outline, flattened finer than a pixel.
 */
fun Path.setTabOutline(edgeX: Float, top: Float, width: Float, height: Float, flare: Float, edgeOnLeft: Boolean) {
    rewind()
    val outline = HandlerShape.tabOutline(width, height, flare, edgeOnLeft)
    val left = if (edgeOnLeft) edgeX else edgeX - width
    moveTo(left + outline[0], top + outline[1])
    var i = 2
    while (i < outline.size) {
        lineTo(left + outline[i], top + outline[i + 1])
        i += 2
    }
    close()
}

/**
 * Draws [look] against the edge at [edgeX], centred on [centerY], [scale] times its real size.
 *
 * @param path scratch, so a drawing that runs every frame makes no path per frame.
 */
fun DrawScope.drawHandle(
    look: HandleLook,
    edgeX: Float,
    centerY: Float,
    edgeOnLeft: Boolean,
    path: Path,
    alpha: Float = 1f,
    scale: Float = 1f,
) {
    if (alpha <= 0f) return
    val w = look.widthDp * density * scale
    val h = look.heightDp * density * scale
    val top = centerY - h / 2f
    if (look.shape == HandlerShape.TAB) {
        path.setTabOutline(edgeX, top, w, h, look.flare, edgeOnLeft)
    } else {
        path.rewind()
        val left = if (edgeOnLeft) edgeX else edgeX - w
        // Never rounder than half the bar, which is a semicircle end; past that a corner folds.
        val cap = minOf(w, h) / 2f
        fun r(i: Int) = CornerRadius((look.cornersDp[i] * density * scale).coerceIn(0f, cap))
        path.addRoundRect(
            RoundRect(
                left = left, top = top, right = left + w, bottom = top + h,
                topLeftCornerRadius = r(0), topRightCornerRadius = r(1),
                bottomRightCornerRadius = r(2), bottomLeftCornerRadius = r(3),
            )
        )
    }
    drawPath(path, look.fill, alpha = alpha)
    if (look.strokeDp > 0f) {
        drawPath(path, look.stroke, alpha = alpha, style = Stroke(look.strokeDp * density * scale))
    }
}
