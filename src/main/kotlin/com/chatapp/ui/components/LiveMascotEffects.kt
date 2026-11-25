package com.chatapp.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chatapp.ui.theme.HandSmallStyle
import com.chatapp.ui.theme.LocalPawPalColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Reads gentle phone tilt from the device accelerometer for subtle 2.5D/3D
 * parallax on the mascot. Gracefully returns (0f, 0f) if unavailable.
 */
@Composable
fun rememberTiltParallax(): Pair<Float, Float> {
    val context = LocalContext.current
    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || accel == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nx = (-event.values[0] / 6f).coerceIn(-1f, 1f)
                val ny = ((event.values[1] - 4.5f) / 6f).coerceIn(-1f, 1f)
                rawX = rawX * 0.82f + nx * 0.18f
                rawY = rawY * 0.82f + ny * 0.18f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            manager.unregisterListener(listener)
        }
    }

    val smoothX by animateFloatAsState(
        targetValue = rawX,
        animationSpec = tween(180),
        label = "tiltX"
    )
    val smoothY by animateFloatAsState(
        targetValue = rawY,
        animationSpec = tween(180),
        label = "tiltY"
    )
    return smoothX to smoothY
}

/**
 * Courier Pet Delivery Animation:
 * Phase 1 (sending/thinking): Pet trots left -> right holding a sealed envelope (✉️) in its mouth to the AI server cloud!
 * Phase 2 (streaming reply): Pet trots right -> left holding a glowing reply scroll back to the user!
 */
