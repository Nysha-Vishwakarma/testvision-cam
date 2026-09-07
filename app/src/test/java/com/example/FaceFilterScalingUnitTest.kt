package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Offset
import com.example.camera.CameraFilter
import com.example.camera.FaceFilterRegistry
import com.example.camera.FaceFilterRenderer
import com.example.camera.FaceFilterScaleUtil
import com.example.camera.FaceLandmarkData
import com.example.camera.FilterAnchor
import com.example.camera.LandmarkSmoother
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying dynamic face filter scaling, landmark anchoring,
 * and multi-distance face proportion stability.
 */
@RunWith(RobolectricTestRunner::class)
class FaceFilterScalingUnitTest {

    @Test
    fun testScaleFactor_scalesDynamicallyWithDistance() {
        val screenWidth = 1000f
        val screenHeight = 2000f

        // 1. Close-up face (fills a large portion of screen: eye distance = 300px)
        val closeUpFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.35f, 0.40f),
            rightEye = Offset(0.65f, 0.40f) // dx = 0.30 * 1000 = 300px
        )

        // 2. Arm's length / normal portrait face (eye distance = 140px)
        val normalFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.43f, 0.40f),
            rightEye = Offset(0.57f, 0.40f) // dx = 0.14 * 1000 = 140px
        )

        // 3. Far-away face (small in frame: eye distance = 60px)
        val farFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.47f, 0.40f),
            rightEye = Offset(0.53f, 0.40f) // dx = 0.06 * 1000 = 60px
        )

        val closeScale = FaceFilterScaleUtil.getFaceScaleFactor(closeUpFace, canvasWidth = screenWidth, canvasHeight = screenHeight)
        val normalScale = FaceFilterScaleUtil.getFaceScaleFactor(normalFace, canvasWidth = screenWidth, canvasHeight = screenHeight)
        val farScale = FaceFilterScaleUtil.getFaceScaleFactor(farFace, canvasWidth = screenWidth, canvasHeight = screenHeight)

        // Verify closeUp > normal > far
        assertTrue("Close-up scale ($closeScale) must be significantly larger than normal scale ($normalScale)", closeScale > normalScale)
        assertTrue("Normal scale ($normalScale) must be significantly larger than far scale ($farScale)", normalScale > farScale)

        // Reference eye distance is 100px:
        assertEquals(3.0f, closeScale, 0.05f)
        assertEquals(1.4f, normalScale, 0.05f)
        assertEquals(0.6f, farScale, 0.05f)
    }

    @Test
    fun testDifferentFaceSizes_scalesProportionally() {
        val screenWidth = 1080f
        val screenHeight = 1920f

        // Adult face with wider inter-pupillary distance (e.g. 180px on canvas)
        val adultFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.4167f, 0.45f),
            rightEye = Offset(0.5833f, 0.45f) // 0.1666 * 1080 = 180px
        )

        // Smaller face proportions (e.g. 110px on canvas at same distance)
        val smallerFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.4491f, 0.45f),
            rightEye = Offset(0.5509f, 0.45f) // 0.1018 * 1080 = 110px
        )

        val adultScale = FaceFilterScaleUtil.getFaceScaleFactor(adultFace, canvasWidth = screenWidth, canvasHeight = screenHeight)
        val smallerScale = FaceFilterScaleUtil.getFaceScaleFactor(smallerFace, canvasWidth = screenWidth, canvasHeight = screenHeight)

        assertEquals(1.8f, adultScale, 0.05f)
        assertEquals(1.1f, smallerScale, 0.05f)

        // Proportions: Adult filter asset renders ~1.63x larger than smaller face asset
        val ratio = adultScale / smallerScale
        assertEquals(180f / 110f, ratio, 0.05f)
    }

    @Test
    fun testCyberGoggles_proportionedCorrectlyForAdultFace() {
        val config = FaceFilterRegistry.getConfig(CameraFilter.NEON_GLASSES)

        // Design width must be at least 2.8x reference eye distance to span temple-to-temple
        val widthToEyeRatio = config.designWidth / config.referenceEyeDistance
        assertTrue("Cyber goggles width ratio ($widthToEyeRatio) must be at least 2.8x for adult temple coverage", widthToEyeRatio >= 2.8f)

        // Design height must cover orbital sockets and cheek tops
        val heightToEyeRatio = config.designHeight / config.referenceEyeDistance
        assertTrue("Cyber goggles height ratio ($heightToEyeRatio) must be at least 1.0x", heightToEyeRatio >= 1.0f)

        // Under normal camera distance (140px eye distance on 1000px screen)
        val normalFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.43f, 0.45f),
            rightEye = Offset(0.57f, 0.45f)
        )
        val scale = FaceFilterScaleUtil.getFaceScaleFactor(normalFace, canvasWidth = 1000f, canvasHeight = 1000f)
        val (renderW, renderH) = config.getRenderDimensions(scale)

        assertEquals(420f, renderW, 5f)  // 300 * 1.4 = 420px (generous adult fit)
        assertEquals(154f, renderH, 5f)  // 110 * 1.4 = 154px
    }

    @Test
    fun testAnchorPointResolution_pinsCorrectLandmarks() {
        val screenWidth = 1000f
        val screenHeight = 1000f

        val face = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.40f, 0.40f), // (400, 400)
            rightEye = Offset(0.60f, 0.40f), // (600, 400)
            noseBase = Offset(0.50f, 0.52f)  // (500, 520)
        )

        // Eye center must be (500, 400)
        val eyeCenter = FaceFilterScaleUtil.resolveAnchorPoint(face, FilterAnchor.EYE_CENTER, screenWidth, screenHeight, 0f)
        assertEquals(500f, eyeCenter.x, 1f)
        assertEquals(400f, eyeCenter.y, 1f)

        // Eyewear filters must anchor to EYE_CENTER, not generic face or nose bridge
        val neonConfig = FaceFilterRegistry.getConfig(CameraFilter.NEON_GLASSES)
        val aviatorConfig = FaceFilterRegistry.getConfig(CameraFilter.AVIATOR_SHADES)
        assertEquals(FilterAnchor.EYE_CENTER, neonConfig.anchor)
        assertEquals(FilterAnchor.EYE_CENTER, aviatorConfig.anchor)

        // Bridge of nose must be between eye center (500, 400) and nose base (500, 520)
        val noseBridge = FaceFilterScaleUtil.resolveAnchorPoint(face, FilterAnchor.BRIDGE_OF_NOSE, screenWidth, screenHeight, 0f)
        assertEquals(500f, noseBridge.x, 1f)
        assertTrue("Bridge Y (${noseBridge.y}) should be below eye center (400) and above nose base (520)", noseBridge.y in 405f..515f)

        // Forehead must be above eye center (Y < 400)
        val forehead = FaceFilterScaleUtil.resolveAnchorPoint(face, FilterAnchor.FOREHEAD, screenWidth, screenHeight, 0f)
        assertEquals(500f, forehead.x, 1f)
        assertTrue("Forehead Y (${forehead.y}) must be well above eye center (400)", forehead.y < 300f)

        // Top of head must be higher than forehead
        val topOfHead = FaceFilterScaleUtil.resolveAnchorPoint(face, FilterAnchor.TOP_OF_HEAD, screenWidth, screenHeight, 0f)
        assertTrue("Top of head Y (${topOfHead.y}) must be higher than forehead (${forehead.y})", topOfHead.y < forehead.y)
    }

    @Test
    fun testEyewearVerticalOffset_appliedAfterScalingProportionallyAtAllDistancesAndAngles() {
        val screen = 1000f

        // 1. Close-up face (eye distance = 300px)
        val closeFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.35f, 0.40f),
            rightEye = Offset(0.65f, 0.40f) // 300px
        )

        // 2. Medium face (eye distance = 150px)
        val mediumFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.425f, 0.40f),
            rightEye = Offset(0.575f, 0.40f) // 150px
        )

        // 3. Far face (eye distance = 60px)
        val farFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.47f, 0.40f),
            rightEye = Offset(0.53f, 0.40f) // 60px
        )

        val offsetRatio = -0.02f // Upward shift by 2% of inter-eye distance

        val closeAnchor = FaceFilterScaleUtil.resolveAnchorPoint(closeFace, FilterAnchor.EYE_CENTER, screen, screen, 0f, offsetRatio)
        val medAnchor = FaceFilterScaleUtil.resolveAnchorPoint(mediumFace, FilterAnchor.EYE_CENTER, screen, screen, 0f, offsetRatio)
        val farAnchor = FaceFilterScaleUtil.resolveAnchorPoint(farFace, FilterAnchor.EYE_CENTER, screen, screen, 0f, offsetRatio)

        // Eye center Y is 400px. Offset = eyeDistance * offsetRatio
        // Close: 300px * (-0.02) = -6.0px -> 394.0px
        // Medium: 150px * (-0.02) = -3.0px -> 397.0px
        // Far: 60px * (-0.02) = -1.2px -> 398.8px
        assertEquals(394f, closeAnchor.y, 0.5f)
        assertEquals(397f, medAnchor.y, 0.5f)
        assertEquals(398.8f, farAnchor.y, 0.5f)

        // Verify proportional scaling: offset ratio is strictly preserved across all distances
        val closeDisplacement = Math.abs(closeAnchor.y - 400f)
        val medDisplacement = Math.abs(medAnchor.y - 400f)
        val farDisplacement = Math.abs(farAnchor.y - 400f)
        assertEquals(2.0f, closeDisplacement / medDisplacement, 0.05f) // 6.0 / 3.0 = 2.0x (300px / 150px)
        assertEquals(2.5f, medDisplacement / farDisplacement, 0.05f)  // 3.0 / 1.2 = 2.5x (150px / 60px)

        // 4. Tilted head (30 degrees clockwise)
        val tiltedAnchor = FaceFilterScaleUtil.resolveAnchorPoint(closeFace, FilterAnchor.EYE_CENTER, screen, screen, 30f, offsetRatio)
        // With 30 deg rotation, offset vector shifts along rotated head axis
        assertTrue("Tilted anchor X must shift with head angle", tiltedAnchor.x != 500f)
        assertTrue("Tilted anchor Y must remain close to 394-400px", tiltedAnchor.y in 393f..401f)
    }

    @Test
    fun testScaleSmoothing_dampensFrameJitterWithoutPulsing() {
        val smoother = LandmarkSmoother(alpha = 0.40f)

        // Frame 1: Baseline eye distance 0.20 normalized
        val frame1 = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.40f, 0.50f),
            rightEye = Offset(0.60f, 0.50f)
        )
        val smoothed1 = smoother.smooth(frame1)!!
        assertEquals(0.20f, smoothed1.smoothedInterEyeDistance, 0.01f)

        // Frame 2: Jitter pulse anomaly (sensor detected temporary 10% spike in distance)
        val frame2 = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.39f, 0.50f),
            rightEye = Offset(0.61f, 0.50f) // raw distance = 0.22 (+10% jump)
        )
        val smoothed2 = smoother.smooth(frame2)!!

        // Smoothed distance must dampen the jitter: 0.40 * 0.22 + 0.60 * 0.20 = 0.208
        assertEquals(0.208f, smoothed2.smoothedInterEyeDistance, 0.005f)

        // Confirm dampening prevented a sudden +10% visual pulsation
        val rawJump = (0.22f - 0.20f) / 0.20f
        val smoothedJump = (smoothed2.smoothedInterEyeDistance - 0.20f) / 0.20f
        assertTrue(smoothedJump < rawJump * 0.5f)
    }

    @Test
    fun testRenderAllFilters_executesCleanlyAtMultipleScales() {
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val closeFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.30f, 0.35f),
            rightEye = Offset(0.70f, 0.35f),
            noseBase = Offset(0.50f, 0.50f),
            headEulerZ = 12f
        )

        val farFace = FaceLandmarkData(
            isFaceDetected = true,
            leftEye = Offset(0.46f, 0.45f),
            rightEye = Offset(0.54f, 0.45f),
            noseBase = Offset(0.50f, 0.52f),
            headEulerZ = -8f
        )

        for (filter in CameraFilter.entries) {
            // Render close-up
            FaceFilterRenderer.drawFilter(canvas, 800f, 800f, filter, closeFace)
            // Render far
            FaceFilterRenderer.drawFilter(canvas, 800f, 800f, filter, farFace)
        }

        bitmap.recycle()
    }
}
