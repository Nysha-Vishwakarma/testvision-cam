"""
stability.py - Motion, shake, and blur pre-screening before full scene analysis.

Responsibilities:
- Fast frame-difference check using OpenCV (or luminance array delta fallback).
- Optional sensor telemetry check (e.g. gyroscope/accelerometer norm).
- Blurry frame detection using Laplacian variance.
- Returns lightweight payload {"stable": bool, "motion_score": float, "blur_score": float, "feedback": str}.
- Invariant: `ready_to_capture` must NEVER be True when `stable` is False.
"""

import logging
from typing import Optional, Dict, Any, Tuple

logger = logging.getLogger("StabilityDetector")

# Thresholds for motion and blur detection
DEFAULT_MOTION_THRESHOLD = 0.22      # Normalized frame-difference percentage
DEFAULT_BLUR_THRESHOLD = 35.0        # Laplacian variance threshold (higher = sharper)

def evaluate_stability(
    curr_bytes: bytes,
    prev_bytes: Optional[bytes] = None,
    device_motion: Optional[Dict[str, float]] = None,
    motion_threshold: float = DEFAULT_MOTION_THRESHOLD,
    blur_threshold: float = DEFAULT_BLUR_THRESHOLD
) -> Dict[str, Any]:
    """
    Evaluates whether the device and scene are steady enough for analysis and capture.
    Fast pre-screening check designed to reject shaky frames in < 5ms.
    """
    motion_score = 0.0
    blur_score = 100.0  # Assumed sharp by default
    is_motion_unstable = False
    is_blurry = False
    feedback = "Camera is steady."

    # 1. Device motion sensor check (if provided by client e.g. Expo gyroscope/accelerometer)
    if device_motion:
        accel_mag = abs(device_motion.get("x", 0.0)) + abs(device_motion.get("y", 0.0)) + abs(device_motion.get("z", 0.0))
        rot_rate = abs(device_motion.get("rot_x", 0.0)) + abs(device_motion.get("rot_y", 0.0)) + abs(device_motion.get("rot_z", 0.0))
        if accel_mag > 3.5 or rot_rate > 1.8:
            is_motion_unstable = True
            motion_score = max(motion_score, 0.8)
            feedback = "Hold steady — device movement detected."

    # 2. OpenCV Frame Difference & Blur Analysis
    try:
        import numpy as np
        import cv2

        curr_arr = np.frombuffer(curr_bytes, np.uint8)
        curr_img = cv2.imdecode(curr_arr, cv2.IMREAD_COLOR)

        if curr_img is not None:
            gray_curr = cv2.cvtColor(curr_img, cv2.COLOR_BGR2GRAY)

            # Blur evaluation via Laplacian variance
            laplacian = cv2.Laplacian(gray_curr, cv2.CV_64F)
            blur_score = float(laplacian.var())
            if blur_score < blur_threshold:
                is_blurry = True
                feedback = "Hold steady — image appears blurry or in motion."

            # Inter-frame motion difference
            if prev_bytes is not None:
                prev_arr = np.frombuffer(prev_bytes, np.uint8)
                prev_img = cv2.imdecode(prev_arr, cv2.IMREAD_COLOR)
                if prev_img is not None and prev_img.shape == curr_img.shape:
                    gray_prev = cv2.cvtColor(prev_img, cv2.COLOR_BGR2GRAY)
                    diff = cv2.absdiff(gray_curr, gray_prev)
                    _, thresh = cv2.threshold(diff, 25, 255, cv2.THRESH_BINARY)
                    motion_pixels = float(np.count_nonzero(thresh))
                    total_pixels = float(thresh.size)
                    motion_score = round(motion_pixels / total_pixels, 3)

                    if motion_score > motion_threshold:
                        is_motion_unstable = True
                        feedback = "Hold steady — scene or camera is moving."
    except Exception as e:
        logger.debug(f"OpenCV stability analysis unavailable, falling back: {e}")
        # Pure Python fallback: check sample variance from byte stream
        if prev_bytes and len(curr_bytes) > 200 and len(prev_bytes) > 200:
            sample_len = min(len(curr_bytes), len(prev_bytes), 2000)
            diff_sum = sum(abs(curr_bytes[i] - prev_bytes[i]) for i in range(100, sample_len, 4))
            avg_diff = diff_sum / ((sample_len - 100) / 4)
            motion_score = round(min(1.0, avg_diff / 80.0), 3)
            if motion_score > 0.40:
                is_motion_unstable = True
                feedback = "Hold steady."

    is_stable = not (is_motion_unstable or is_blurry)

    return {
        "stable": bool(is_stable),
        "motion_score": float(motion_score),
        "blur_score": round(float(blur_score), 2),
        "feedback": str(feedback)
    }
