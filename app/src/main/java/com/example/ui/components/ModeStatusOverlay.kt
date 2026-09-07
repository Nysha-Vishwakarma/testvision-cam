package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.camera.CameraFilter
import com.example.camera.CameraMode
import com.example.camera.FaceLandmarkData

/**
 * Overlay displaying status for the active mode:
 * - In "Filter" mode: subtle face tracking indicator if a filter is active but no face is in frame
 */
@Composable
fun ModeStatusOverlay(
    currentMode: CameraMode,
    activeFilter: CameraFilter,
    faceLandmarks: FaceLandmarkData?,
    onReturnToBase: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Subtle tracking status when filter is active but no face is in frame
    if (currentMode == CameraMode.FILTER && activeFilter != CameraFilter.NONE) {
        val isDetected = faceLandmarks?.isFaceDetected == true
        AnimatedVisibility(
            visible = !isDetected,
            enter = fadeIn() + slideInVertically { -20 },
            exit = fadeOut() + slideOutVertically { -20 },
            modifier = modifier
        ) {
            GlassPanel(
                shape = RoundedCornerShape(18.dp),
                elevation = 4.dp,
                modifier = Modifier.testTag("face_search_indicator")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFD54F))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Looking for a face…",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
