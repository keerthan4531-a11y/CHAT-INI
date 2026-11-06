package com.chatapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chatapp.ui.theme.LocalPawPalColors
import kotlin.math.cos
import kotlin.math.sin

enum class CatMood {
    IDLE,
    THINKING,
    SEARCHING,
    TOOL,
    TYPING,
    HAPPY,
    SAD,
    SLEEPING
}

enum class MascotKind { CAT, DOG }

/**
 * Mochi, the hand-drawn cat mascot. Every AI action has a mood:
 * thinking, searching, using a tool, typing, happy, sad, sleepy.
 *
 * The whole drawing is a single Canvas so it stays crisp at any size and can
 * be dropped into an avatar, an empty state or a celebration overlay.
 */
@Composable
fun DoodleCat(
    mood: CatMood = CatMood.IDLE,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    size: Dp = 220.dp
) {
    val colors = LocalPawPalColors.current
    val motion = rememberCatMotion(animate)

    Canvas(modifier = modifier.size(size)) {
        val scale = kotlin.math.min(this.size.width, this.size.height) / 260f
        val dx = (this.size.width - 260f * scale) / 2f
        val dy = (this.size.height - 260f * scale) / 2f
        translate(dx, dy) {
            scale(scale, scale) {
                drawMochi(colors, mood, motion)
            }
        }
    }
}

private data class CatMotion(
    val tail: Float,
    val breathe: Float,
    val blink: Float,
    earTwitch: Float,
    val float: Float,
    val typing: Float,
    val bounce: Float
)

@Composable
private fun rememberCatMotion(animate: Boolean): CatMotion {
    if (!animate) {
        return remember { CatMotion(0f, 0f, 0f, 0f, 0f, 0f, 0f) }
    }
    val transition = rememberInfiniteTransition(label = "mochi")
    val tail by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail"
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5400
                0f at 0
                0f at 4700
                1f at 4840
                0f at 5000
                0f at 5400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val earTwitch by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ear"
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
    val typing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(190, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "typing"
    )
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(430, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )
    return CatMotion(tail, breathe, blink, earTwitch, float, typing, bounce)
}

