package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.aiphoto.AIPhotographerUiState

/**
 * Modern glassmorphic overlay for AI Photographer mode.
 * Upgraded with:
 * - General object/subject recognition support (person, object, product, food, pet, scenery).
 * - Closed-loop movement verification cues (confirms user actually followed last instruction).
 * - Multi-directional guidance (Orbit Left/Right, Move Up/Down/Left/Right, Step Closer/Back).
 * - Manual override banner when guidance movement is stuck.
 * - Live composition score pill (0-100%) with emerald glow on ready-to-capture.
 * - Ambient lighting & zoom/exposure adaptation chips.
 */
@Composable
fun AIPhotographerOverlay(
    state: AIPhotographerUiState,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val score = state.score
    val isReady = state.readyToCapture
    val isCapturing = state.isAutoCapturing
    val isStuck = state.guidanceStuck
    val movementFollowed = state.previousInstructionFollowed

    val scoreColor by animateColorAsState(
        targetValue = when {
            isReady || score >= 75 -> Color(0xFF00E676) // Emerald green
            score >= 50 -> Color(0xFFFFD54F)            // Warm amber
            else -> Color(0xFFFF8A65)                   // Coral orange
        },
        animationSpec = tween(durationMillis = 300),
        label = "scoreColor"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Composition Score & Subject Recognition Glass Pill
        GlassPanel(
            modifier = Modifier
                .testTag("ai_composition_score_pill")
                .then(
                    if (isReady) Modifier.scale(pulseScale) else Modifier
                ),
            shape = RoundedCornerShape(22.dp),
            elevation = if (isReady) 8.dp else 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val isAnalyzing = state.isAnalyzing

                Box(
                    modifier = Modifier.size(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAnalyzing && score == 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF64B5F6),
                            trackColor = Color.White.copy(alpha = 0.15f),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { (score / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                            color = scoreColor,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            strokeWidth = 2.5.dp
                        )
                    }
                    if (isReady) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Ready to Capture",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(15.dp)
                        )
                    } else if (isAnalyzing && score == 0) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Analyzing Composition",
                            tint = Color(0xFF64B5F6),
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Scorer",
                            tint = scoreColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(9.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAnalyzing && score == 0) {
                        Text(
                            text = "ANALYZING…",
                            color = Color(0xFF64B5F6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    } else {
                        Text(
                            text = "$score%",
                            color = scoreColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isAnalyzing) "ANALYZING…" else if (isReady) "READY" else "COMPOSITION",
                            color = if (isReady) Color(0xFF00E676) else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // General Subject Type Badge (e.g. Object, Product, Person)
                if (state.subjectType.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = state.subjectType.uppercase(),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        // 2. Guidance Card: Either Manual Override (Stuck), Movement Correction, or Directional Guidance
        if (isStuck) {
            // Manual Override Banner when movement detection was repeatedly missed
            GlassPanel(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .testTag("ai_manual_override_card"),
                shape = RoundedCornerShape(18.dp),
                elevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = "Manual Override",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Manual Override Active",
                            color = Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.correctionNote ?: "Having trouble detecting movement — feel free to adjust manually and tap capture",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        } else if (!movementFollowed && state.correctionNote != null) {
            // Closed-Loop Movement Verification: Previous movement was NOT detected or was incorrect
            GlassPanel(
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .testTag("ai_verification_correction_card"),
                shape = RoundedCornerShape(18.dp),
                elevation = 5.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Correction",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Movement Check",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = state.correctionNote,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Standard Directional Nudge & Lighting Guidance Card
            AnimatedVisibility(
                visible = state.framingFeedback.isNotBlank(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { -8 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -8 })
            ) {
                GlassPanel(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .testTag("ai_framing_nudge_card"),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Directional row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            val dirIcon = when (state.suggestedMoveDirection) {
                                "ORBIT_LEFT" -> Icons.Rounded.RotateLeft
                                "ORBIT_RIGHT" -> Icons.Rounded.RotateRight
                                "MOVE_UP" -> Icons.Rounded.ArrowUpward
                                "MOVE_DOWN" -> Icons.Rounded.ArrowDownward
                                "MOVE_LEFT" -> Icons.Rounded.ArrowBack
                                "MOVE_RIGHT" -> Icons.Rounded.ArrowForward
                                "STEP_BACK" -> Icons.Rounded.ZoomOut
                                "STEP_CLOSER" -> Icons.Rounded.ZoomIn
                                else -> if (state.readyToCapture) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome
                            }
                            val dirTint = when {
                                state.readyToCapture -> Color(0xFF00E676)
                                state.suggestedMoveDirection != "HOLD_STEADY" -> Color(0xFFFFD54F)
                                else -> Color.White.copy(alpha = 0.85f)
                            }

                            Icon(
                                imageVector = dirIcon,
                                contentDescription = state.suggestedMoveDirection,
                                tint = dirTint,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.framingFeedback,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Lighting row (compact secondary line if present and analyzed)
                        if (state.lastAnalyzedTime > 0L && state.lightingFeedback.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WbSunny,
                                    contentDescription = "Lighting",
                                    tint = Color(0xFFFFD54F).copy(alpha = 0.85f),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = state.lightingFeedback,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Auto-Capture Countdown Visual Cue
        AnimatedVisibility(
            visible = isCapturing,
            enter = fadeIn(tween(150)) + slideInVertically(initialOffsetY = { 10 }),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xE600E676), Color(0xCC00C853))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto-Capturing — Hold Steady!",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Offline State Notice (Graceful non-blocking)
        AnimatedVisibility(
            visible = !state.isBackendConnected,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -6 }),
            exit = fadeOut()
        ) {
            GlassPanel(
                modifier = Modifier.testTag("ai_offline_notice"),
                shape = RoundedCornerShape(14.dp),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = "Offline",
                        tint = Color(0xFFFF8A65),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AI Assistant offline — Manual capture ready",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
