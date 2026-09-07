"""
distance_estimator.py - Subject-to-camera distance estimation and framing depth guidance.

Features:
- Estimates relative distance category: 'close_up', 'medium_shot', 'full_body', 'too_far'.
- Computes approximate distance in meters based on subject bounding box scale and aspect ratio.
- Provides distance-aware coaching (e.g. "Step back for a full-body shot" vs "Too far for a portrait").
"""

import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("DistanceEstimator")

def estimate_subject_distance(
    subject_box: Optional[Dict[str, float]],
    scene_type: str = "portrait"
) -> Dict[str, Any]:
    """
    Estimates subject distance and provides framing scale guidance.
    subject_box format: {"x": cx, "y": cy, "scale": approx_area (0..1)}
    """
    sb = subject_box or {"x": 0.5, "y": 0.5, "scale": 0.35}
    scale = max(0.01, min(1.0, float(sb.get("scale", 0.35))))

    # Approximate distance scaling (relative meters assuming standard mobile lens FOV)
    # Area scale = 0.60 -> ~0.5m (close-up)
    # Area scale = 0.35 -> ~1.2m (bust/medium)
    # Area scale = 0.12 -> ~2.8m (full-body)
    # Area scale < 0.05 -> ~5.0m (far)
    approx_meters = round(0.7 / (scale ** 0.5), 1)

    if scale >= 0.55:
        category = "close_up"
        guidance = "Subject is very close — step back if you want more background context."
        zoom_recommendation = -0.1
    elif 0.25 <= scale < 0.55:
        category = "medium_shot"
        guidance = "Distance is ideal for a medium portrait."
        zoom_recommendation = 0.0
    elif 0.08 <= scale < 0.25:
        category = "full_body"
        guidance = "Great distance for a full-length outfit shot."
        zoom_recommendation = 0.0
    else:
        category = "too_far"
        guidance = "Subject is too far away — step closer or zoom in."
        zoom_recommendation = +0.15

    return {
        "distance_category": category,
        "approx_distance_meters": approx_meters,
        "subject_scale": round(scale, 3),
        "guidance": guidance,
        "zoom_recommendation": zoom_recommendation
    }
