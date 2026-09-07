package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.camera.aiphoto.AIPhotographerApiClient
import com.example.camera.aiphoto.AIPhotographerConfig
import com.example.camera.aiphoto.ServerConfigInfo
import kotlinx.coroutines.launch

/**
 * Modern glassmorphic settings dialog for the AI Photographer engine.
 * The Groq API key is managed strictly server-side (Render Cloud / .env).
 * The client only verifies and fetches configuration directly from the backend.
 *
 * Adheres strictly to premium SaaS CRM and mobile design guidelines:
 * - Rounded-xl / 18.dp soft rounded buttons (never sharp)
 * - Airy breathing space and vertical scrollability
 * - Glassmorphic stat cards with soft background blur, light border, and elegant shadow
 * - Modern dropdown selector for model configuration
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(AIPhotographerConfig.getServerUrl(context)) }
    var selectedModel by remember { mutableStateOf(AIPhotographerConfig.getGroqModel(context)) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var isVerifying by remember { mutableStateOf(false) }
    var serverConfig by remember { mutableStateOf<ServerConfigInfo?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(false) }

    val availableModels = listOf(
        "qwen/qwen3.6-27b" to "Qwen 3.6 27B Vision (Provider Prefixed)"
    )

    // Function to check status from server
    fun verifyServerSideConfig() {
        isVerifying = true
        statusMessage = "Connecting to backend to query server-side configuration..."
        isSuccessStatus = true
        coroutineScope.launch {
            val client = AIPhotographerApiClient()
            val result = client.fetchServerConfig(serverUrl)
            isVerifying = false
            if (result.isSuccess) {
                val info = result.getOrNull()
                serverConfig = info
                if (info != null && info.isServerKeyConfigured) {
                    isSuccessStatus = true
                    statusMessage = "Connected to Render Cloud. Server-side Groq key is active (${info.keyPreview}) with ${info.visionModel}."
                } else if (info != null) {
                    isSuccessStatus = false
                    statusMessage = "Connected to Render backend, but GROQ_API_KEY is not set in Render environment variables."
                } else {
                    isSuccessStatus = true
                    statusMessage = "Connected to backend successfully."
                }
            } else {
                isSuccessStatus = false
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                statusMessage = "Connection failed: $err. Check your backend URL or verify Render service is active."
            }
        }
    }

    // Auto-verify on open
    LaunchedEffect(Unit) {
        verifyServerSideConfig()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E222B),
                            Color(0xFF14171E)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI Engine",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AI Engine & Backend",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Server-Side Groq Qwen 3.6-27B",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Glass Stat Cards Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Server Vision Model
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "VISION MODEL",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Qwen 3.6 27B",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Groq Accelerated",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    // Card 2: Server-Side Key Security
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "API KEY STATUS",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val isKeyOk = serverConfig?.isServerKeyConfigured == true
                            Text(
                                text = if (isKeyOk) "Server Active" else if (serverConfig != null) "Missing on Server" else "Checking...",
                                color = if (isKeyOk) Color(0xFF00E676) else Color(0xFFFFD54F),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isKeyOk) (serverConfig?.keyPreview ?: "Secure") else "Render Cloud",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                // Server-Side Security Information Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Server Security",
                            tint = Color(0xFF00E676),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 1.dp)
                        )
                        Column {
                            Text(
                                text = "Server-Side Key Management",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Your Groq API key is kept securely on the Render cloud backend. The mobile app connects as a thin client without storing sensitive credentials.",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                // 1. Model Preference Selector (Modern Dropdown)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Vision Model Preference",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = availableModels.find { it.first == selectedModel }?.second ?: selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("model_selector_dropdown")
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .background(Color(0xFF1E222B))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        ) {
                            availableModels.forEach { (modelKey, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = label,
                                                color = if (selectedModel == modelKey) Color(0xFF00E676) else Color.White,
                                                fontWeight = if (selectedModel == modelKey) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = modelKey,
                                                color = Color.White.copy(alpha = 0.45f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedModel = modelKey
                                        dropdownExpanded = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Backend Server URL Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Python Backend URL",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E676),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.White.copy(alpha = 0.06f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input"),
                        singleLine = true
                    )

                    // Quick Switch Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                serverUrl = "https://vision-cam.onrender.com"
                                verifyServerSideConfig()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676).copy(alpha = 0.15f),
                                contentColor = Color(0xFF00E676)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Render Cloud (Default)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                serverUrl = "http://10.0.2.2:8000"
                                verifyServerSideConfig()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White.copy(alpha = 0.7f)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Local (10.0.2.2)", fontSize = 11.sp)
                        }
                    }
                }

                // Status Message
                AnimatedVisibility(
                    visible = statusMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSuccessStatus) Color(0xFF00E676).copy(alpha = 0.12f)
                                else Color(0xFFFF5252).copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (isSuccessStatus) Color(0xFF00E676).copy(alpha = 0.35f)
                                else Color(0xFFFF5252).copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            color = if (isSuccessStatus) Color(0xFF00E676) else Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Action Buttons Row (Save & Test Connection)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Check Server Status Button
                    OutlinedButton(
                        onClick = { verifyServerSideConfig() },
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF00E676)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("verify_server_config_button")
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF00E676),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Fetch Server Status", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            AIPhotographerConfig.setServerUrl(context, serverUrl)
                            AIPhotographerConfig.setGroqModel(context, selectedModel)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color(0xFF14171E)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_ai_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
