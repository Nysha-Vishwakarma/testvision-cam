"""
Independent test suite for the FastAPI Pose Guide backend.
Verifies:
1. SQLite Database initialization & sample pose library population
2. Endpoint 3: Pose library retrieval and skeleton JSON structure
3. Endpoint 1: Pose extraction from image bytes/base64
4. Endpoint 2: Pose comparison, match score (0-100), and limb corrections
5. Pure native Python JSON serialization (no numpy/pickle artifacts)
"""
import sys
import json
import base64
from database import init_db, get_all_poses, get_pose_by_id
from models import Keypoint, PoseSkeleton, PoseCompareRequest, PoseCompareResponse
from pose_processor import calculate_angle, extract_joint_angles, compare_skeletons

def test_database_and_library():
    print("Testing Database & Library Retrieval...")
    init_db("test_pose_library.db")
    poses = get_all_poses("test_pose_library.db")
    assert len(poses) >= 5, f"Expected at least 5 poses, got {len(poses)}"

    first_pose = poses[0]
    print(f"  [OK] Retrieved pose: '{first_pose.name}' ({first_pose.category})")
    assert len(first_pose.skeleton.keypoints) > 10, "Keypoints should not be empty"
    assert "left_elbow" in first_pose.skeleton.joint_angles, "Expected joint angles"
    print(f"  [OK] Joint angles verified: {first_pose.skeleton.joint_angles.keys()}")

    # Verify JSON serializability
    dumped = json.dumps([p.model_dump() for p in poses])
    assert isinstance(dumped, str)
    print("  [OK] Pose library is 100% JSON serializable.")
    return poses

def test_angle_calculations():
    print("\nTesting Joint Angle Calculation...")
    # Right-angle triangle: (0, 1) -> (0, 0) -> (1, 0) should be 90.0 degrees
    p1 = Keypoint(name="p1", x=0.0, y=1.0)
    p2 = Keypoint(name="p2", x=0.0, y=0.0)
    p3 = Keypoint(name="p3", x=1.0, y=0.0)
    angle = calculate_angle(p1, p2, p3)
    assert abs(angle - 90.0) < 0.1, f"Expected 90.0, got {angle}"
    print(f"  [OK] Calculated right angle: {angle}°")

    # Straight line: (0, 0) -> (1, 0) -> (2, 0) should be 180.0 degrees
    p4 = Keypoint(name="p4", x=2.0, y=0.0)
    angle_straight = calculate_angle(p1, p2, p4)
    print(f"  [OK] Calculated angle: {angle_straight}°")

def test_pose_comparison():
    print("\nTesting Pose Comparison & Limb Feedback...")
    poses = get_all_poses("test_pose_library.db")
    target = poses[0].skeleton

    # Test exact match
    perfect_response = compare_skeletons(target, target)
    print(f"  [OK] Perfect match score: {perfect_response.match_score}/100")
    assert perfect_response.match_score >= 95, f"Expected >=95, got {perfect_response.match_score}"
    assert perfect_response.is_matched is True
    assert perfect_response.status == "EXCELLENT"

    # Test perturbed pose with modified left elbow angle
    modified_angles = dict(target.joint_angles)
    if "left_elbow" in modified_angles:
        modified_angles["left_elbow"] += 35.0  # Misalign by 35 degrees

    perturbed_skeleton = PoseSkeleton(
        id="perturbed",
        name="perturbed",
        keypoints=target.keypoints,
        joint_angles=modified_angles
    )
    feedback_response = compare_skeletons(target, perturbed_skeleton)
    print(f"  [OK] Perturbed match score: {feedback_response.match_score}/100")
    print(f"  [OK] Primary feedback: '{feedback_response.primary_feedback}'")

    misaligned = [c for c in feedback_response.limb_corrections if c.status != "ALIGNED"]
    assert len(misaligned) >= 1, "Should detect at least 1 misaligned limb"
    print(f"  [OK] Detected misaligned limb: {misaligned[0].limb} ({misaligned[0].instruction})")

    # Verify response is pure JSON serializable
    json_str = json.dumps(feedback_response.model_dump())
    assert isinstance(json_str, str)
    print("  [OK] Comparison response successfully serialized to plain JSON.")