private fun DrawScope.drawMochi(
    colors: com.chatapp.ui.theme.PawPalColors,
    mood: CatMood,
    motion: CatMotion
) {
    val ink = colors.catOutline
    val fur = colors.catFur
    val shade = colors.catFurShade
    val pink = colors.catPink
    val white = Color.White
    val coral = colors.coral
    val butter = colors.butter
    val mint = colors.mint
    val sky = colors.sky
    val card = colors.card

    val tailAngle = -7f + motion.tail * 14f
    val breatheScale = 1f + motion.breathe * 0.012f
    val happy = mood == CatMood.HAPPY
    val sad = mood == CatMood.SAD
    val sleepy = mood == CatMood.SLEEPING
    val bounceY = if (happy) -motion.bounce * 5f else 0f

    // ---- tail (behind everything) ----
    rotate(tailAngle, pivot = Offset(188f, 205f)) {
        val tailPath = Path().apply {
            moveTo(186f, 206f)
            cubicTo(232f, 212f, 248f, 176f, 224f, 154f)
            cubicTo(212f, 143f, 196f, 151f, 203f, 164f)
        }
        drawPath(tailPath, color = ink, style = Stroke(width = 17f, cap = StrokeCap.Round))
        drawPath(tailPath, color = fur, style = Stroke(width = 11f, cap = StrokeCap.Round))
        drawLine(ink, Offset(224f, 170f), Offset(232f, 176f), 3f, cap = StrokeCap.Round)
        drawLine(ink, Offset(216f, 186f), Offset(226f, 190f), 3f, cap = StrokeCap.Round)
    }

    // ---- ears (behind the head) ----
    val earDroop = if (sad) 16f else motion.earTwitch * 2.5f
    rotate(-earDroop, pivot = Offset(82f, 74f)) {
        drawEar(ink, fur, pink, left = true)
    }
    rotate(earDroop, pivot = Offset(178f, 74f)) {
        drawEar(ink, fur, pink, left = false)
    }

    // ---- body ----
    val bodyPath = Path().apply {
        moveTo(82f, 214f)
        cubicTo(68f, 186f, 82f, 152f, 130f, 152f)
        cubicTo(178f, 152f, 192f, 186f, 178f, 214f)
        cubicTo(160f, 227f, 100f, 227f, 82f, 214f)
        close()
    }
    translate(0f, bounceY) {
        scale(1f, breatheScale, pivot = Offset(130f, 226f)) {
            drawPath(bodyPath, color = fur)
            drawPath(bodyPath, color = ink, style = Stroke(width = 4.2f))
            drawOval(Rect(102f, 172f, 158f, 218f), color = white.copy(alpha = 0.45f))
            // side stripes
            drawLine(ink.copy(alpha = 0.55f), Offset(88f, 186f), Offset(96f, 190f), 3f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.55f), Offset(86f, 198f), Offset(95f, 200f), 3f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.55f), Offset(172f, 186f), Offset(164f, 190f), 3f, cap = StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.55f), Offset(174f, 198f), Offset(165f, 200f), 3f, cap = StrokeCap.Round)
        }

        // ---- paws / laptop / thinking paw ----
        when (mood) {
            CatMood.TOOL -> {
                drawLaptop(ink, mint, card, motion.typing)
            }

            CatMood.THINKING -> {
                drawPaw(ink, fur, 106f, 214f)
                drawPaw(ink, fur, 154f, 214f)
                rotate(-24f, pivot = Offset(96f, 196f)) {
                    drawOval(Rect(80f, 140f, 112f, 170f), color = fur)
                    drawOval(Rect(80f, 140f, 112f, 170f), color = ink, style = Stroke(3.4f))
                    drawLine(ink, Offset(88f, 150f), Offset(92f, 158f), 2.2f, cap = StrokeCap.Round)
                    drawLine(ink, Offset(96f, 148f), Offset(99f, 156f), 2.2f, cap = StrokeCap.Round)
                }
            }

            else -> {
                drawPaw(ink, fur, 106f, 214f)
                drawPaw(ink, fur, 154f, 214f)
            }
        }

        // ---- head ----
        drawOval(Rect(68f, 53f, 192f, 163f), color = fur)
        drawOval(Rect(68f, 53f, 192f, 163f), color = ink, style = Stroke(4.5f))

        // forehead stripes
        drawLine(ink, Offset(118f, 58f), Offset(121f, 70f), 3.4f, cap = StrokeCap.Round)
        drawLine(ink, Offset(130f, 55f), Offset(130f, 68f), 3.4f, cap = StrokeCap.Round)
        drawLine(ink, Offset(142f, 58f), Offset(139f, 70f), 3.4f, cap = StrokeCap.Round)
        // cheek stripes
        drawLine(ink.copy(alpha = 0.6f), Offset(74f, 112f), Offset(88f, 116f), 2.6f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.6f), Offset(74f, 122f), Offset(88f, 124f), 2.6f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.6f), Offset(186f, 112f), Offset(172f, 116f), 2.6f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.6f), Offset(186f, 122f), Offset(172f, 124f), 2.6f, cap = StrokeCap.Round)

        // cheeks
        drawCircle(pink.copy(alpha = 0.5f), 9f, Offset(94f, 126f))
        drawCircle(pink.copy(alpha = 0.5f), 9f, Offset(166f, 126f))

        // eyes
        if (happy) {
            drawPath(
                Path().apply {
                    moveTo(98f, 108f)
                    cubicTo(102f, 98f, 112f, 98f, 116f, 108f)
                },
                color = ink,
                style = Stroke(width = 4.2f, cap = StrokeCap.Round)
            )
            drawPath(
                Path().apply {
                    moveTo(144f, 108f)
                    cubicTo(148f, 98f, 158f, 98f, 162f, 108f)
                },
                color = ink,
                style = Stroke(width = 4.2f, cap = StrokeCap.Round)
            )
        } else if (sleepy || motion.blink > 0.55f) {
            drawArc(ink, Rect(98f, 100f, 118f, 112f), 0f, 180f, false, Stroke(3.6f, cap = StrokeCap.Round))
            drawArc(ink, Rect(142f, 100f, 162f, 112f), 0f, 180f, false, Stroke(3.6f, cap = StrokeCap.Round))
        } else {
            drawOval(Rect(101f, 95f, 115f, 114f), color = ink)
            drawOval(Rect(145f, 95f, 159f, 114f), color = ink)
            drawCircle(white, 2.6f, Offset(105f, 100f))
            drawCircle(white, 2.6f, Offset(149f, 100f))
            if (sad) {
                drawLine(ink, Offset(98f, 88f), Offset(116f, 94f), 3f, cap = StrokeCap.Round)
                drawLine(ink, Offset(162f, 88f), Offset(144f, 94f), 3f, cap = StrokeCap.Round)
                drawOval(sky.copy(alpha = 0.85f), Rect(154f, 116f, 162f, 128f))
            }
        }

        // nose + mouth
        val nose = Path().apply {
            moveTo(124f, 118f)
            lineTo(136f, 118f)
            lineTo(130f, 126f)
            close()
        }
        drawPath(nose, color = pink)
        drawPath(nose, color = ink, style = Stroke(2.4f))
        drawPath(
            Path().apply {
                moveTo(130f, 126f)
                cubicTo(130f, 133f, 123f, 135f, 119f, 130f)
            },
            color = ink,
            style = Stroke(2.6f, cap = StrokeCap.Round)
        )
        drawPath(
            Path().apply {
                moveTo(130f, 126f)
                cubicTo(130f, 133f, 137f, 135f, 141f, 130f)
            },
            color = ink,
            style = Stroke(2.6f, cap = StrokeCap.Round)
        )

        // whiskers
        drawLine(ink.copy(alpha = 0.7f), Offset(86f, 112f), Offset(56f, 106f), 2.2f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.7f), Offset(86f, 120f), Offset(54f, 120f), 2.2f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.7f), Offset(86f, 128f), Offset(58f, 134f), 2.2f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.7f), Offset(174f, 112f), Offset(204f, 106f), 2.2f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.7f), Offset(174f, 120f), Offset(206f, 120f), 2.2f, cap = StrokeCap.Round)
        drawLine(ink.copy(alpha = 0.7f), Offset(174f, 128f), Offset(202f, 134f), 2.2f, cap = StrokeCap.Round)

        // collar + bell
        drawArc(coral, Rect(98f, 140f, 162f, 172f), 12f, 156f, false, Stroke(8f, cap = StrokeCap.Round))
        drawCircle(butter, 8f, Offset(130f, 170f))
        drawCircle(ink, 8f, Offset(130f, 170f), style = Stroke(2.6f))
        drawLine(ink, Offset(130f, 163f), Offset(130f, 166f), 2f, cap = StrokeCap.Round)
    }

    // ---- mood overlays (outside the breathing transform) ----
    when (mood) {
        CatMood.THINKING -> {
            drawCircle(card, 6f, Offset(62f, 82f), style = Stroke(2.2f))
            drawCircle(card, 9f, Offset(47f, 64f), style = Stroke(2.4f))
            drawCircle(card, 14f, Offset(29f, 40f), style = Stroke(2.6f))
            drawText("?", 24f, 47f, ink, 26f)
            drawText("?", 42f, 70f, ink, 18f)
        }

        CatMood.SEARCHING -> {
            rotate(-8f + motion.float * 16f, pivot = Offset(196f, 84f)) {
                drawCircle(sky.copy(alpha = 0.35f), 20f, Offset(196f, 84f))
                drawCircle(ink, 20f, Offset(196f, 84f), style = Stroke(6f))
                drawLine(ink, Offset(210f, 98f), Offset(228f, 118f), 8f, cap = StrokeCap.Round)
            }
            drawSparkle(butter, 232f, 58f, 7f)
        }

        CatMood.TOOL -> {
            rotate(motion.float * 40f, pivot = Offset(214f, 150f)) {
                drawCircle(card, 11f, Offset(214f, 150f), style = Stroke(3f))
                drawCircle(butter, 4f, Offset(214f, 150f))
                for (i in 0 until 6) {
                    val angle = (Math.PI * 2 * i / 6).toFloat()
                    drawLine(
                        ink,
                        Offset(214f + cos(angle) * 11f, 150f + sin(angle) * 11f),
                        Offset(214f + cos(angle) * 16f, 150f + sin(angle) * 16f),
                        3f,
                        cap = StrokeCap.Round
                    )
                }
            }
            drawSparkle(mint, 60f, 150f, 6f)
        }

        CatMood.TYPING -> {
            drawSparkle(butter, 62f, 120f, 7f)
            drawSparkle(coral, 210f, 110f, 6f)
            drawSparkle(mint, 196f, 60f, 5f)
        }

        CatMood.HAPPY -> {
            drawHeart(coral, 206f, 74f - motion.float * 6f, 11f)
            drawHeart(roseOrPink(colors), 54f, 92f - motion.float * 8f, 8f)
            drawHeart(butter, 224f, 120f - motion.float * 5f, 6f)
            drawSparkle(butter, 78f, 52f, 6f)
            drawSparkle(mint, 190f, 40f, 5f)
        }

        CatMood.SAD -> {
            drawCloud(sky.copy(alpha = 0.9f), ink, 196f, 46f, motion.float)
            drawText("z", 0f, 0f, Color.Transparent, 1f)
        }

        CatMood.SLEEPING -> {
            drawText("z", 196f, 74f - motion.float * 6f, ink, 22f)
            drawText("z", 212f, 56f - motion.float * 9f, ink, 17f)
            drawText("z", 224f, 40f - motion.float * 12f, ink, 13f)
        }

        else -> {
            if (mood == CatMood.IDLE) {
                drawSparkle(butter, 226f, 66f, 5f)
            }
        }
    }
}

