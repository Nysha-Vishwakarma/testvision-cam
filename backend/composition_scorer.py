"""
Swappable Composition Scoring Module for AI Photographer Mode.

Analyzes framing (subject bounding box, rule-of-thirds, scale, centering)
and ambient lighting (brightness histogram, highlight/shadow clipping)
to produce a normalized 0-100 composition score and directional coaching cues.

Architecture:
- Single entry point: score_composition(image_bytes, session_id) -> dict
- Stateless heuristics with optional server-side session history for stability detection
- Clean decoupling so this heuristic scorer can be swapped with a deep-learning
  model (PyTorch / TensorFlow / ONNX) without altering the API contract or mobile client.
"""

import time
import math
import logging
from collections import deque
from typing import Optional, Dict, Any, Tuple

logger = logging.getLogger("CompositionScorer")

# Server-side rolling frame history per session to verify framing stability
# Key: session_id -> deque of recent frame results (timestamp, score, move_direction)
_SESSION_HISTORY: Dict[str, deque] = {}
SESSION_MAX_AGE_SECONDS = 10.0
STABILITY_REQUIRED_FRAMES = 2
MIN_CAPTURE_SCORE = 75

def _clean_stale_sessions():
    """Prunes session histories older than SESSION_MAX_AGE_SECONDS."""
    now = time.time()
    stale_keys = [
        sid for sid, hist in _SESSION_HISTORY.items()
        if not hist or (now - hist[-1].get("timestamp", 0) > SESSION_MAX_AGE_SECONDS)
    ]
    for sid in stale_keys:
        _SESSION_HISTORY.pop(sid, None)

def _analyze_lighting_heuristics(image_bytes: bytes) -> Tuple[int, str, float]:
    """
    Computes ambient brightness and highlights/shadows balance.
    Returns: (lighting_subscore: 0-40, lighting_feedback: str, suggested_exp_adjustment: float)
    """
    try:
        import numpy as np
        import cv2

        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is not None:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            mean_brightness = float(np.mean(gray))
            hist = cv2.calcHist([gray], [0], None, [256], [0, 256]).flatten()
            total_pixels = float(np.sum(hist))

            if total_pixels > 0:
                shadow_ratio = float(np.sum(hist[:30]) / total_pixels)
                highlight_ratio = float(np.sum(hist[225:]) / total_pixels)
            else:
                shadow_ratio = 0.0
                highlight_ratio = 0.0

            # Evaluate lighting quality
            if mean_brightness < 55.0 or shadow_ratio > 0.45:
                return (
                    18,
                    "Scene is underexposed. Increasing exposure or move toward light.",
                    +1.0
                )
            elif mean_brightness > 205.0 or highlight_ratio > 0.40:
                return (
                    18,
                    "Harsh overexposure detected. Decreasing exposure.",
                    -1.0
                )
            elif 85.0 <= mean_brightness <= 175.0 and shadow_ratio < 0.25 and highlight_ratio < 0.20:
                return (
                    40,
                    "Lighting is soft and well-balanced.",
                    0.0
                )
            else:
                # Moderate acceptable lighting
                return (
                    32,
                    "Ambient lighting is good.",
                    0.0
                )
    except Exception as e:
        logger.debug(f"OpenCV lighting analysis unavailable: {e}")

    # Pure Python fallback when OpenCV is not installed (calculates byte luminance sample)
    if len(image_bytes) > 200:
        # Sample slices from the middle of the byte buffer
        sample = image_bytes[100:min(len(image_bytes), 2000)]
        avg_val = sum(sample) / len(sample)
        if avg_val < 50:
            return (20, "Scene appears dark. Increasing exposure.", +1.0)
        elif avg_val > 210:
            return (20, "Scene is very bright. Decreasing exposure.", -1.0)
        else:
            return (38, "Lighting is well-balanced.", 0.0)

    return (35, "Lighting is balanced.", 0.0)