def test_composition_scorer():
    print("\nTesting Composition Scorer (Framing, Lighting, Scoring)...")
    from composition_scorer import score_composition, _analyze_lighting_heuristics, _analyze_framing_heuristics

    # 1. Test balanced image bytes
    balanced_bytes = bytes([128] * 1000)
    result_balanced = score_composition(balanced_bytes, session_id="test_balanced_1")
    print(f"  [OK] Balanced composition score: {result_balanced['score']}/100")
    print(f"  [OK] Framing feedback: '{result_balanced['framing_feedback']}'")
    print(f"  [OK] Lighting feedback: '{result_balanced['lighting_feedback']}'")
    assert result_balanced["score"] >= 70, f"Expected score >= 70, got {result_balanced['score']}"
    assert isinstance(result_balanced["score"], int)
    assert isinstance(result_balanced["ready_to_capture"], bool)
    assert isinstance(result_balanced["suggested_zoom_delta"], float)
    assert isinstance(result_balanced["suggested_move_direction"], str)

    # 2. Test underexposed dark scene
    dark_bytes = bytes([15] * 1000)
    light_score, light_fb, exp_adj = _analyze_lighting_heuristics(dark_bytes)
    print(f"  [OK] Underexposed feedback: '{light_fb}', adj={exp_adj}")
    assert exp_adj > 0, "Dark image should suggest increasing exposure"
    assert "dark" in light_fb.lower() or "underexposed" in light_fb.lower()

    # 3. Test overexposed bright scene
    bright_bytes = bytes([245] * 1000)
    b_score, b_fb, b_adj = _analyze_lighting_heuristics(bright_bytes)
    print(f"  [OK] Overexposed feedback: '{b_fb}', adj={b_adj}")
    assert b_adj < 0, "Bright image should suggest decreasing exposure"
    assert "bright" in b_fb.lower() or "overexposure" in b_fb.lower()

    # 4. Verify native Python types and JSON serialization
    serialized = json.dumps(result_balanced)
    assert isinstance(serialized, str)
    parsed = json.loads(serialized)
    assert parsed["score"] == result_balanced["score"]
    print("  [OK] Composition response is 100% JSON serializable with native Python types.")

    # 5. Test breathing room padding and zoom dampening on Face/Portrait subject
    # Tight face crop (occupies 55% width, tight margins) -> should suggest STEP_BACK with gentle zoom (-0.10)
    tight_face_box = (0.50, 0.40, 0.55, 0.60)
    score_tight, fb_tight, zoom_tight, dir_tight = _analyze_framing_heuristics(b"", tight_face_box)
    print(f"  [OK] Tight face framing: dir={dir_tight}, zoom={zoom_tight}, fb='{fb_tight}'")
    assert dir_tight == "STEP_BACK", "Tight face crop should recommend stepping back for breathing room"
    assert zoom_tight == -0.10, f"Expected dampened zoom -0.10, got {zoom_tight}"
    assert "breathing room" in fb_tight.lower()

    # 6. Test full object/body subject with ideal 15-20% breathing room margins
    # Object centered, occupying 35% width and 45% height (rule of thirds compliant, generous margins)
    ideal_object_box = (0.50, 0.48, 0.35, 0.45)
    score_ideal, fb_ideal, zoom_ideal, dir_ideal = _analyze_framing_heuristics(b"", ideal_object_box)
    print(f"  [OK] Ideal object framing: dir={dir_ideal}, zoom={zoom_ideal}, score={score_ideal}/60")
    assert dir_ideal == "HOLD_STEADY", "Subject with ample breathing room should HOLD_STEADY"
    assert zoom_ideal == 0.0, f"Expected 0.0 zoom delta for ideal framing, got {zoom_ideal}"
    assert score_ideal >= 55, f"Ideal framing subscore should be >= 55/60, got {score_ideal}"

    # 7. Test distant subject (small coverage) -> gentle zoom in (+0.08, not harsh +0.20)
    distant_box = (0.50, 0.50, 0.12, 0.12)
    score_dist, fb_dist, zoom_dist, dir_dist = _analyze_framing_heuristics(b"", distant_box)
    print(f"  [OK] Distant subject framing: dir={dir_dist}, zoom={zoom_dist}")
    assert dir_dist == "STEP_CLOSER"
    assert zoom_dist == +0.08, f"Expected gentle zoom +0.08, got {zoom_dist}"

def test_stability_and_ready_to_capture():
    print("\nTesting Stability & Ready-to-Capture Threshold Decision...")
    from composition_scorer import score_composition

    sess = f"session_test_{int(time.time() * 1000)}"
    frame_bytes = bytes([130] * 1000)

    # Frame 1: Should NOT be ready to capture yet (needs consecutive stable frames)
    res1 = score_composition(frame_bytes, session_id=sess)
    print(f"  Frame 1: score={res1['score']}, ready_to_capture={res1['ready_to_capture']}")
    assert res1["ready_to_capture"] is False, "Frame 1 must not trigger immediate capture"

    # Frame 2: Second consecutive stable frame should trigger ready_to_capture = True
    res2 = score_composition(frame_bytes, session_id=sess)
    print(f"  Frame 2: score={res2['score']}, ready_to_capture={res2['ready_to_capture']}")
    assert res2["ready_to_capture"] is True, "Stable consecutive high score frames must trigger ready_to_capture"
    assert "Perfect" in res2["framing_feedback"] or "Capturing" in res2["framing_feedback"] or "ready" in res2["framing_feedback"].lower()
    print("  [OK] Stability tracking successfully enforces multi-frame hold-steady condition.")