private fun DrawScope.drawEar(ink: Color, fur: Color, pink: Color, left: Boolean) {
    val outer = if (left) {
        Path().apply {
            moveTo(80f, 74f)
            lineTo(66f, 28f)
            lineTo(112f, 50f)
            close()
        }
    } else {
        Path().apply {
            moveTo(180f, 74f)
            lineTo(194f, 28f)
            lineTo(148f, 50f)
            close()
        }
    }
    val inner = if (left) {
        Path().apply {
            moveTo(82f, 66f)
            lineTo(74f, 40f)
            lineTo(100f, 52f)
            close()
        }
    } else {
        Path().apply {
            moveTo(178f, 66f)
            lineTo(186f, 40f)
            lineTo(160f, 52f)
            close()
        }
    }
    drawPath(outer, color = fur)
    drawPath(outer, color = ink, style = Stroke(4.4f))
    drawPath(inner, color = pink)
}

private fun DrawScope.drawPaw(ink: Color, fur: Color, cx: Float, cy: Float) {
    drawOval(Rect(cx - 17f, cy - 11f, cx + 17f, cy + 11f), color = fur)
    drawOval(Rect(cx - 17f, cy - 11f, cx + 17f, cy + 11f), color = ink, style = Stroke(3.2f))
    drawLine(ink, Offset(cx - 5f, cy - 6f), Offset(cx - 5f, cy + 2f), 2f, cap = StrokeCap.Round)
    drawLine(ink, Offset(cx + 5f, cy - 6f), Offset(cx + 5f, cy + 2f), 2f, cap = StrokeCap.Round)
}

