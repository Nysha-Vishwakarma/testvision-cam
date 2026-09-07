package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R

/**
 * Floating glass pill housing the primary camera controls:
 * - Last captured thumbnail preview (left)
 * - Tactile shutter button (center)
 * - Front/rear camera switch (right)
 */
@Composable
fun BottomControls(
    isCapturing: Boolean,
    onShutterClick: () -> Unit,
    onSwitchCamera: () -> Unit,
    lastCapturedUri: Uri?,
    onThumbnailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = tween(durationMillis = 350),
        label = "switch_camera_rotation"
    )

    GlassPanel(
        shape = RoundedCornerShape(48.dp),
        elevation = 10.dp,
        modifier = modifier.testTag("bottom_controls_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Left: Gallery / Last Captured Photo Thumbnail
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onThumbnailClick
                    )
                    .testTag("gallery_thumbnail_button"),
                contentAlignment = Alignment.Center
            ) {
                if (lastCapturedUri != null) {
                    AsyncImage(
                        model = lastCapturedUri,
                        contentDescription = stringResource(R.string.gallery_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.gallery_thumbnail),
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Center: Primary Shutter Button
            Box(
                modifier = Modifier.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                ShutterButton(
                    onClick = onShutterClick,
                    isCapturing = isCapturing
                )
            }

            // Right: Front/Rear Lens Switch Button
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isCapturing) 0.04f else 0.08f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (isCapturing) 0.15f else 0.4f),
                                Color.White.copy(alpha = if (isCapturing) 0.05f else 0.1f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = !isCapturing,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White)
                    ) {
                        rotationDegrees += 180f
                        onSwitchCamera()
                    }
                    .testTag("switch_camera_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cameraswitch,
                    contentDescription = stringResource(R.string.switch_camera),
                    tint = Color.White.copy(alpha = if (isCapturing) 0.35f else 0.85f),
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(animatedRotation)
                )
            }
        }
    }
}
