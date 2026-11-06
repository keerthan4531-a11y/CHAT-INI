package com.chatapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.HandSmallStyle
import com.chatapp.ui.theme.LocalPawPalColors
import kotlin.math.sin

/**
 * A deterministic, slightly irregular rounded rectangle. This tiny bit of
 * wobble is what makes cards look hand-drawn instead of Material-perfect.
 */
class WobblyShape(
    private val seed: Int = 0,
    private val radius: Dp = 18.dp
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = radius.toPx(density)
        val w = size.width
        val h = size.height
        val amp = 1.4f * density.density
        fun j(n: Int): Float = (sin((seed + n) * 12.9898) * 0.5 + 0.5) * 2f * amp - amp
        val path = Path().apply {
            moveTo(r + j(1), j(2))
            lineTo(w - r + j(3), j(4))
            cubicTo(w - r * 0.35f, j(5), w - j(6), r * 0.35f, w - j(7), r + j(8))
            lineTo(w - j(9), h - r + j(10))
            cubicTo(w - r * 0.4f, h - j(11), w - r * 0.7f, h - j(12), w - r + j(13), h - j(14))
            lineTo(r + j(15), h - j(16))
            cubicTo(r * 0.4f, h - j(17), j(18), h - r * 0.5f, j(19), h - r + j(20))
            lineTo(j(21), r + j(22))
            cubicTo(r * 0.35f, j(23), r * 0.7f, j(24), r + j(25), j(26))
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Sticker-style card: wobbly border, offset solid shadow, press squish.
 */
@Composable
fun WobblyCard(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    fill: Color = LocalPawPalColors.current.card,
    borderColor: Color = LocalPawPalColors.current.line,
    borderWidth: Dp = 2.dp,
    shadow: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalPawPalColors.current
    val shape = remember(seed) { WobblyShape(seed) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(120),
        label = "wobbly-press"
    )
    Box(modifier = modifier) {
        if (shadow) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = 3.dp, bottom = 3.dp)
                    .offset(x = 3.dp, y = 3.dp)
                    .clip(shape)
                    .background(colors.shadow.copy(alpha = if (colors.isDark) 0.55f else 0.9f))
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    enabled = onClick != null,
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onClick?.invoke() }
                ),
            shape = shape,
            color = fill
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A strip of "washi tape" for sticky notes and cards. */
@Composable
fun Tape(
    modifier: Modifier = Modifier,
    color: Color = LocalPawPalColors.current.butter,
    width: Dp = 46.dp,
    height: Dp = 15.dp,
    rotation: Float = -4f
) {
    Box(
        modifier
            .width(width)
            .height(height)
            .rotate(rotation)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.82f))
    )
}

/** Pastel sticky note with tape and a hand-written caption. */
@Composable
fun StickyNote(
    text: String,
    emoji: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: Float = -1.4f,
    seed: Int = 0
) {
    val colors = LocalPawPalColors.current
    WobblyCard(
        modifier = modifier.rotate(rotation),
        seed = seed,
        fill = background,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = HandSmallStyle,
                color = colors.ink,
                fontWeight = FontWeight.Medium
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Tape(width = 38.dp, height = 12.dp, rotation = 3f)
        }
    }
}

/** Chunky doodle button with an offset shadow and press squish. */
@Composable
fun DoodleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    background: Color = LocalPawPalColors.current.coral,
    contentColor: Color = LocalPawPalColors.current.ink,
    enabled: Boolean = true,
    seed: Int = 7
) {
    val colors = LocalPawPalColors.current
    val shape = remember(seed) { WobblyShape(seed, 14.dp) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(110),
        label = "button-press"
    )
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 3.dp, bottom = 3.dp)
                .offset(x = 3.dp, y = 3.dp)
                .clip(shape)
                .background(colors.shadow.copy(alpha = if (colors.isDark) 0.55f else 0.9f))
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(2.dp, colors.line, shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            shape = shape,
            color = if (enabled) background else colors.cardAlt
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (emoji != null) {
                    Text(text = emoji, fontSize = 17.sp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) contentColor else colors.inkMute
                )
            }
        }
    }
}

/** Round doodle icon button. */
@Composable
fun DoodleIconButton(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalPawPalColors.current.card,
    size: Dp = 40.dp,
    active: Boolean = false,
    contentDescription: String? = null
) {
    val colors = LocalPawPalColors.current
    val shape = remember { WobblyShape(3, size / 2.4f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(110),
        label = "icon-press"
    )
    Box(modifier = modifier.size(size).clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 2.dp, bottom = 2.dp)
                .offset(x = 2.dp, y = 2.dp)
                .clip(shape)
                .background(colors.shadow.copy(alpha = if (colors.isDark) 0.5f else 0.85f))
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(1.8.dp, colors.line, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            shape = shape,
            color = if (active) colors.butter else background
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = (size.value * 0.42f).sp)
            }
        }
    }
}