private fun DrawScope.drawLaptop(ink: Color, screen: Color, card: Color, typing: Float) {
    drawOval(Rect(84f, 172f, 176f, 218f), color = ink)
    drawOval(Rect(90f, 178f, 170f, 210f), color = screen)
    drawOval(Rect(74f, 214f, 186f, 226f), color = ink)
    drawOval(Rect(80f, 216f, 180f, 222f), color = card.copy(alpha = 0.35f))
    // little code lines on the screen
    drawLine(card, Offset(100f, 190f), Offset(132f, 190f), 2.4f, cap = StrokeCap.Round)
    drawLine(card, Offset(100f, 198f), Offset(146f, 198f), 2.4f, cap = StrokeCap.Round)
    drawLine(card, Offset(100f, 206f), Offset(122f, 206f), 2.4f, cap = StrokeCap.Round)
    // typing paws
    val offset = typing * 3f
    drawOval(Rect(100f, 206f - offset, 122f, 218f - offset), color = card)
    drawOval(Rect(100f, 206f - offset, 122f, 218f - offset), color = ink, style = Stroke(2.4f))
    drawOval(Rect(138f, 206f + offset, 160f, 218f + offset), color = card)
    drawOval(Rect(138f, 206f + offset, 160f, 218f + offset), color = ink, style = Stroke(2.4f))
}

