"""
golden_hour.py - Lighting quality, time-of-day, and ambient sunlight awareness.

Features:
- Flags favorable vs harsh lighting periods based on capture timestamp (or local hour).
- Detects harsh midday overhead sunlight (steep downward shadow angle + high contrast).
- Feeds soft, aesthetic tips into the lighting feedback pipeline without contradicting hardware exposure.
"""

import time
import datetime
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("GoldenHour")

def evaluate_lighting_quality(
    has_harsh_shadows: bool = False,
    color_temperature: str = "neutral",
    timestamp: Optional[float] = None
) -> Dict[str, Any]:
    """
    Evaluates ambient lighting atmosphere.
    Produces complementary soft tips (e.g. "Golden hour glow detected" or "Harsh midday light — find open shade").
    """
    now_ts = timestamp or time.time()
    dt = datetime.datetime.fromtimestamp(now_ts)
    hour = dt.hour

    is_golden_hour = (hour in (6, 7, 8, 17, 18, 19))
    is_midday = (11 <= hour <= 14)

    tip = None
    quality = "standard"

    if is_golden_hour and color_temperature == "warm":
        quality = "golden_hour"
        tip = "Warm golden hour light detected — ideal for natural portraits."
    elif has_harsh_shadows and is_midday:
        quality = "harsh_direct"
        tip = "Harsh direct sunlight detected — find open shade for softer lighting."
    elif color_temperature == "cool":
        quality = "cool_ambient"
        tip = "Cool ambient light — consider warming up tone or moving closer to warm illumination."

    return {
        "is_golden_hour": is_golden_hour,
        "is_midday_harsh": is_midday and has_harsh_shadows,
        "lighting_quality": quality,
        "aesthetic_tip": tip
    }
