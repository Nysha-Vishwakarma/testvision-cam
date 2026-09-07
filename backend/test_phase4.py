#!/usr/bin/env python3
"""
Unit tests for all 15 Phase 4 AI Photographer backend intelligence modules.
"""
import sys
import os
import json
import time

sys.path.insert(0, os.path.dirname(__file__))

import stability
import lighting_advanced
import subject_tracker
import capture_timing
import preference_profile
import scene_classifier
import session_manager
import composition_rules
import distance_estimator
import multi_subject
import background_check
import golden_hour
import capture_variety
import confidence_gate
import capture_rate_limiter
from verification import verify_and_analyze_frame
from models import CompositionAnalysisResponse

def test_phase4_modules():
    print("Testing Phase 4 Intelligence Modules...")
    dummy_img = bytes([128] * 1600)

    # 1. Stability
    s_res = stability.evaluate_stability(dummy_img, prev_bytes=dummy_img)
    assert s_res["stable"] is True
    print("  [OK] stability.py passed")

    # 2. Lighting Advanced
    l_res = lighting_advanced.analyze_advanced_lighting(dummy_img, subject_box={"x": 0.5, "y": 0.5, "scale": 0.4})
    assert "is_backlit" in l_res
    print("  [OK] lighting_advanced.py passed")

    # 3. Subject Tracker
    track_session = {"subject_track": None}
    t_res = subject_tracker.update_subject_track({"x": 0.5, "y": 0.5, "scale": 0.4}, track_session)
    assert t_res["tracked"] is True
    assert "subject_id" in t_res
    print("  [OK] subject_tracker.py passed")

    # 4. Capture Timing
    time_res = capture_timing.evaluate_capture_timing(dummy_img, subject_type="portrait")
    assert "eyes_open" in time_res
    print("  [OK] capture_timing.py passed")

    # 5. Preference Profile
    user_p = preference_profile.get_user_profile("test_user")
    assert user_p["preferred_style"] == "balanced"
    print("  [OK] preference_profile.py passed")

    # 6. Scene Classifier
    sc_res = scene_classifier.classify_scene(dummy_img)
    assert "scene_type" in sc_res
    print("  [OK] scene_classifier.py passed")

    # 7. Session Manager
    sm = session_manager.SessionManager()
    sess = sm.get_or_create_session("sess_123")
    assert sess["session_id"] == "sess_123"
    print("  [OK] session_manager.py passed")

    # 8. Composition Rules
    cr_res = composition_rules.evaluate_composition_rules(dummy_img, {"x": 0.5, "y": 0.5, "scale": 0.4})
    assert "rule_score" in cr_res
    print("  [OK] composition_rules.py passed")

    # 9. Distance Estimator
    de_res = distance_estimator.estimate_subject_distance({"x": 0.5, "y": 0.5, "scale": 0.35})
    assert de_res["distance_category"] == "medium_shot"
    print("  [OK] distance_estimator.py passed")

    # 10. Multi Subject
    ms_res = multi_subject.analyze_multi_subject(dummy_img, scene_type="portrait")
    assert "subject_count" in ms_res
    assert "all_eyes_open" in ms_res
    print("  [OK] multi_subject.py passed")

    # 11. Background Check
    bg_res = background_check.analyze_background_clutter(dummy_img, {"x": 0.5, "y": 0.5, "scale": 0.4})
    assert "clutter_score" in bg_res
    print("  [OK] background_check.py passed")

    # 12. Golden Hour
    gh_res = golden_hour.evaluate_lighting_quality(False, "warm", time.time())
    assert "lighting_quality" in gh_res
    print("  [OK] golden_hour.py passed")

    # 13. Capture Variety
    cv_res = capture_variety.get_post_capture_variety(1, "portrait")
    assert "next_framing_suggestion" in cv_res
    print("  [OK] capture_variety.py passed")

    # 14. Confidence Gate
    cg_res = confidence_gate.evaluate_confidence_gate(85, 80, is_stable=True, eyes_open=True)
    assert cg_res["is_trusted"] is True
    print("  [OK] confidence_gate.py passed")

    # 15. Capture Rate Limiter
    c_state = {"last_capture_time": 0.0, "consecutive_ready_count": 1, "post_capture_active": False}
    c_check = capture_rate_limiter.evaluate_capture_permission(True, c_state, time.time())
    assert c_check["trigger_capture"] is True
    print("  [OK] capture_rate_limiter.py passed")

    # Pipeline integration test
    res = verify_and_analyze_frame(dummy_img, session_id="test_pipeline_sid")
    assert isinstance(res["score"], int)
    assert "stability" in res
    assert "confidence" in res
    assert "composition_rules" in res
    assert "distance" in res

    response_model = CompositionAnalysisResponse(**res)
    dumped = json.dumps(response_model.model_dump())
    assert len(dumped) > 50
    print("  [OK] Full Phase 4 pipeline end-to-end integration verified!")
    print("\nALL 15 PHASE 4 MODULES VERIFIED & WORKING!")

if __name__ == "__main__":
    test_phase4_modules()
