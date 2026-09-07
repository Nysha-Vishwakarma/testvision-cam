package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Clean, minimal glassmorphic information dialog displaying project credits,
 * student team details, and faculty appreciation.
 */
@Composable
fun InfoModal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        // Semi-transparent dimming backdrop that intercepts touches to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .testTag("info_modal_backdrop"),
            contentAlignment = Alignment.Center
        ) {
            // Calm subtle scale-in animated glass card
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                GlassPanel(
                    shape = RoundedCornerShape(26.dp),
                    elevation = 12.dp,
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { /* Intercept clicks inside card so it doesn't dismiss */ }
                        )
                        .testTag("info_modal_card")
                ) {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(22.dp)
                    ) {
                        // Header Row: Title & Close Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF81D4FA).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF81D4FA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.info_title),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                )
                            }

                            // Close Button (X icon with standard 48dp touch target)
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, color = Color.White),
                                        onClick = onDismiss
                                    )
                                    .testTag("info_modal_close_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close_info),
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Welcome & College Attribution
                        Text(
                            text = stringResource(R.string.info_welcome_desc),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Project Details Glass Capsule
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DetailRow(
                                    label = "Project name:",
                                    value = "AI Camera"
                                )
                                DetailRow(
                                    label = "Class & Course:",
                                    value = "SY BSc in Data Science and Analytics"
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Section 1: Student Team Header
                        SectionHeader(
                            icon = Icons.Rounded.Group,
                            title = "Student Team"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Student Team Styled List / Table
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                StudentRow("Nysha Vishwakarma", "Roll No. 52", isLast = false)
                                StudentRow("Isha Salgoankar", "Roll No. 45", isLast = false)
                                StudentRow("Urvi Ayachit", "Roll No. 03", isLast = false)
                                StudentRow("Shreeya Dubey", "Roll No. 13", isLast = true)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Section 2: A Special Thank You Header
                        SectionHeader(
                            icon = Icons.Rounded.VolunteerActivism,
                            title = "A Special Thank You"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Faculty Appreciation Body
                        Text(
                            text = "We sincerely thank our college faculty for their valuable support, guidance, and encouragement throughout the development of this project. We are grateful for their support in validating and helping us bring our idea to life. Thank You!",
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFFFD54F),
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StudentRow(
    name: String,
    rollNo: String,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = rollNo,
                color = Color(0xFF81D4FA).copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (!isLast) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