private fun DrawScope.drawHeart(color: Color, cx: Float, cy: Float, s: Float) {
    val path = Path().apply {
        moveTo(cx, cy + s * 0.75f)
        cubicTo(cx - s * 1.4f, cy - s * 0.2f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.45f)
        cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.4f, cy - s * 0.2f, cx, cy + s * 0.75f)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawSparkle(color: Color, cx: Float, cy: Float, r: Float) {
    val path = Path().apply {
        moveTo(cx, cy - r)
        cubicTo(cx + r * 0.2f, cy - r * 0.2f, cx + r * 0.2f, cy - r * 0.2f, cx + r, cy)
        cubicTo(cx + r * 0.2f, cy + r * 0.2f, cx + r * 0.2f, cy + r * 0.2f, cx, cy + r)
        cubicTo(cx - r * 0.2f, cy + r * 0.2f, cx - r * 0.2f, cy + r * 0.2f, cx - r, cy)
        cubicTo(cx - r * 0.2f, cy - r * 0.2f, cx - r * 0.2f, cy - r * 0.2f, cx, cy - r)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawCloud(color: Color, ink: Color, cx: Float, cy: Float, float: Float) {
    val dy = float * 3f
    drawCircle(color, 13f, Offset(cx - 12f, cy + dy))
    drawCircle(color, 17f, Offset(cx + 4f, cy - 6f + dy))
    drawCircle(color, 12f, Offset(cx + 20f, cy + dy))
    drawOval(Rect(cx - 24f, cy + dy, cx + 32f, cy + 14f + dy), color = color)
    drawLine(ink.copy(alpha = 0.5f), Offset(cx - 14f, cy + 20f + dy), Offset(cx - 16f, cy + 30f + dy), 2.4f, cap = StrokeCap.Round)
    drawLine(ink.copy(alpha = 0.5f), Offset(cx + 2f, cy + 20f + dy), Offset(cx, cy + 32f + dy), 2.4f, cap = StrokeCap.Round)
    drawLine(ink.copy(alpha = 0.5f), Offset(cx + 18f, cy + 20f + dy), Offset(cx + 16f, cy + 28f + dy), 2.4f, cap = StrokeCap.Round)
}

private fun roseOrPink(colors: com.chatapp.ui.theme.PawPalColors): Color = colors.rose

private fun DrawScope.drawText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    size: Float
) {
    if (size <= 1f) return
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = color.toArgb()
            textSize = size
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}

/**
 * Biscuit, the dog doodle. Same visual language as Mochi: ink outline, cream
 * fur, floppy ears and a very good tongue.
 */
@Composable
fun DoodleDog(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    size: Dp = 180.dp,
    happy: Boolean = true
) {
    val colors = LocalPawPalColors.current
    val transition = rememberInfiniteTransition(label = "biscuit")
    val tail by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dog-tail"
    )
    val float by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dog-float"
    )
    val ink = colors.catOutline
    val fur = colors.catFur
    val brown = colors.butter
    val pink = colors.catPink
    val wobble = if (animate) tail else 0f

    Canvas(modifier = modifier.size(size)) {
        val scale = kotlin.math.min(this.size.width, this.size.height) / 260f
        translate((this.size.width - 260f * scale) / 2f, (this.size.height - 260f * scale) / 2f) {
            scale(scale, scale) {
                // tail
                rotate(-10f + wobble * 20f, pivot = Offset(190f, 200f)) {
                    drawPath(
                        Path().apply {
                            moveTo(188f, 200f)
                            cubicTo(222f, 196f, 230f, 170f, 214f, 160f)
                        },
                        color = ink,
                        style = Stroke(15f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        Path().apply {
                            moveTo(188f, 200f)
                            cubicTo(222f, 196f, 230f, 170f, 214f, 160f)
                        },
                        color = fur,
                        style = Stroke(9f, cap = StrokeCap.Round)
                    )
                }
                // ears
                drawOval(Rect(52f, 66f, 100f, 148f), color = brown)
                drawOval(Rect(52f, 66f, 100f, 148f), color = ink, style = Stroke(4f))
                drawOval(Rect(160f, 66f, 208f, 148f), color = brown)
                drawOval(Rect(160f, 66f, 208f, 148f), color = ink, style = Stroke(4f))
                // body
                drawOval(Rect(76f, 142f, 184f, 224f), color = fur)
                drawOval(Rect(76f, 142f, 184f, 224f), color = ink, style = Stroke(4f))
                // head
                drawOval(Rect(68f, 56f, 192f, 172f), color = fur)
                drawOval(Rect(68f, 56f, 192f, 172f), color = ink, style = Stroke(4.5f))
                // eye patch
                drawCircle(brown.copy(alpha = 0.8f), 22f, Offset(158f, 92f))
                // eyes
                drawOval(Rect(98f, 96f, 114f, 114f), color = ink)
                drawOval(Rect(146f, 96f, 162f, 114f), color = ink)
                drawCircle(Color.White, 2.6f, Offset(102f, 101f))
                drawCircle(Color.White, 2.6f, Offset(150f, 101f))
                // snout
                drawOval(Rect(104f, 118f, 156f, 152f), color = colors.card)
                drawOval(Rect(104f, 118f, 156f, 152f), color = ink, style = Stroke(2.6f))
                drawOval(Rect(122f, 122f, 138f, 134f), color = ink)
                drawPath(
                    Path().apply {
                        moveTo(130f, 134f)
                        cubicTo(130f, 142f, 122f, 144f, 118f, 139f)
                    },
                    color = ink,
                    style = Stroke(2.4f, cap = StrokeCap.Round)
                )
                drawPath(
                    Path().apply {
                        moveTo(130f, 134f)
                        cubicTo(130f, 142f, 138f, 144f, 142f, 139f)
                    },
                    color = ink,
                    style = Stroke(2.4f, cap = StrokeCap.Round)
                )
                if (happy) {
                    drawOval(Rect(122f, 140f, 138f, 158f), color = pink)
                    drawLine(pink, Offset(130f, 140f), Offset(130f, 150f), 1.6f)
                }
                // collar
                drawArc(colors.coral, Rect(96f, 150f, 164f, 178f), 12f, 156f, false, Stroke(7f, cap = StrokeCap.Round))
                drawCircle(colors.butter, 7f, Offset(130f, 176f))
                drawCircle(ink, 7f, Offset(130f, 176f), style = Stroke(2.2f))
                if (happy) {
                    drawHeart(colors.coral, 206f, 74f - float * 6f, 10f)
                    drawSparkle(colors.butter, 60f, 60f, 6f)
                }
            }
        }
    }
}

/** Small round mascot avatar for message rows and the drawer header. */
@Composable
fun MascotAvatar(
    kind: MascotKind,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    mood: CatMood = CatMood.HAPPY
) {
    val colors = LocalPawPalColors.current
    val shape = remember { WobblyShape(11, size / 2.2f) }
    Box(modifier = modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 2.dp, bottom = 2.dp)
                .offset(x = 2.dp, y = 2.dp)
                .clip(shape)
                .background(colors.shadow.copy(alpha = if (colors.isDark) 0.5f else 0.85f))
        )
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.6.dp, colors.line, shape),
            shape = shape,
            color = colors.butterSoft
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (kind == MascotKind.CAT) {
                    DoodleCat(mood = mood, size = size, animate = false)
                } else {
                    DoodleDog(size = size, animate = false)
                }
            }
        }
    }
}
