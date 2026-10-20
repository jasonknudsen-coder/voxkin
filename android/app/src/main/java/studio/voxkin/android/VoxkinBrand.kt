package studio.voxkin.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Exact native palette and ribbon paths shared with App/VoxkinBrand.swift. */
object VoxkinBrand {
    val navy = Color(0.025f, 0.045f, 0.09f)
    val surface = Color(0.055f, 0.075f, 0.14f)
    val cyan = Color(0.04f, 0.82f, 0.94f)
    val blue = Color(0.12f, 0.36f, 1f)
    val purple = Color(0.49f, 0.20f, 1f)
    val magenta = Color(0.84f, 0.26f, 0.96f)
    val secondary = Color.White.copy(alpha = .62f)
    val gradient = Brush.linearGradient(listOf(cyan, blue, purple))
    val background = Brush.linearGradient(listOf(surface, navy, navy))
}

@Composable
fun VoxkinWordmark(modifier: Modifier = Modifier) {
    Row(modifier) {
        Text("Vox", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("kin", color = VoxkinBrand.magenta, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VoxkinMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val factor = minOf(size.width / 800f, size.height / 425f)
        translate((size.width - 800f * factor) / 2f, (size.height - 425f * factor) / 2f) {
            scale(factor, factor, Offset.Zero) {
                translate(-100f, -270f) {
                    val ribbon =
                        Path().apply {
                            moveTo(330f, 285f)
                            cubicTo(290f, 285f, 264f, 324f, 285f, 361f)
                            lineTo(435f, 634f)
                            cubicTo(471f, 699f, 549f, 699f, 588f, 633f)
                            lineTo(737f, 361f)
                            cubicTo(760f, 319f, 735f, 281f, 696f, 281f)
                            cubicTo(674f, 281f, 657f, 291f, 645f, 313f)
                            lineTo(513f, 547f)
                            lineTo(382f, 313f)
                            cubicTo(370f, 294f, 352f, 285f, 330f, 285f)
                            close()
                        }
                    val front =
                        Path().apply {
                            moveTo(330f, 285f)
                            cubicTo(290f, 285f, 264f, 324f, 285f, 361f)
                            lineTo(435f, 634f)
                            cubicTo(471f, 699f, 549f, 699f, 588f, 633f)
                            lineTo(613f, 588f)
                            cubicTo(576f, 633f, 541f, 597f, 513f, 547f)
                            lineTo(382f, 313f)
                            cubicTo(370f, 294f, 352f, 285f, 330f, 285f)
                            close()
                        }
                    drawPath(
                        ribbon,
                        Brush.linearGradient(
                            listOf(Color(0xffee67f7), Color(0xff7939ff), Color(0xff4932f5)),
                            Offset(700f, 280f),
                            Offset(500f, 680f),
                        ),
                    )
                    drawPath(
                        front,
                        Brush.linearGradient(
                            listOf(Color(0xff12d9ec), Color(0xff087eff), Color(0xff04cbea)),
                            Offset(330f, 285f),
                            Offset(530f, 685f),
                        ),
                    )
                    listOf(
                            Triple(110f, 448f, 104f),
                            Triple(175f, 410f, 180f),
                            Triple(783f, 410f, 180f),
                            Triple(848f, 448f, 104f),
                        )
                        .forEach { (x, y, h) ->
                            val colors =
                                if (x < 500f) listOf(Color(0xff12d9ec), Color(0xff087eff))
                                else listOf(Color(0xffee67f7), Color(0xff7939ff))
                            drawRoundRect(
                                Brush.linearGradient(colors, Offset(x, y), Offset(x, y + h)),
                                Offset(x, y),
                                Size(42f, h),
                                CornerRadius(21f),
                            )
                        }
                }
            }
        }
    }
}

enum class VoxkinSymbol {
    Wave,
    History,
    Settings,
    Mic,
    Keyboard,
    Stop,
    Close,
    Arrow,
    Bolt,
    Plus,
    Chevron,
    Refresh,
    Check,
    More,
    Play,
}

/** Small native line icons avoid platform emoji/font differences. */
@Composable
fun VoxkinIcon(
    symbol: VoxkinSymbol,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = VoxkinBrand.cyan,
) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        scale(unit, unit, Offset.Zero) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.9f) =
                drawLine(color, Offset(x1, y1), Offset(x2, y2), width, StrokeCap.Round)
            when (symbol) {
                VoxkinSymbol.Wave ->
                    listOf(4f to 5f, 8f to 14f, 12f to 20f, 16f to 14f, 20f to 5f).forEach { (x, h)
                        ->
                        line(x, 12 - h / 2, x, 12 + h / 2, 2.5f)
                    }
                VoxkinSymbol.Mic -> {
                    drawRoundRect(color, Offset(8f, 2f), Size(8f, 13f), CornerRadius(4f))
                    drawArc(
                        color,
                        0f,
                        180f,
                        false,
                        Offset(5f, 8f),
                        Size(14f, 12f),
                        style = Stroke(1.8f),
                    )
                    line(12f, 20f, 12f, 23f)
                    line(8f, 23f, 16f, 23f)
                }
                VoxkinSymbol.Stop ->
                    drawRoundRect(color, Offset(6f, 6f), Size(12f, 12f), CornerRadius(2f))
                VoxkinSymbol.Close -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                VoxkinSymbol.Arrow -> {
                    line(12f, 20f, 12f, 4f, 2.6f)
                    line(5f, 11f, 12f, 4f, 2.6f)
                    line(12f, 4f, 19f, 11f, 2.6f)
                }
                VoxkinSymbol.Keyboard -> {
                    drawRoundRect(
                        color,
                        Offset(2f, 5f),
                        Size(20f, 14f),
                        CornerRadius(2f),
                        style = Stroke(1.6f),
                    )
                    for (y in listOf(9f, 12f)) for (x in listOf(6f, 10f, 14f, 18f)) drawCircle(
                        color,
                        .8f,
                        Offset(x, y),
                    )
                    line(7f, 16f, 17f, 16f, 1.6f)
                }
                VoxkinSymbol.History -> {
                    drawArc(
                        color,
                        -80f,
                        300f,
                        false,
                        Offset(4f, 3f),
                        Size(17f, 17f),
                        style = Stroke(1.9f),
                    )
                    line(3f, 5f, 3f, 10f)
                    line(3f, 10f, 8f, 10f)
                    line(12f, 7f, 12f, 12f)
                    line(12f, 12f, 16f, 14f)
                }
                VoxkinSymbol.Settings -> {
                    for ((y, x) in listOf(5f to 8f, 12f to 16f, 19f to 10f)) {
                        line(3f, y, 21f, y, 1.7f)
                        drawCircle(VoxkinBrand.navy, 3f, Offset(x, y))
                        drawCircle(color, 2.5f, Offset(x, y), style = Stroke(1.6f))
                    }
                }
                VoxkinSymbol.Bolt -> {
                    val p =
                        Path().apply {
                            moveTo(14f, 2f)
                            lineTo(5f, 13f)
                            lineTo(11f, 13f)
                            lineTo(10f, 22f)
                            lineTo(19f, 10f)
                            lineTo(13f, 10f)
                            close()
                        }
                    drawPath(p, color)
                }
                VoxkinSymbol.Plus -> {
                    line(12f, 4f, 12f, 20f)
                    line(4f, 12f, 20f, 12f)
                }
                VoxkinSymbol.Chevron -> {
                    line(9f, 5f, 16f, 12f)
                    line(16f, 12f, 9f, 19f)
                }
                VoxkinSymbol.Refresh -> {
                    drawArc(
                        color,
                        -60f,
                        290f,
                        false,
                        Offset(4f, 4f),
                        Size(16f, 16f),
                        style = Stroke(1.9f),
                    )
                    line(19f, 3f, 20f, 9f)
                    line(20f, 9f, 14f, 8f)
                }
                VoxkinSymbol.Check -> {
                    line(4f, 12f, 9f, 17f)
                    line(9f, 17f, 20f, 6f)
                }
                VoxkinSymbol.More ->
                    for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f, Offset(x, 12f))
                VoxkinSymbol.Play ->
                    drawPath(
                        Path().apply {
                            moveTo(7f, 3f)
                            lineTo(21f, 12f)
                            lineTo(7f, 21f)
                            close()
                        },
                        color,
                    )
            }
        }
    }
}
