package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full-screen frosted glass photo inspection dialog.
 */
@Composable
fun PhotoPreviewDialog(
    photoUri: Uri?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photoUri == null) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .testTag("photo_preview_dialog"),
        contentAlignment = Alignment.Center
    ) {
        // Photo in rounded glass card
        GlassPanel(
            shape = RoundedCornerShape(24.dp),
            elevation = 16.dp,
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Captured Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
            )
        }

        // Close Glass Button at Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            GlassPanel(
                shape = CircleShape,
                elevation = 8.dp,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, color = Color.White),
                        onClick = onDismiss
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close preview",
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }
        }
    }
}
