package cn.nebulae.timetable

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.PathParser

/** Original demo illustration geometry rendered using the Android canvas. */
@Composable
internal fun TrainIllustration(modifier: Modifier, side: Boolean = false) {
    Canvas(modifier.semantics { contentDescription = "列车插画" }) {
        drawIntoCanvas { target ->
            val canvas = target.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun color(hex: String) { paint.color = android.graphics.Color.parseColor(hex); paint.style = Paint.Style.FILL; paint.alpha = 255 }
            fun path(data: String, hex: String, stroke: Float = 0f, alpha: Float = 1f) {
                color(hex)
                paint.alpha = (255 * alpha).toInt()
                if (stroke > 0) { paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke }
                canvas.drawPath(PathParser.createPathFromPathData(data)!!, paint)
            }
            fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float, hex: String) {
                color(hex)
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
            }
            canvas.save()
            canvas.scale(size.width / if (side) 480f else 200f, size.height / if (side) 170f else 120f)
            if (side) {
                path("M0 145L480 145", "#ffffff", 15f, .4f)
                canvas.save()
                canvas.translate(13f, 12f)
                canvas.skew(-.21255656f, 0f)
                rect(0f, 28f, 410f, 109f, 38f, "#f7fcff")
                path("M30 45H380Q393 45 394 70V93H19V70Q19 45 30 45Z", "#537e9b")
                rect(51f, 50f, 87f, 38f, 8f, "#c2e9f5")
                rect(149f, 50f, 87f, 38f, 8f, "#c2e9f5")
                rect(248f, 50f, 114f, 38f, 8f, "#c2e9f5")
                path("M25 111H384", "#d71935", 12f)
                path("M55 132H365", "#6b7b90", 5f)
                canvas.restore()
            } else {
                path("M15 90L179 90M20 103L186 103", "#ffffff", 4f, .3f)
                path("M33 78Q33 24 73 22H152Q175 22 175 49V82Q175 94 163 94H47Q33 94 33 78Z", "#ffffff")
                rect(47f, 29f, 115f, 38f, 12f, "#3f6378")
                path("M58 34L104 34L87 64H58Z", "#94becb")
                path("M115 34H155V64H97Z", "#94becb")
                color("#fa6d74")
                canvas.drawCircle(57f, 77f, 6f, paint)
                canvas.drawCircle(154f, 77f, 6f, paint)
                path("M62 96L52 114M146 96L159 114", "#eeeeee", 5f)
            }
            canvas.restore()
        }
    }
}
