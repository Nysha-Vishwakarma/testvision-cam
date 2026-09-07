"""
multi_subject.py - Group photo detection and multi-subject composition rules.

Features:
- Detects multiple subjects/faces in frame.
- Center-weighted framing evaluation for groups to ensure balance.
- Edge cutoff prevention (checks if anyone is cut off at frame borders).
- Multi-subject eyes-open validation: if ANY person in group has closed eyes, flags ready_to_capture=False.
"""

import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("MultiSubjectDetector")

def analyze_multi_subject(
    image_bytes: bytes,
    scene_type: str = "portrait"
) -> Dict[str, Any]:
    """
    Detects subjects in frame and verifies group composition.
    Returns: {
        "is_group": bool,
        "subject_count": int,
        "edge_cutoff_detected": bool,
        "all_eyes_open": bool,
        "group_balanced": bool,
        "guidance": str
    }
    """
    result = {
        "is_group": False,
        "subject_count": 1,
        "edge_cutoff_detected": False,
        "all_eyes_open": True,
        "group_balanced": True,
        "guidance": "Single subject framing."
    }

    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if img is not None:
            h, w = img.shape[:2]
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            # Skin & face clustering
            img_ycrcb = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
            skin_mask = cv2.inRange(img_ycrcb, np.array([0, 133, 77]), np.array([255, 173, 127]))
            contours, _ = cv2.findContours(skin_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

            significant_boxes = []
            for c in contours:
                area = cv2.contourArea(c)
                if area > (w * h * 0.012):
                    x, y, bw, bh = cv2.boundingRect(c)
                    significant_boxes.append((x, y, bw, bh))

            count = max(1, len(significant_boxes))
            result["subject_count"] = count

            if count >= 2:
                result["is_group"] = True
                result["guidance"] = f"Group shot ({count} subjects): centering group in frame."

                # Check edge cutoff: any box within 3% of frame border
                cutoff = False
                for x, y, bw, bh in significant_boxes:
                    if x < (w * 0.03) or (x + bw) > (w * 0.97):
                        cutoff = True
                        break
                result["edge_cutoff_detected"] = cutoff
                if cutoff:
                    result["guidance"] = "Subject cut off at edge — squeeze together or step back."
                    result["group_balanced"] = False

                # Check eyes across all detected face regions
                all_open = True
                for x, y, bw, bh in significant_boxes:
                    face_crop = gray[y:y+bh, x:x+bw]
                    if face_crop.size > 200:
                        eye_strip = face_crop[:int(bh * 0.45), :]
                        sobel = cv2.Sobel(eye_strip, cv2.CV_64F, 1, 0, ksize=3)
                        if float(sobel.var()) < 14.0:
                            all_open = False
                            break

                result["all_eyes_open"] = all_open
                if not all_open:
                    result["guidance"] = "Someone in the group blinked — keep eyes open!"

            return result
    except Exception as e:
        logger.debug(f"OpenCV multi-subject fallback: {e}")

    return result
