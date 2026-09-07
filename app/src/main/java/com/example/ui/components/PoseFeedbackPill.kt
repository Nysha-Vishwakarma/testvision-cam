package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal on-screen frosted glass pill showing:
 * 1. Match score (0-100%) with color-coded confidence (greyed out when no pose detected)
 * 2. Primary directional alignment cues or "No pose detected — step into frame"
 * 3. Graceful subtle reconnecting state
 */
@Composable
fun PoseFeedbackPill(
    noPoseDetected: Boolean,
    matchScore: Int?,
    primaryFeedback: String,
    isMatched: Boolean,
    isReconnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val isScoreAvailable = !noPoseDetected && matchScore != null

    val scoreColor = when {
        !isScoreAvailable -> Color.White.copy(alpha = 0.35f)
        matchScore >= 80 -> Color(0xFF00E676) // Emerald green
        matchScore >= 50 -> Color(0xFFFFD54F) // Golden amber
        else -> Color(0xFFFF8A65)             // Warm coral
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("pose_feedback_cluster")
    ) {
        // Top Glass Pill: Match Score + Status (Greyed out if no pose detected)
        GlassPanel(
            shape = RoundedCornerShape(22.dp),
            elevation = if (isScoreAvailable) 6.dp else 2.dp,
            modifier = Modifier.testTag("pose_score_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                // Score indicator circle / icon
                if (isMatched && isScoreAvailable) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Matched",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(scoreColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isScoreAvailable) "$matchScore% MATCH" else "POSE GUIDE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isScoreAvailable) Color.White else Color.White.copy(alpha = 0.55f),
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Secondary guidance chip
        val displayFeedback = when {
            noPoseDetected -> "No pose detected — step into frame"
            primaryFeedback.isNotEmpty() -> primaryFeedback
            else -> "Align body with guide skeleton"
        }

        AnimatedVisibility(
            visible = displayFeedback.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -10 },
            exit = fadeOut() + slideOutVertically { -10 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(6.dp))
                GlassPanel(
                    shape = RoundedCornerShape(14.dp),
                    elevation = 4.dp,
                    modifier = Modifier.testTag("pose_instruction_chip")
                ) {
                    Text(
                        text = displayFeedback,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (noPoseDetected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
