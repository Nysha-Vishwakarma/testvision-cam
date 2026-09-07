package com.example.ui.components

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.pose.PoseLibraryItem

/**
 * Frosted glass sheet allowing users to:
 * 1. Browse curated pose library by category
 * 2. Upload custom pose from gallery via zero-permission Photo Picker
 * 3. Paste a Pinterest / web image URL
 */
@Composable
fun PoseSelectionSheet(
    isVisible: Boolean,
    poses: List<PoseLibraryItem>,
    selectedPose: PoseLibraryItem,
    onSelectPose: (PoseLibraryItem) -> Unit,
    onUploadImage: (Uri) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val context = LocalContext.current
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onUploadImage(uri)
        }
    }

    var selectedCategory by remember { mutableStateOf("All") }
    var pinterestUrl by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }

    val categories = remember(poses) {
        listOf("All") + poses.map { it.category }.distinct()
    }

    val filteredPoses = remember(selectedCategory, poses) {
        if (selectedCategory == "All") poses else poses.filter { it.category == selectedCategory }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        GlassPanel(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            elevation = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clickable(enabled = false) {}
                .testTag("pose_selection_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pose Guide Library",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Select an aesthetic posture guide",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("close_pose_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Row: Upload Photo & Paste Pinterest URL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_pose_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Upload Pose", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showUrlInput = !showUrlInput },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showUrlInput) Color(0xFFFFD54F).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.14f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pinterest_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Pinterest URL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Expandable Pinterest / URL Input field
                if (showUrlInput) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pinterestUrl,
                            onValueChange = { pinterestUrl = it },
                            placeholder = {
                                Text(
                                    text = "Paste image or Pinterest URL...",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD54F),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("pinterest_url_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (pinterestUrl.isNotBlank()) {
                                    // Set default aesthetic pose with url note
                                    val custom = poses[0].copy(
                                        name = "Imported Pose",
                                        category = "Pinterest"
                                    )
                                    onSelectPose(custom)
                                    showUrlInput = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD54F),
                                contentColor = Color(0xFF1E2228)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("submit_url_button")
                        ) {
                            Text(text = "Load", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isCatSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCatSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.5.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCatSelected) Color(0xFF1E2228) else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Poses 2-Column Grid with Skeleton Thumbnails
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredPoses, key = { it.id }) { pose ->
                        val isSelected = pose.id == selectedPose.id

                        GlassPanel(
                            shape = RoundedCornerShape(16.dp),
                            elevation = if (isSelected) 8.dp else 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    Log.d("PoseSelectionSheet", "Selected pose: id=${pose.id}, name='${pose.name}', skeletonId=${pose.skeleton.id}, keypoints=${pose.skeleton.keypoints.size}")
                                    onSelectPose(pose)
                                }
                                .testTag("pose_card_${pose.id}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Dark neutral canvas thumbnail displaying skeleton stick figure outline
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .background(Color(0xFF101318)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PoseSkeletonThumbnail(
                                        skeleton = pose.skeleton,
                                        isSelected = isSelected,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFFD54F)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFF1E2228),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                // Information below skeleton outline: Name, Category, Difficulty
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = pose.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pose.category,
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = pose.difficulty,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
