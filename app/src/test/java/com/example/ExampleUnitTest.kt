package com.example

import androidx.compose.ui.geometry.Offset
import com.example.camera.CameraFilter
import com.example.camera.CameraMode
import com.example.camera.CameraUiState
import com.example.camera.FocusState
import com.example.camera.FaceLandmarkData
import com.example.camera.LandmarkSmoother
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying landmark smoothing algorithms and camera mode definitions.
 */
class ExampleUnitTest {

  @Test
  fun testLandmarkSmoother_averagesCoordinatesSmoothly() {
    val smoother = LandmarkSmoother(alpha = 0.5f)

    val frame1 = FaceLandmarkData(
      isFaceDetected = true,
      leftEye = Offset(100f, 100f),
      rightEye = Offset(200f, 100f),
      headEulerZ = 0f
    )

    val smoothed1 = smoother.smooth(frame1)
    assertNotNull(smoothed1)
    assertEquals(100f, smoothed1?.leftEye?.x ?: 0f, 0.001f)

    val frame2 = FaceLandmarkData(
      isFaceDetected = true,
      leftEye = Offset(120f, 100f),
      rightEye = Offset(220f, 100f),
      headEulerZ = 10f
    )

    val smoothed2 = smoother.smooth(frame2)
    assertNotNull(smoothed2)
    // 0.5 * 120 + 0.5 * 100 = 110
    assertEquals(110f, smoothed2?.leftEye?.x ?: 0f, 0.01f)
    // 0.5 * 10 + 0.5 * 0 = 5
    assertEquals(5f, smoothed2?.headEulerZ ?: 0f, 0.01f)
  }

  @Test
  fun testLandmarkSmoother_resetsGracefullyOnFaceLost() {
    val smoother = LandmarkSmoother(alpha = 0.5f)

    val frame = FaceLandmarkData(
      isFaceDetected = true,
      leftEye = Offset(100f, 100f),
      rightEye = Offset(200f, 100f)
    )
    smoother.smooth(frame)

    // Simulate 10 frames with no face
    var lastResult: FaceLandmarkData? = null
    repeat(10) {
      lastResult = smoother.smooth(null)
    }

    assertNull(lastResult)
  }

  @Test
  fun testCameraModes_containsAllRequiredModes() {
    val titles = CameraMode.entries.map { it.title }
    assertTrue(titles.contains("Photo"))
    assertTrue(titles.contains("Filter"))
    assertTrue(titles.contains("Pose Guide"))
    assertTrue(titles.contains("AI Photographer"))
  }

  @Test
  fun testCameraMode_defaultIsPhotoBaseCamera() {
    val defaultMode = CameraMode.entries.first()
    assertEquals(CameraMode.PHOTO, defaultMode)
    assertEquals("Photo", CameraMode.PHOTO.title)
    assertEquals("Photo", CameraMode.PHOTO.shortLabel)
  }

  @Test
  fun testCameraFilters_hasCleanDefault() {
    assertEquals(CameraFilter.NONE, CameraFilter.entries.first())
  }

  @Test
  fun testExposureCalculation_safeBounds() {
    val min = -4
    val max = 4
    val totalRange = (max - min).toFloat().coerceAtLeast(1f)
    assertEquals(8f, totalRange)

    val zeroFraction = ((0 - min).toFloat() / totalRange).coerceIn(0f, 1f)
    assertEquals(0.5f, zeroFraction)

    val clampedPositive = 6.coerceIn(min, max)
    assertEquals(4, clampedPositive)

    val clampedNegative = (-10).coerceIn(min, max)
    assertEquals(-4, clampedNegative)
  }

  @Test
  fun testFocusState_initialIdle() {
    val state = CameraUiState()
    assertEquals(FocusState.IDLE, state.focusState)
    assertEquals(null, state.focusPoint)
  }
}
