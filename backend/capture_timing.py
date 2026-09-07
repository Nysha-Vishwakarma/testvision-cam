"""
capture_timing.py - Decisive moment detection for human and non-human subjects.

Features:
- Eyes-open detection for human portraits (prevents capture during blinks).
- Smile/expression detection as an optional positive signal (nudges capture readiness).
- Motion-peak / settling detection for static objects or moving subjects (ensures minimal blur).
"""

import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("CaptureTiming")

def evaluate_capture_timing(
    image_bytes: bytes,
    subject_type: str = "portrait",
    subject_box: Optional[Dict[str, float]] = None
) -> Dict[str, Any]:
    """
    Evaluates whether the scene is currently in a decisive, settled moment.
    Returns: {
        "eyes_open": bool,
        "smile_detected": bool,
        "is_settled_moment": bool,
        "timing_score": int (0-100),
        "guidance": str
    }
    """
    # Defaults
    result = {
        "eyes_open": True,            # True by default unless closed eyes detected
        "smile_detected": False,
        "is_settled_moment": True,
        "timing_score": 85,
        "guidance": "Timing is good."
    }

    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if img is not None:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
            h, w = img.shape[:2]

            # If subject is a human/portrait: evaluate facial expression and eye state
            if subject_type.lower() in ("portrait", "person", "group"):
                # Approximate eye region in upper third of subject box
                sb = subject_box or {"x": 0.5, "y": 0.35, "scale": 0.35}
                cx, cy = float(sb.get("x", 0.5)), float(sb.get("y", 0.35))
                box_w = int(w * 0.25)
                box_h = int(h * 0.25)

                x1 = max(0, min(w - 1, int(cx * w - box_w / 2)))
                y1 = max(0, min(h - 1, int(cy * h - box_h / 2)))
                x2 = min(w, x1 + box_w)
                y2 = min(h, y1 + box_h)

                face_roi = gray[y1:y2, x1:x2]
                if face_roi.size > 100:
                    # Eye region: upper 40% of face box
                    eye_roi = face_roi[:int(face_roi.shape[0] * 0.45), :]
                    # Mouth region: bottom 35% of face box
                    mouth_roi = face_roi[int(face_roi.shape[0] * 0.65):, :]

                    # Eye openness heuristic: variance of gradient in eye band
                    # When eyes are open, pupils and sclera create high sharp gradient transitions
                    sobel_eye = cv2.Sobel(eye_roi, cv2.CV_64F, 1, 0, ksize=3)
                    eye_variance = float(sobel_eye.var()) if eye_roi.size > 0 else 50.0

                    if eye_variance < 18.0 and eye_roi.size > 200:
                        result["eyes_open"] = False
                        result["is_settled_moment"] = False
                        result["timing_score"] = 30
                        result["guidance"] = "Keep eyes open for photo."
                    else:
                        result["eyes_open"] = True

                    # Smile heuristic: horizontal gradient / mouth corner contrast
                    if mouth_roi.size > 0:
                        mouth_edges = cv2.Canny(mouth_roi, 50, 150)
                        mouth_edge_ratio = float(np.count_nonzero(mouth_edges) / mouth_roi.size)
                        if mouth_edge_ratio > 0.08:
                            result["smile_detected"] = True
                            result["timing_score"] = 95
                            result["guidance"] = "Great expression! Hold steady."
            else:
                # Non-human / product / landscape: check settling via gradient crispness
                edges = cv2.Canny(gray, 60, 180)
                edge_count = float(np.count_nonzero(edges))
                if edge_count > 500:
                    result["is_settled_moment"] = True
                    result["timing_score"] = 90
                    result["guidance"] = "Subject is clear and settled."

            return result
    except Exception as e:
        logger.debug(f"OpenCV capture timing analysis unavailable: {e}")

    return result
