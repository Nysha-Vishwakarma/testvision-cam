package com.example.ui.components

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Top floating glass capsule containing flash, grid overlay, and info controls.
 */
@Composable
fun TopControls(
    flashMode: Int,
    onToggleFlash: () -> Unit,
    isGridVisible: Boolean,
    onToggleGrid: () -> Unit,
    onOpenInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        shape = RoundedCornerShape(24.dp),
        elevation = 6.dp,
        modifier = modifier.testTag("top_controls_panel")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            // Flash Mode Toggle
            val flashIcon = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                ImageCapture.FLASH_MODE_AUTO -> Icons.Rounded.FlashAuto
                else -> Icons.Rounded.FlashOff
            }
            val flashTint = when (flashMode) {
                ImageCapture.FLASH_MODE_ON, ImageCapture.FLASH_MODE_AUTO -> Color(0xFFFFD54F)
                else -> Color.White.copy(alpha = 0.7f)
            }
            val flashDesc = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> stringResource(R.string.flash_on)
                ImageCapture.FLASH_MODE_AUTO -> stringResource(R.string.flash_auto)
                else -> stringResource(R.string.flash_off)
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onToggleFlash
                    )
                    .testTag("flash_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = flashIcon,
                    contentDescription = flashDesc,
                    tint = flashTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Subtle Glass Divider 1
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 3x3 Grid Overlay Toggle
            val gridIcon = if (isGridVisible) Icons.Rounded.GridOn else Icons.Rounded.GridOff
            val gridTint = if (isGridVisible) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onToggleGrid
                    )
                    .testTag("grid_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = gridIcon,
                    contentDescription = stringResource(R.string.grid_toggle),
                    tint = gridTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Subtle Glass Divider 2
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // Project Info Modal Trigger
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onOpenInfo
                    )
                    .testTag("info_modal_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.info_button),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
