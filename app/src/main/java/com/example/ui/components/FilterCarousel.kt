package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.CameraFilter

/**
 * Glassmorphic horizontal filter selector carousel.
 * Allows swiping or tapping through facial filters with live preview.
 */
@Composable
fun FilterCarousel(
    activeFilter: CameraFilter,
    onFilterSelected: (CameraFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .testTag("filter_carousel")
    ) {
        items(CameraFilter.entries.toTypedArray(), key = { it.name }) { filter ->
            val isSelected = filter == activeFilter

            val ringColor by animateColorAsState(
                targetValue = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.2f),
                animationSpec = tween(220),
                label = "filter_ring_color"
            )

            val ringWidth by animateDpAsState(
                targetValue = if (isSelected) 2.dp else 1.dp,
                animationSpec = tween(220),
                label = "filter_ring_width"
            )

            val icon: ImageVector = when (filter) {
                CameraFilter.NONE -> Icons.Rounded.AutoFixOff
                CameraFilter.NEON_GLASSES -> Icons.Rounded.Visibility
                CameraFilter.AVIATOR_SHADES -> Icons.Rounded.Lightbulb
                CameraFilter.CAT_EARS -> Icons.Rounded.Pets
                CameraFilter.CELESTIAL_HALO -> Icons.Rounded.WbSunny
                CameraFilter.CYBER_MESH -> Icons.Rounded.GridView
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 28.dp)
                    ) {
                        if (filter != activeFilter) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFilterSelected(filter)
                        }
                    }
                    .testTag("filter_item_${filter.id}")
            ) {
                // Glass Circle Thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                Brush.radialGradient(
                                    listOf(Color(0x33FFD54F), Color(0x1A1E2228))
                                )
                            } else {
                                Brush.radialGradient(
                                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                                )
                            }
                        )
                        .border(
                            width = ringWidth,
                            color = ringColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = filter.displayName,
                        tint = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = filter.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
