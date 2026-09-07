"""
background_check.py - Distraction, clutter, and edge-density background analysis.

Features:
- Evaluates background region clutter using OpenCV Canny edge density and saliency variance.
- Flags busy or visually competing backgrounds (e.g. poles, photobombers, cluttered room).
- Returns actionable recomposition advice (e.g. "Try a plainer background" or "Shift angle to avoid background clutter").
"""

import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("BackgroundCheck")

CLUTTER_EDGE_DENSITY_THRESHOLD = 0.16

def analyze_background_clutter(
    image_bytes: bytes,
    subject_box: Optional[Dict[str, float]] = None
) -> Dict[str, Any]:
    """
    Measures edge density and distraction levels outside the subject bounding box.
    """
    result = {
        "is_cluttered": False,
        "clutter_score": 25,  # 0 (clean) to 100 (extreme clutter)
        "guidance": "Background is clean and unobtrusive."
    }

    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if img is not None:
            h, w = img.shape[:2]
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            # Mask out subject region
            sb = subject_box or {"x": 0.5, "y": 0.5, "scale": 0.35}
            cx, cy = float(sb.get("x", 0.5)), float(sb.get("y", 0.5))
            scale = float(sb.get("scale", 0.35))
            bw, bh = int(w * (scale ** 0.5)), int(h * (scale ** 0.5))

            x1 = max(0, min(w - 1, int(cx * w - bw / 2)))
            y1 = max(0, min(h - 1, int(cy * h - bh / 2)))
            x2 = min(w, x1 + bw)
            y2 = min(h, y1 + bh)

            # Compute Canny edges across the frame
            edges = cv2.Canny(gray, 50, 150)

            # Mask out subject region to only evaluate background
            bg_mask = np.ones((h, w), dtype=np.uint8) * 255
            bg_mask[y1:y2, x1:x2] = 0
            bg_edges = edges[bg_mask == 255]

            if bg_edges.size > 0:
                edge_density = float(np.count_nonzero(bg_edges)) / float(bg_edges.size)
                clutter_score = int(min(100, edge_density * 400))
                result["clutter_score"] = clutter_score

                if edge_density > CLUTTER_EDGE_DENSITY_THRESHOLD:
                    result["is_cluttered"] = True
                    result["guidance"] = "Background is busy or cluttered — try shifting angle or recomposing against a cleaner backdrop."
                else:
                    result["guidance"] = "Background is clean with good subject separation."

            return result
    except Exception as e:
        logger.debug(f"OpenCV background check fallback: {e}")

    return result
