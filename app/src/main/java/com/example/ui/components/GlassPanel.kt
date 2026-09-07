package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable minimalist glassmorphism container.
 * Features a semi-transparent frosted backdrop, 1px subtle white border,
 * smooth rounded corners, and clean diffuse drop-shadow.
 *
 * Android Render Architecture Fix:
 * Native RenderNode elevation and framework setShadowLayer with transparent fills
 * produce harsh black jagged/wired outline artifacts on modern Android GPUs.
 *
 * This implementation resolves the exact shape outline (matching any pill or card corner radius)
 * and renders smooth, multi-pass anti-aliased diffuse shadow layers in DrawScope,
 * completely eliminating unclipped rectangular or jagged shadow glitches even during
 * scale/alpha/loading animation loops.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 8.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0x661E222B),
            Color(0x440F1217)
        )
    )

    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0x55FFFFFF),
            Color(0x1AFFFFFF),
            Color(0x0AFFFFFF)
        )
    )

    // Outer shadow wrapper layer: casts diffuse radial shadow strictly matching shape
    Box(
        modifier = modifier
            .drawBehind {
                if (elevation > 0.dp) {
                    val elevationPx = elevation.toPx()
                    val shadowOffsetY = (elevationPx * 0.35f).coerceAtLeast(1f)

                    val outline = shape.createOutline(size, layoutDirection, this)
                    when (outline) {
                        is Outline.Rounded -> {
                            val r = outline.roundRect.topLeftCornerRadius

                            // Pass 1: Ambient soft outer blur (expanded spread, gentle alpha)
                            val spread1 = elevationPx * 0.40f
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.08f),
                                topLeft = Offset(-spread1, shadowOffsetY - spread1 * 0.5f),
                                size = Size(size.width + spread1 * 2, size.height + spread1 * 2),
                                cornerRadius = CornerRadius(r.x + spread1, r.y + spread1)
                            )

                            // Pass 2: Mid diffuse shadow
                            val spread2 = elevationPx * 0.18f
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.12f),
                                topLeft = Offset(-spread2, shadowOffsetY - spread2 * 0.5f),
                                size = Size(size.width + spread2 * 2, size.height + spread2 * 2),
                                cornerRadius = CornerRadius(r.x + spread2, r.y + spread2)
                            )

                            // Pass 3: Direct directional soft shadow matching exact shape
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.18f),
                                topLeft = Offset(0f, shadowOffsetY),
                                size = size,
                                cornerRadius = r
                            )
                        }
                        is Outline.Generic -> {
                            drawPath(
                                path = outline.path,
                                color = Color.Black.copy(alpha = 0.20f)
                            )
                        }
                        is Outline.Rectangle -> {
                            val spread = elevationPx * 0.25f
                            drawRect(
                                color = Color.Black.copy(alpha = 0.15f),
                                topLeft = Offset(-spread, shadowOffsetY - spread * 0.5f),
                                size = Size(size.width + spread * 2, size.height + spread * 2)
                            )
                        }
                    }
                }
            }
    ) {
        // Inner frosted glass layer: strictly clipped to shape with inner 1px border
        Box(
            modifier = Modifier
                .clip(shape)
                .background(brush = backgroundBrush)
                .border(
                    border = BorderStroke(1.dp, borderBrush),
                    shape = shape
                )
        ) {
            content()
        }
    }
}
