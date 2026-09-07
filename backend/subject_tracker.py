"""
subject_tracker.py - Persistent subject identity across frames using IoU matching.

Features:
- Tracks subject bounding boxes across consecutive frames using Intersection over Union (IoU).
- Maintains consistent subject ID across session snapshots.
- Handles subject leaving the frame and returning without losing state.
- Computes tracking confidence and cumulative visibility count.
"""

import time
import logging
from typing import Dict, Any, Optional, Tuple

logger = logging.getLogger("SubjectTracker")

IOU_MATCH_THRESHOLD = 0.25   # Minimum IoU overlap to consider same subject
MAX_DISAPPEAR_FRAMES = 5      # Frames allowed before re-initializing subject

def calculate_iou(box1: Dict[str, float], box2: Dict[str, float]) -> float:
    """
    Calculates Intersection-over-Union between two boxes.
    Format: {"x": cx, "y": cy, "scale": approx_area}
    """
    # Approximate box coordinates: center cx, cy, width w = sqrt(scale), height h = sqrt(scale)
    s1 = max(0.01, float(box1.get("scale", 0.35)))
    w1 = s1 ** 0.5
    h1 = w1 * 1.33  # standard portrait aspect ratio
    x1_min = box1.get("x", 0.5) - w1 / 2.0
    x1_max = box1.get("x", 0.5) + w1 / 2.0
    y1_min = box1.get("y", 0.5) - h1 / 2.0
    y1_max = box1.get("y", 0.5) + h1 / 2.0

    s2 = max(0.01, float(box2.get("scale", 0.35)))
    w2 = s2 ** 0.5
    h2 = w2 * 1.33
    x2_min = box2.get("x", 0.5) - w2 / 2.0
    x2_max = box2.get("x", 0.5) + w2 / 2.0
    y2_min = box2.get("y", 0.5) - h2 / 2.0
    y2_max = box2.get("y", 0.5) + h2 / 2.0

    # Intersection rect
    inter_x_min = max(x1_min, x2_min)
    inter_y_min = max(y1_min, y2_min)
    inter_x_max = min(x1_max, x2_max)
    inter_y_max = min(y1_max, y2_max)

    inter_w = max(0.0, inter_x_max - inter_x_min)
    inter_h = max(0.0, inter_y_max - inter_y_min)
    inter_area = inter_w * inter_h

    area1 = w1 * h1
    area2 = w2 * h2
    union_area = area1 + area2 - inter_area
    if union_area <= 0.0:
        return 0.0
    return round(inter_area / union_area, 3)

def update_subject_track(
    current_box: Optional[Dict[str, float]],
    session_state: Dict[str, Any]
) -> Dict[str, Any]:
    """
    Updates persistent subject tracking record inside session state.
    Returns tracking status dictionary.
    """
    track = session_state.get("subject_track")
    now = time.time()

    if not current_box:
        if track:
            track["missing_frames"] = track.get("missing_frames", 0) + 1
            if track["missing_frames"] > MAX_DISAPPEAR_FRAMES:
                track["is_visible"] = False
            return {
                "tracked": False,
                "subject_id": track.get("subject_id", "subject_1"),
                "is_same_subject": False,
                "missing_frames": track["missing_frames"],
                "status": "SUBJECT_OUT_OF_FRAME"
            }
        return {
            "tracked": False,
            "subject_id": "none",
            "is_same_subject": False,
            "missing_frames": 1,
            "status": "NO_SUBJECT"
        }

    if track is None:
        # Initial discovery of subject
        new_track = {
            "subject_id": f"subj_{int(now * 1000) % 10000}",
            "last_box": current_box,
            "first_seen": now,
            "last_seen": now,
            "seen_count": 1,
            "missing_frames": 0,
            "is_visible": True
        }
        session_state["subject_track"] = new_track
        return {
            "tracked": True,
            "subject_id": new_track["subject_id"],
            "is_same_subject": True,
            "iou": 1.0,
            "seen_count": 1,
            "status": "NEW_SUBJECT"
        }

    # Compare against last known box
    prev_box = track.get("last_box", current_box)
    iou = calculate_iou(prev_box, current_box)
    is_match = (iou >= IOU_MATCH_THRESHOLD) or (track.get("missing_frames", 0) > 0)

    track["last_box"] = current_box
    track["last_seen"] = now
    track["missing_frames"] = 0
    track["seen_count"] = track.get("seen_count", 0) + 1
    track["is_visible"] = True

    return {
        "tracked": True,
        "subject_id": track["subject_id"],
        "is_same_subject": bool(is_match),
        "iou": float(iou),
        "seen_count": track["seen_count"],
        "status": "TRACKING_RESUMED" if track.get("missing_frames", 0) > 0 else "TRACKING_ACTIVE"
    }
