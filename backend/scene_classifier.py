"""
scene_classifier.py - Cheap pre-check classifying rough scene type.

Classes:
- 'portrait': Single person / face dominant
- 'group': Multiple people / faces in frame
- 'object': Centered static product / object
- 'landscape': Wide horizon / panoramic view

Features:
- Determines composition rule set per scene type (Rule-of-Thirds, Golden Ratio, Centered, Symmetry).
- Computes confidence score (0-100).
- If confidence >= 88, can skip expensive multimodal API calls entirely!
"""

import logging
from typing import Dict, Any, Tuple

logger = logging.getLogger("SceneClassifier")

CONFIDENCE_SKIP_API_THRESHOLD = 88

def classify_scene(image_bytes: bytes) -> Dict[str, Any]:
    """
    Classifies the rough scene type and recommended composition rule.
    Fast local computer vision heuristic check (< 10ms).
    """
    result = {
        "scene_type": "object",
        "confidence": 65,
        "rule_set": "rule_of_thirds",
        "skip_vision_api": False,
        "details": "Standard object / scene composition."
    }

    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if img is not None:
            h, w = img.shape[:2]
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            # 1. Edge & Horizon line analysis for landscape detection
            edges = cv2.Canny(gray, 50, 150)
            lines = cv2.HoughLinesP(edges, 1, np.pi / 180, threshold=80, minLineLength=int(w * 0.4), maxLineGap=20)
            horizontal_lines = 0
            if lines is not None:
                for line in lines:
                    x1, y1, x2, y2 = line[0]
                    angle = abs(np.arctan2(y2 - y1, x2 - x1) * 180.0 / np.pi)
                    if angle < 10.0 or angle > 170.0:
                        horizontal_lines += 1

            # 2. Central skin color / face region heuristic (YCrCb range for skin)
            img_ycrcb = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
            skin_mask = cv2.inRange(img_ycrcb, np.array([0, 133, 77]), np.array([255, 173, 127]))
            skin_ratio = float(np.count_nonzero(skin_mask)) / float(w * h)

            # Find distinct skin clusters for single vs group detection
            contours, _ = cv2.findContours(skin_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            significant_faces = [c for c in contours if cv2.contourArea(c) > (w * h * 0.015)]

            if len(significant_faces) >= 2:
                result["scene_type"] = "group"
                result["confidence"] = 90
                result["rule_set"] = "symmetry_and_balance"
                result["details"] = f"Group scene with {len(significant_faces)} subjects detected."
                result["skip_vision_api"] = True
            elif len(significant_faces) == 1 or (skin_ratio > 0.08 and skin_ratio < 0.45):
                result["scene_type"] = "portrait"
                result["confidence"] = 92
                result["rule_set"] = "rule_of_thirds_headroom"
                result["details"] = "Portrait / person subject detected."
                result["skip_vision_api"] = True
            elif horizontal_lines >= 2 and skin_ratio < 0.02:
                result["scene_type"] = "landscape"
                result["confidence"] = 88
                result["rule_set"] = "horizon_leveling"
                result["details"] = "Landscape scene with prominent horizon."
                result["skip_vision_api"] = True
            else:
                result["scene_type"] = "object"
                result["confidence"] = 75
                result["rule_set"] = "rule_of_thirds"
                result["details"] = "Object or still-life subject."
                result["skip_vision_api"] = False

            return result
    except Exception as e:
        logger.debug(f"OpenCV scene classification fallback: {e}")

    return result
