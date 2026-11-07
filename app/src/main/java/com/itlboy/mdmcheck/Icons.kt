package com.itlboy.mdmcheck

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icon nét (giống mockup), vẽ từ đường dẫn SVG 24×24 — khỏi kéo cả thư viện icon. */
object Ic {
    private fun stroke(name: String, width: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val SHIELD = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"

    val check = stroke("check", 2.6f, "M20 6L9 17l-5-5")
    val x = stroke("x", 2.6f, "M18 6L6 18", "M6 6l12 12")
    val alert = stroke("alert", 2.6f, "M12 7v6", "M12 17h.01")
    val shield = stroke("shield", 2f, SHIELD)
    val shieldCheck = stroke("shieldCheck", 2f, SHIELD, "M9 12l2 2 4-4")
    val phone = stroke("phone", 2f, "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M11 18h2")
    val clock = stroke("clock", 2f, "M12 7v5l3 2", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z")
    val lock = stroke("lock", 2f, "M6 11h12v10H6z", "M8 11V7a4 4 0 0 1 8 0v4")
    val chevron = stroke("chevron", 2.2f, "M9 6l6 6-6 6")
    val back = stroke("back", 2.4f, "M15 6l-6 6 6 6")
    val info = stroke("info", 2f, "M12 2.5a9.5 9.5 0 1 0 0 19a9.5 9.5 0 1 0 0-19z", "M12 11v6", "M12 7.5h.01")
}