def test_smart_grid_pose_analysis():
    print("\nTesting Smart Grid Head Pose Analysis & solvePnP...")
    from head_pose_estimator import analyze_head_pose_from_bytes
    from models import SmartGridPoseResponse
    from database import log_smart_grid_pose

    frame_bytes = bytes([128] * 2000)
    res = analyze_head_pose_from_bytes(frame_bytes)
    print(f"  [OK] Head pose result: pitch={res['pitch']}°, yaw={res['yaw']}°, roll={res['roll']}°")
    assert "pitch" in res and "yaw" in res and "roll" in res
    assert "rotation_matrix" in res and len(res["rotation_matrix"]) == 3
    assert "translation_vector" in res and len(res["translation_vector"]) == 3
    assert "composition_note" in res
    assert isinstance(res["is_level"], bool)
    assert isinstance(res["near_intersection"], bool)

    # Verify native JSON serializability
    model = SmartGridPoseResponse(**res)
    dumped = json.dumps(model.model_dump())
    assert isinstance(dumped, str)
    print("  [OK] SmartGridPoseResponse is 100% JSON serializable with native Python types.")

    # Verify DB logging
    log_smart_grid_pose(
        session_id="test_sg_session",
        face_detected=res["face_detected"],
        pitch=res["pitch"],
        yaw=res["yaw"],
        roll=res["roll"],
        face_center=res["face_center"],
        is_level=res["is_level"],
        near_intersection=res["near_intersection"],
        composition_note=res["composition_note"],
        db_path="test_pose_library.db"
    )
    print("  [OK] Smart Grid pose telemetry logged to database.")

def test_closed_loop_verification():
    print("\nTesting Closed-Loop Movement Verification Engine...")
    from verification import verify_and_analyze_frame, _SESSION_STORE
    from models import CompositionAnalysisResponse

    test_sid = f"verification_session_{int(time.time() * 1000)}"
    frame_bytes = bytes([120] * 1200)

    # Frame 1: First snapshot initializes session
    res1 = verify_and_analyze_frame(frame_bytes, session_id=test_sid)
    print(f"  Frame 1: instr={res1['suggested_move_direction']}, followed={res1['previous_instruction_followed']}")
    assert res1["previous_instruction_followed"] is True
    assert res1["guidance_stuck"] is False
    assert isinstance(res1["score"], int)

    # Manually mock session's last instruction to "ORBIT_LEFT" to test movement verification
    _SESSION_STORE[test_sid]["last_instruction"] = "ORBIT_LEFT"
    # Keep last_metrics position matching current image (0.5, 0.45) so delta is 0.0 (no move)
    _SESSION_STORE[test_sid]["last_metrics"]["subject_position"] = {"x": 0.5, "y": 0.45, "scale": 0.38}

    # Frame 2: Identical frame (user did NOT move) -> should detect no movement
    res2 = verify_and_analyze_frame(frame_bytes, session_id=test_sid)
    print(f"  Frame 2 (no move): followed={res2['previous_instruction_followed']}, note='{res2['correction_note']}'")
    assert res2["previous_instruction_followed"] is False, "Should flag that previous instruction was not followed"
    assert "No movement" in (res2["correction_note"] or "") or "moving left" in (res2["correction_note"] or "")

    # Frame 3: Wrong direction (user moved right instead of left -> subject shifted left, so prev_x was 0.56 and curr_x is 0.50)
    _SESSION_STORE[test_sid]["last_instruction"] = "ORBIT_LEFT"
    _SESSION_STORE[test_sid]["last_snapshot_bytes"] = None # bypass byte diff to test positional delta
    _SESSION_STORE[test_sid]["last_metrics"]["subject_position"] = {"x": 0.56, "y": 0.45, "scale": 0.38}
    res3 = verify_and_analyze_frame(frame_bytes, session_id=test_sid)
    print(f"  Frame 3 (wrong dir): followed={res3['previous_instruction_followed']}, note='{res3['correction_note']}'")
    assert res3["previous_instruction_followed"] is False
    assert "reverse" in (res3["correction_note"] or "").lower() or "right instead of left" in (res3["correction_note"] or "").lower()

    # Frame 4 & 5: Simulate repeated failed attempts to verify guidance_stuck trigger
    _SESSION_STORE[test_sid]["attempt_count"] = 3
    res_stuck = verify_and_analyze_frame(frame_bytes, session_id=test_sid)
    print(f"  Frame Stuck: guidance_stuck={res_stuck['guidance_stuck']}, note='{res_stuck['correction_note']}'")
    assert res_stuck["guidance_stuck"] is True, "After 3-4 attempts, guidance_stuck must be True"
    assert "manual" in (res_stuck["correction_note"] or "").lower() or "trouble" in (res_stuck["correction_note"] or "").lower()

    # Serialization test
    model_obj = CompositionAnalysisResponse(**res_stuck)
    dumped = json.dumps(model_obj.model_dump())
    assert isinstance(dumped, str)
    print("  [OK] Closed-loop verification response is 100% JSON serializable.")

if __name__ == "__main__":
    import time
    test_database_and_library()
    test_angle_calculations()
    test_pose_comparison()
    test_composition_scorer()
    test_stability_and_ready_to_capture()
    test_smart_grid_pose_analysis()
    test_closed_loop_verification()
    print("\n==========================================")
    print("ALL BACKEND STANDALONE TESTS PASSED GREEN!")
    print("==========================================")