@Composable
fun CourierPetBanner(
    isGenerating: Boolean,
    hasContentStarted: Boolean,
    isSearching: Boolean,
    mascot: MascotKind,
    assistantName: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current

    AnimatedVisibility(
        visible = isGenerating,
        enter = fadeIn(tween(200)) + expandVertically(),
        exit = fadeOut(tween(220)) + shrinkVertically(),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "courier")
        val trotProgress by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "trot"
        )
        val hopPhase by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(380, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "hop"
        )

        val goingToServer = !hasContentStarted
        val petXFraction = if (goingToServer) trotProgress else (1f - trotProgress)
        val statusText = when {
            isSearching -> "$assistantName is sniffing the web for answers…"
            goingToServer -> "$assistantName is delivering your letter to the server…"
            else -> "$assistantName is bringing your reply back!"
        }

        WobblyCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            seed = 77,
            fill = if (goingToServer) colors.butterSoft else colors.mintSoft,
            borderColor = colors.line,
            borderWidth = 1.5.dp,
            shadow = false,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📱", fontSize = 15.sp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .padding(horizontal = 6.dp)
                    ) {
                        val ink = colors.line
                        val roadColor = colors.inkMute.copy(alpha = 0.55f)
                        val furColor = if (mascot == MascotKind.CAT) colors.catFur else Color(0xFFF2D0A9)
                        val accentColor = if (goingToServer) colors.coral else colors.mint

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val roadY = size.height - 6f
                            // Dashed doodle road
                            drawLine(
                                color = roadColor,
                                start = Offset(6f, roadY),
                                end = Offset(size.width - 6f, roadY),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                            )

                            // Pet position & bounce
                            val usableW = (size.width - 52f).coerceAtLeast(20f)
                            val petCenterX = 26f + usableW * petXFraction
                            val hopY = kotlin.math.abs(sin(hopPhase)) * 7f
                            val petY = roadY - 14f - hopY
                            val facingRight = goingToServer

                            drawCourierPet(
                                center = Offset(petCenterX, petY),
                                facingRight = facingRight,
                                hopPhase = hopPhase,
                                isCat = mascot == MascotKind.CAT,
                                fur = furColor,
                                ink = ink,
                                parcelColor = if (goingToServer) colors.card else accentColor,
                                sealColor = colors.coral
                            )
                        }
                    }
                    Text(text = "☁️", fontSize = 17.sp)
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = statusText,
                    style = HandSmallStyle,
                    color = colors.inkSoft,
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun DrawScope.drawCourierPet(
    center: Offset,
    facingRight: Boolean,
    hopPhase: Float,
    isCat: Boolean,
    fur: Color,
    ink: Color,
    parcelColor: Color,
    sealColor: Color
) {
    val dir = if (facingRight) 1f else -1f
    val legSwing = sin(hopPhase) * 5f

    translate(center.x, center.y) {
        // Tail
        val tailPath = Path().apply {
            moveTo(-12f * dir, 2f)
            cubicTo(-22f * dir, -2f, -24f * dir, -12f, -18f * dir, -14f)
        }
        drawPath(tailPath, color = ink, style = Stroke(4f, cap = StrokeCap.Round))
        drawPath(tailPath, color = fur, style = Stroke(2f, cap = StrokeCap.Round))

        // Running legs
        drawLine(ink, Offset(-8f * dir, 7f), Offset((-10f + legSwing) * dir, 14f), 3f, cap = StrokeCap.Round)
        drawLine(ink, Offset(6f * dir, 7f), Offset((8f - legSwing) * dir, 14f), 3f, cap = StrokeCap.Round)

        // Body oval
        drawOval(
            color = fur,
            topLeft = Offset(-13f, -4f),
            size = Size(24f, 14f)
        )
        drawOval(
            color = ink,
            topLeft = Offset(-13f, -4f),
            size = Size(24f, 14f),
            style = Stroke(2.4f)
        )

        // Head
        val headX = 11f * dir
        val headY = -6f
        drawCircle(color = fur, radius = 9f, center = Offset(headX, headY))
        drawCircle(color = ink, radius = 9f, center = Offset(headX, headY), style = Stroke(2.4f))

        // Ears
        if (isCat) {
            val earPath = Path().apply {
                moveTo(headX - 6f, headY - 6f)
                lineTo(headX - 4f, headY - 14f)
                lineTo(headX, headY - 8f)
                moveTo(headX + 2f, headY - 8f)
                lineTo(headX + 5f, headY - 14f)
                lineTo(headX + 7f, headY - 5f)
            }
            drawPath(earPath, color = fur)
            drawPath(earPath, color = ink, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            // Floppy dog ear
            drawOval(
                color = sealColor.copy(alpha = 0.7f),
                topLeft = Offset(headX - 3f * dir - 3f, headY - 4f),
                size = Size(6f, 10f)
            )
            drawOval(
                color = ink,
                topLeft = Offset(headX - 3f * dir - 3f, headY - 4f),
                size = Size(6f, 10f),
                style = Stroke(1.8f)
            )
        }

        // Eye
        drawCircle(color = ink, radius = 1.8f, center = Offset(headX + 3.5f * dir, headY - 1.5f))

        // Envelope / Reply Scroll held in mouth!
        val envX = headX + 8f * dir
        val envY = headY + 2f
        val envLeft = if (facingRight) envX - 1f else envX - 13f
        rotate(degrees = if (facingRight) -8f else 8f, pivot = Offset(envX, envY)) {
            drawRoundRect(
                color = parcelColor,
                topLeft = Offset(envLeft, envY - 5f),
                size = Size(14f, 10f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = ink,
                topLeft = Offset(envLeft, envY - 5f),
                size = Size(14f, 10f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(1.8f)
            )
            // Envelope flap V
            val flap = Path().apply {
                moveTo(envLeft + 1f, envY - 4f)
                lineTo(envLeft + 7f, envY + 1f)
                lineTo(envLeft + 13f, envY - 4f)
            }
            drawPath(flap, color = ink, style = Stroke(1.5f, cap = StrokeCap.Round))
            // Tiny heart/wax seal
            drawCircle(color = sealColor, radius = 2.1f, center = Offset(envLeft + 7f, envY + 1f))
        }
    }
}

/**
 * Interactive, breathing, blinking, phone-tilt-parallax mascot for EmptyState.
 * Tap to pet for floating hearts, bounce squish, and a Purr/Woof bubble!
 */
@Composable
fun PettableHeroMascot(
    kind: MascotKind,
    mood: CatMood,
    size: Dp,
    tiltX: Float,
    tiltY: Float,
    isUserTyping: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    var petCount by remember { mutableIntStateOf(0) }
    var showPurrBubble by remember { mutableStateOf(false) }

    val bounceScale = remember { Animatable(1f) }
    val heartProgress = remember { Animatable(0f) }

    LaunchedEffect(petCount) {
        if (petCount > 0) {
            showPurrBubble = true
            bounceScale.snapTo(0.88f)
            bounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    LaunchedEffect(petCount) {
        if (petCount > 0) {
            heartProgress.snapTo(0f)
            heartProgress.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
            delay(400)
            showPurrBubble = false
        }
    }

    val effectiveMood = when {
        showPurrBubble -> CatMood.HAPPY
        isUserTyping -> CatMood.TYPING
        else -> mood
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { petCount += 1 },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = bounceScale.value
                scaleY = bounceScale.value
                translationX = tiltX * 14f
                translationY = tiltY * 10f + if (isUserTyping) 6f else 0f
                rotationZ = tiltX * 3f
            }
        ) {
            if (kind == MascotKind.CAT) {
                DoodleCat(mood = effectiveMood, size = size, animate = animate)
            } else {
                DoodleDog(
                    happy = effectiveMood == CatMood.HAPPY || effectiveMood == CatMood.TYPING,
                    size = size,
                    animate = animate
                )
            }
        }

        // Floating hearts when petted
        if (petCount > 0 && heartProgress.value < 0.99f) {
            val p = heartProgress.value
            val alpha = (1f - p).coerceIn(0f, 1f)
            Text(
                text = "💖",
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (-18).dp, y = (24 - p * 44).dp)
                    .alpha(alpha)
            )
            Text(
                text = "✨",
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (34 - p * 38).dp)
                    .alpha(alpha)
            )
        }

        // Cute speech bubble when petted or peeking while user types
        val bubbleText = when {
            showPurrBubble && kind == MascotKind.CAT -> "Purr~ 💕"
            showPurrBubble && kind == MascotKind.DOG -> "Woof! 🦴"
            isUserTyping -> "👀 listening…"
            else -> null
        }
        if (bubbleText != null) {
            Text(
                text = bubbleText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.butterSoft)
                    .border(1.4.dp, colors.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Pastel paw & star confetti burst played when a checklist is 100% completed.
 */
@Composable
fun DoodleConfettiOverlay(
    triggerCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = LocalPawPalColors.current
    val progress = remember { Animatable(1f) }

    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(1400, easing = FastOutSlowInEasing))
        }
    }

    if (triggerCount == 0 || progress.value >= 0.99f) return

    val palette = listOf(colors.coral, colors.butter, colors.mint, colors.sky, colors.lilac)
    val particles = remember(triggerCount) {
        List(18) { i ->
            val angle = (i * 20f) * (PI / 180.0)
            val speed = 60f + (i % 4) * 32f
            Triple(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed - 40f, i)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val alpha = (1f - t).coerceIn(0f, 1f)
        val cx = size.width / 2f
        val cy = size.height / 3f

        particles.forEach { (vx, vy, idx) ->
            val x = cx + vx * t * 1.4f
            val y = cy + vy * t + (t * t * 140f)
            val color = palette[idx % palette.size].copy(alpha = alpha)
            if (idx % 2 == 0) {
                drawCircle(color = color, radius = 6f * (1f - t * 0.3f), center = Offset(x, y))
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x - 5f, y - 3f),
                    size = Size(10f, 6f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
            }
        }
    }
}

/**
 * Walking paw-print cursor shown while AI is streaming tokens.
 */
@Composable
fun WalkingPawCursor(modifier: Modifier = Modifier) {
    val colors = LocalPawPalColors.current
    val transition = rememberInfiniteTransition(label = "pawCursor")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pawStep"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniPawCanvas(color = colors.coral.copy(alpha = 0.35f + step * 0.65f))
        Spacer(Modifier.width(4.dp))
        MiniPawCanvas(color = colors.coral.copy(alpha = 1f - step * 0.65f))
    }
}

@Composable
private fun MiniPawCanvas(color: Color) {
    Canvas(modifier = Modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        // Main pad
        drawOval(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.48f),
            size = Size(w * 0.56f, h * 0.42f)
        )
        // 3 tiny toe beans
        drawCircle(color = color, radius = w * 0.12f, center = Offset(w * 0.22f, h * 0.32f))
        drawCircle(color = color, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.22f))
        drawCircle(color = color, radius = w * 0.12f, center = Offset(w * 0.78f, h * 0.32f))
    }
}
