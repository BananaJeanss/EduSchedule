package dev.bananajeans.eduschedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Small original line icons. No obsolete material-icons-extended dependency.
@Composable fun Glyph(name: String, description: String? = null) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { if (description != null) contentDescription = description }) {
        val scale = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * scale,y * scale),Offset(x2 * scale,y2 * scale), 1.8f * scale, StrokeCap.Round)
        fun box(x: Float,y: Float,w: Float,h: Float) = drawRect(color,Offset(x * scale,y * scale),Size(w * scale,h * scale),style=Stroke(1.8f * scale))
        when(name) {
            "back" -> { line(15f,5f,8f,12f); line(8f,12f,15f,19f) }
            "next" -> { line(9f,5f,16f,12f); line(16f,12f,9f,19f) }
            "more" -> listOf(5f,12f,19f).forEach { drawCircle(color,1.7f * scale,Offset(12f * scale,it * scale)) }
            "day" -> { box(4f,4f,16f,16f); line(4f,9f,20f,9f); line(8f,13f,16f,13f); line(8f,17f,13f,17f) }
            "week" -> { box(3f,5f,18f,15f); line(3f,10f,21f,10f); line(9f,10f,9f,20f); line(15f,10f,15f,20f) }
            "browse" -> { drawCircle(color,6f * scale,Offset(10f * scale,10f * scale),style=Stroke(1.8f * scale)); line(15f,15f,21f,21f) }
            "refresh" -> { drawArc(color,45f,285f,false,Offset(4f * scale,4f * scale),Size(16f * scale,16f * scale),style=Stroke(1.8f * scale)); line(20f,4f,20f,10f); line(20f,10f,14f,10f) }
            "home" -> { line(3f,11f,12f,3f); line(12f,3f,21f,11f); line(6f,9f,6f,21f); line(18f,9f,18f,21f); line(6f,21f,18f,21f) }
            "check" -> { line(4f,12f,10f,18f); line(10f,18f,20f,6f) }
            else -> { box(4f,4f,16f,16f); line(8f,9f,16f,9f); line(8f,15f,16f,15f) }
        }
    }
}
