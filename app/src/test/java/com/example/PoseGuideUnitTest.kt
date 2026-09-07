package com.example

import com.example.camera.pose.BuiltInPoseLibrary
import com.example.camera.pose.OnDevicePoseEvaluator
import com.example.camera.pose.PoseScoreSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseGuideUnitTest {

    @Test
    fun builtInPoseLibrary_containsExpectedPoses() {
        val items = BuiltInPoseLibrary.items
        assertEquals("Library should contain exactly 10 poses", 10, items.size)

        val first = items.first()
        assertEquals("casual_lean", first.id)
        assertTrue("Keypoints should be non-empty", first.skeleton.keypoints.isNotEmpty())
        assertTrue("Joint angles should be pre-computed", first.skeleton.jointAngles.isNotEmpty())
    }

    @Test
    fun builtInPoseLibrary_distinctPosesHaveDistinctSkeletons() {
        val items = BuiltInPoseLibrary.items
        assertEquals("Library should contain exactly 10 poses", 10, items.size)

        val ids = items.map { it.id }.toSet()
        assertEquals("All pose IDs must be distinct", items.size, ids.size)

        // Ensure keypoints across poses are distinct and not duplicate skeletons
        val firstKpCoords = items[0].skeleton.keypoints.map { it.x to it.y }
        val secondKpCoords = items[1].skeleton.keypoints.map { it.x to it.y }
        assertTrue("Different poses must have distinct keypoints", firstKpCoords != secondKpCoords)
    }

    @Test
    fun onDevicePoseEvaluator_perfectMatchYieldsHighScore() {
        val target = BuiltInPoseLibrary.items[0].skeleton
        val result = OnDevicePoseEvaluator.compare(target, target.jointAngles)

        val score = result.matchScore
        assertNotNull("Score should not be null for valid match", score)
        assertTrue("Expected score >= 90 for identical angles", score != null && score >= 90)
        assertTrue("Expected isMatched to be true", result.isMatched)
        assertEquals("EXCELLENT", result.status)
        assertTrue("Primary feedback should acknowledge flawless alignment", result.primaryFeedback.contains("Flawless"))
    }

    @Test
    fun onDevicePoseEvaluator_insufficientDataYieldsNoPoseDetected() {
        val target = BuiltInPoseLibrary.items[0].skeleton
        val emptyAngles = emptyMap<String, Float>()
        val result = OnDevicePoseEvaluator.compare(target, emptyAngles)

        assertTrue("Empty joint angles should flag noPoseDetected", result.noPoseDetected)
        assertEquals("Score should be null when no pose detected", null, result.matchScore)
        assertEquals("NO_POSE_DETECTED", result.status)
    }

    @Test
    fun onDevicePoseEvaluator_misalignedLimbGeneratesDirectionalFeedback() {
        val target = BuiltInPoseLibrary.items[0].skeleton
        val perturbedAngles = target.jointAngles.toMutableMap()
        val originalElbow = perturbedAngles["left_elbow"] ?: 90f
        perturbedAngles["left_elbow"] = originalElbow + 30f // Misalign by 30 degrees

        val result = OnDevicePoseEvaluator.compare(target, perturbedAngles)
        val misaligned = result.corrections.filter { it.status != "ALIGNED" }

        assertTrue("Should detect misaligned limb", misaligned.isNotEmpty())
        val elbowCorrection = misaligned.find { it.limb == "left_elbow" }
        assertNotNull("Should have correction for left elbow", elbowCorrection)
        assertTrue("Instruction should guide adjustment", elbowCorrection!!.instruction.contains("elbow"))
    }

    @Test
    fun poseScoreSmoother_smoothsMovingAverageWindow() {
        val smoother = PoseScoreSmoother(windowSize = 3)
        assertEquals(80, smoother.smooth(80))
        assertEquals(85, smoother.smooth(90)) // (80 + 90) / 2 = 85
        assertEquals(80, smoother.smooth(70)) // (80 + 90 + 70) / 3 = 80
        assertEquals(86, smoother.smooth(100)) // (90 + 70 + 100) / 3 = 86 (window of 3: drops 80)

        smoother.reset()
        assertEquals(50, smoother.smooth(50))
    }
}
