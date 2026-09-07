package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import com.example.camera.CameraFilter
import com.example.camera.FaceFilterRenderer
import com.example.camera.FaceLandmarkData

/**
 * High-performance hardware-accelerated Canvas overlay rendering real-time face filters.
 */
@Composable
fun FaceFilterOverlay(
    activeFilter: CameraFilter,
    faceLandmarks: FaceLandmarkData?,
    modifier: Modifier = Modifier
) {
    if (activeFilter == CameraFilter.NONE || faceLandmarks == null || !faceLandmarks.isFaceDetected) {
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("face_filter_overlay")
    ) {
        drawIntoCanvas { canvas ->
            FaceFilterRenderer.drawFilter(
                canvas = canvas.nativeCanvas,
                width = size.width,
                height = size.height,
                filter = activeFilter,
                landmarks = faceLandmarks
            )
        }
    }
}