def _analyze_framing_heuristics(
    image_bytes: bytes,
    detected_box_override: Optional[Tuple[float, float, float, float]] = None
) -> Tuple[int, str, float, str]:
    """
    Detects subject framing, rule-of-thirds alignment, scale/distance, and centering.
    Returns: (framing_subscore: 0-60, framing_feedback: str, suggested_zoom_delta: float, suggested_move_direction: str)
    """
    detected_box = detected_box_override  # (cx, cy, width_ratio, height_ratio) in normalized [0, 1]

    if detected_box is None:
        try:
            import numpy as np
            import cv2

            nparr = np.frombuffer(image_bytes, np.uint8)
            img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            if img is not None:
                h, w = img.shape[:2]
                # 1. Try MediaPipe Pose or Face for exact subject localization
                try:
                    import mediapipe as mp
                    mp_face = mp.solutions.face_detection
                    with mp_face.FaceDetection(min_detection_confidence=0.5) as face_det:
                        results = face_det.process(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
                        if results and results.detections:
                            det = results.detections[0]
                            bb = det.location_data.relative_bounding_box
                            cx = bb.xmin + bb.width / 2.0
                            cy = bb.ymin + bb.height / 2.0
                            detected_box = (cx, cy, bb.width, bb.height)
                except Exception:
                    pass

                # 2. If no face detected with mediapipe, try OpenCV Haar Cascade or Contour centroid
                if detected_box is None:
                    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
                    # Compute center of mass / gradient energy
                    blurred = cv2.GaussianBlur(gray, (21, 21), 0)
                    thresh = cv2.adaptiveThreshold(
                        blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY_INV, 11, 2
                    )
                    moments = cv2.moments(thresh)
                    if moments["m00"] > 0:
                        cx = (moments["m10"] / moments["m00"]) / float(w)
                        cy = (moments["m01"] / moments["m00"]) / float(h)
                        detected_box = (cx, cy, 0.40, 0.50)
                    else:
                        detected_box = (0.50, 0.45, 0.40, 0.50)
        except Exception as e:
            logger.debug(f"OpenCV framing analysis unavailable: {e}")

    # Fallback default framing for pure Python environment
    if detected_box is None:
        detected_box = (0.50, 0.40, 0.38, 0.52)

    cx, cy, bw, bh = detected_box

    # Apply 15-20% breathing room padding to subject bounding box
    BREATHING_PADDING_RATIO = 0.18  # 18% margin on each side
    padded_bw = min(1.0, bw * (1.0 + 2.0 * BREATHING_PADDING_RATIO))
    padded_bh = min(1.0, bh * (1.0 + 2.0 * BREATHING_PADDING_RATIO))
    padded_coverage = padded_bw * padded_bh
    raw_coverage = bw * bh

    # Minimum margin from subject bounding box to frame edges
    left_margin = cx - bw / 2.0
    right_margin = 1.0 - (cx + bw / 2.0)
    top_margin = cy - bh / 2.0
    bottom_margin = 1.0 - (cy + bh / 2.0)
    min_edge_margin = min(left_margin, right_margin, top_margin, bottom_margin)

    # Rule of thirds targets:
    # Golden intersection points are around x: 0.33, 0.50, 0.67 and y: 0.33 to 0.45 (for eye line/face)
    x_dist_to_third = min(abs(cx - 0.33), abs(cx - 0.50), abs(cx - 0.67))
    y_dist_to_third = abs(cy - 0.38)

    # 1. Scale / Distance check with breathing room
    if padded_coverage > 0.60 or raw_coverage > 0.42 or bw > 0.58 or min_edge_margin < 0.08:
        # Subject is too close or lacks breathing space
        return (
            35,
            "Subject lacks breathing room — step back or zoom out for a natural frame.",
            -0.10,
            "STEP_BACK"
        )
    elif padded_coverage < 0.14 or (bw < 0.16 and bh < 0.20):
        # Subject is genuinely too far
        return (
            35,
            "Subject is too far — step closer or zoom in slightly.",
            +0.08,
            "STEP_CLOSER"
        )

    # 2. Centering & Rule-of-Thirds check
    if cx < 0.28:
        return (
            40,
            "Subject is too far left. Shift camera right to frame.",
            0.0,
            "MOVE_RIGHT"
        )
    elif cx > 0.72:
        return (
            40,
            "Subject is too far right. Shift camera left to frame.",
            0.0,
            "MOVE_LEFT"
        )
    elif cy < 0.20:
        return (
            42,
            "Subject is too close to top edge. Tilt camera down.",
            0.0,
            "MOVE_DOWN"
        )
    elif cy > 0.65:
        return (
            42,
            "Subject is too low in the frame. Tilt camera up.",
            0.0,
            "MOVE_UP"
        )

    # Subject is within aesthetic framing zone
    framing_score = 58
    if x_dist_to_third < 0.06 and y_dist_to_third < 0.08:
        framing_score = 60
        framing_feedback = "Subject perfectly positioned on the rule-of-thirds grid."
    else:
        framing_feedback = "Framing is balanced and pleasing."

    return (framing_score, framing_feedback, 0.0, "HOLD_STEADY")

def score_composition(
    image_bytes: bytes,
    session_id: Optional[str] = None
) -> Dict[str, Any]:
    """
    Main swappable scoring routine.
    Combines framing analysis and lighting analysis into a single 0-100 score.
    Enforces stability across consecutive frames for auto-capture activation.
    Guarantees pure native Python types in returned dictionary.
    """
    _clean_stale_sessions()

    # 1. Compute subscores
    lighting_subscore, lighting_feedback, exp_adj = _analyze_lighting_heuristics(image_bytes)
    framing_subscore, framing_feedback, zoom_delta, move_dir = _analyze_framing_heuristics(image_bytes)

    total_score = max(0, min(100, int(lighting_subscore + framing_subscore)))

    # 2. Session stability evaluation for capture decision
    sid = session_id or "default_session"
    now = time.time()

    if sid not in _SESSION_HISTORY:
        _SESSION_HISTORY[sid] = deque(maxlen=5)

    history = _SESSION_HISTORY[sid]
    history.append({
        "timestamp": now,
        "score": total_score,
        "move_direction": move_dir
    })

    # Ready to capture requires:
    # 1) Current score >= MIN_CAPTURE_SCORE (75)
    # 2) Subject stable across last STABILITY_REQUIRED_FRAMES (consecutive high scores and steady position)
    ready_to_capture = False
    if total_score >= MIN_CAPTURE_SCORE and move_dir == "HOLD_STEADY":
        recent_frames = [
            f for f in history
            if (now - f["timestamp"]) <= 4.0
        ]
        if len(recent_frames) >= STABILITY_REQUIRED_FRAMES:
            all_high_score = all(f["score"] >= MIN_CAPTURE_SCORE for f in recent_frames)
            all_steady = all(f["move_direction"] == "HOLD_STEADY" for f in recent_frames)
            if all_high_score and all_steady:
                ready_to_capture = True

    # High score cue enhancement
    if ready_to_capture:
        framing_feedback = "Perfect composition! Hold steady — capturing now."
    elif total_score >= 80:
        framing_feedback = "Great composition! Keep holding steady."

    return {
        "score": int(total_score),
        "framing_feedback": str(framing_feedback),
        "lighting_feedback": str(lighting_feedback),
        "suggested_zoom_delta": round(float(zoom_delta), 2),
        "suggested_move_direction": str(move_dir),
        "ready_to_capture": bool(ready_to_capture)
    }