/** Small pastel pill used for tags and metadata. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = LocalPawPalColors.current.cardAlt,
    color: Color = LocalPawPalColors.current.inkSoft,
    emoji: String? = null,
    border: Boolean = true
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (border) Modifier.border(1.4.dp, colors.lineSoft, RoundedCornerShape(50))
                else Modifier
            )
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null) {
            Text(text = emoji, fontSize = 11.sp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Toggleable pill (Think / Search / Agent). */
@Composable
fun ToggleChip(
    label: String,
    emoji: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(
                if (active) 1.8.dp else 1.2.dp,
                if (active) colors.line else colors.lineSoft,
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (active) colors.mintSoft else colors.card
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 12.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) colors.ink else colors.inkMute
            )
        }
    }
}

/** Handwritten section title with a squiggly underline. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPawPalColors.current.ink
) {
    val colors = LocalPawPalColors.current
    Column(modifier = modifier) {
        Text(text = text, style = HandSmallStyle, color = color, fontSize = 22.sp)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .padding(top = 1.dp)
        ) {
            val w = size.width
            val path = Path().apply {
                moveTo(0f, size.height / 2)
                var x = 0f
                while (x < w) {
                    val next = (x + 14f).coerceAtMost(w)
                    cubicTo(
                        x + 4f, size.height * 0.15f,
                        next - 4f, size.height * 0.85f,
                        next, size.height / 2
                    )
                    x = next
                }
            }
            drawPath(path, color = colors.coral, style = Stroke(width = 2.4f))
        }
    }
}

/** Progress meter with a paw marker at the current position. */
@Composable
fun Meter(
    progress: Float,
    color: Color = LocalPawPalColors.current.coral,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    showPaw: Boolean = true,
    trackColor: Color = LocalPawPalColors.current.paperDeep
) {
    val colors = LocalPawPalColors.current
    val clamped = progress.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val width = maxWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(50))
                .border(1.4.dp, colors.lineSoft, RoundedCornerShape(50))
                .background(trackColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .height(height)
                    .background(color)
            )
        }
        if (showPaw) {
            Text(
                text = "🐾",
                fontSize = 11.sp,
                modifier = Modifier.offset(x = (width * clamped - 11.dp).coerceAtLeast(0.dp), y = -13.dp)
            )
        }
    }
}

/** Small status pill with a colored dot. */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (colors.isDark) 0.22f else 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Little twinkling sparkle. */
@Composable
fun Sparkle(
    modifier: Modifier = Modifier,
    color: Color = LocalPawPalColors.current.butter,
    size: Dp = 14.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val r = this.size.width / 2
        val path = Path().apply {
            moveTo(cx, cy - r)
            cubicTo(cx + r * 0.18f, cy - r * 0.18f, cx + r * 0.18f, cy - r * 0.18f, cx + r, cy)
            cubicTo(cx + r * 0.18f, cy + r * 0.18f, cx + r * 0.18f, cy + r * 0.18f, cx, cy + r)
            cubicTo(cx - r * 0.18f, cy + r * 0.18f, cx - r * 0.18f, cy + r * 0.18f, cx - r, cy)
            cubicTo(cx - r * 0.18f, cy - r * 0.18f, cx - r * 0.18f, cy - r * 0.18f, cx, cy - r)
            close()
        }
        drawPath(path, color = color)
    }
}

/** Wavy hand-drawn divider. */
@Composable
fun SquiggleDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalPawPalColors.current.lineSoft
) {
    Canvas(modifier = modifier.fillMaxWidth().height(8.dp)) {
        val w = size.width
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            var x = 0f
            while (x < w) {
                val next = (x + 18f).coerceAtMost(w)
                cubicTo(x + 5f, 0f, next - 5f, size.height, next, size.height / 2)
                x = next
            }
        }
        drawPath(path, color = color, style = Stroke(width = 1.6f))
    }
}

/** Doodle arrow used next to the composer. */
@Composable
fun DoodleArrow(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = HandSmallStyle, color = colors.inkMute, fontSize = 18.sp)
        Canvas(Modifier.size(54.dp, 34.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.08f, h * 0.2f)
                cubicTo(w * 0.45f, h * 0.05f, w * 0.8f, h * 0.3f, w * 0.86f, h * 0.72f)
                moveTo(w * 0.72f, h * 0.62f)
                lineTo(w * 0.86f, h * 0.72f)
                lineTo(w * 0.9f, h * 0.52f)
            }
            drawPath(path, color = colors.inkMute, style = Stroke(width = 2.2f))
        }
    }
}
