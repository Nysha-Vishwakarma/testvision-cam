"""
lighting_advanced.py - Advanced lighting analysis module.

Features:
- Backlighting detection: Compares subject-region brightness against background-region brightness.
- Harsh shadow detection: Checks local contrast and shadow gradient across the subject bounding box.
- White balance / Color temperature flag: Evaluates color channel balance (Red/Blue ratio).
- Returns specific, actionable guidance strings (e.g. "Subject is backlit — face toward the light source").
"""

import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("LightingAdvanced")

def analyze_advanced_lighting(
    image_bytes: bytes,
    subject_box: Optional[Dict[str, float]] = None
) -> Dict[str, Any]:
    """
    Performs multi-zone illumination analysis on the image bytes.
    subject_box format: {"x": center_x, "y": center_y, "scale": approx_scale} in normalized coordinates (0..1).
    """
    # Defaults
    result = {
        "is_backlit": False,
        "has_harsh_shadows": False,
        "color_temperature": "neutral",  # "warm", "cool", "neutral"
        "subject_brightness": 128.0,
        "background_brightness": 128.0,
        "contrast_ratio": 1.0,
        "actionable_guidance": "Lighting is well balanced.",
        "suggested_exposure_delta": 0
    }

    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)

        if img is not None:
            h, w = img.shape[:2]
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            # Color temperature estimation via BGR channel averages
            b_mean = float(np.mean(img[:, :, 0]))
            g_mean = float(np.mean(img[:, :, 1]))
            r_mean = float(np.mean(img[:, :, 2]))

            if r_mean > (b_mean * 1.25) and r_mean > 130:
                result["color_temperature"] = "warm"
            elif b_mean > (r_mean * 1.25) and b_mean > 130:
                result["color_temperature"] = "cool"
            else:
                result["color_temperature"] = "neutral"

            # Parse subject box or default to center third
            sb = subject_box or {"x": 0.5, "y": 0.5, "scale": 0.35}
            cx = float(sb.get("x", 0.5))
            cy = float(sb.get("y", 0.5))
            scale = float(sb.get("scale", 0.35))
            box_w = max(20, int(w * (scale ** 0.5)))
            box_h = max(20, int(h * (scale ** 0.5)))

            x1 = max(0, min(w - 1, int(cx * w - box_w / 2)))
            y1 = max(0, min(h - 1, int(cy * h - box_h / 2)))
            x2 = max(x1 + 10, min(w, x1 + box_w))
            y2 = max(y1 + 10, min(h, y1 + box_h))

            subject_roi = gray[y1:y2, x1:x2]

            # Background mask (all pixels outside subject_roi)
            mask = np.ones((h, w), dtype=np.uint8) * 255
            mask[y1:y2, x1:x2] = 0
            bg_pixels = gray[mask == 255]

            subj_mean = float(np.mean(subject_roi)) if subject_roi.size > 0 else 128.0
            bg_mean = float(np.mean(bg_pixels)) if bg_pixels.size > 0 else 128.0

            result["subject_brightness"] = round(subj_mean, 1)
            result["background_brightness"] = round(bg_mean, 1)

            # 1. Backlighting Check: background significantly brighter than subject
            if bg_mean > 160.0 and subj_mean < 110.0 and (bg_mean - subj_mean) > 50.0:
                result["is_backlit"] = True
                result["actionable_guidance"] = "Subject is backlit — face toward the light source or enable fill light."
                result["suggested_exposure_delta"] = +1

            # 2. Harsh Shadow Check: high internal standard deviation & dark patches within subject
            elif subject_roi.size > 0:
                subj_std = float(np.std(subject_roi))
                dark_patch_ratio = float(np.count_nonzero(subject_roi < 45) / subject_roi.size)
                bright_patch_ratio = float(np.count_nonzero(subject_roi > 215) / subject_roi.size)

                if subj_std > 58.0 and dark_patch_ratio > 0.20 and bright_patch_ratio > 0.15:
                    result["has_harsh_shadows"] = True
                    result["actionable_guidance"] = "Harsh shadows detected across subject — diffuse lighting or turn slightly."
                    result["suggested_exposure_delta"] = 0
                elif subj_mean < 60.0:
                    result["actionable_guidance"] = "Scene appears underexposed — increase lighting on subject."
                    result["suggested_exposure_delta"] = +1
                elif subj_mean > 200.0:
                    result["actionable_guidance"] = "Subject is overexposed — step into softer lighting."
                    result["suggested_exposure_delta"] = -1
                else:
                    result["actionable_guidance"] = "Lighting is soft and well-balanced."

            return result
    except Exception as e:
        logger.debug(f"OpenCV advanced lighting fallback: {e}")

    # Pure Python Fallback
    if len(image_bytes) > 500:
        sample = image_bytes[100:2000]
        avg = sum(sample) / len(sample)
        result["subject_brightness"] = round(avg, 1)
        if avg < 60:
            result["actionable_guidance"] = "Low light detected — move to a brighter location."
            result["suggested_exposure_delta"] = +1
        elif avg > 200:
            result["actionable_guidance"] = "Harsh glare detected — adjust camera angle away from bright light."
            result["suggested_exposure_delta"] = -1

    return result
