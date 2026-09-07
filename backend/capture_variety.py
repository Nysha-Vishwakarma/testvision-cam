"""
capture_variety.py - Suggests creative alternate framings after auto-capture.

Features:
- Fires distinct follow-up suggestions strictly AFTER a successful capture.
- Cycles through creative mini-session prompts:
  1. "Great shot! Now try a vertical orientation."
  2. "Get closer for a detail shot."
  3. "Try a lower angle for a dramatic perspective."
  4. "Try stepping to the side for a 3/4 profile."
- Ensures clear separation between pre-capture guidance and post-capture variety suggestions.
"""

from typing import Dict, Any, Optional

VARIETY_PROMPTS = [
    "Great shot! Now try a vertical full-length shot.",
    "Nice capture! Step closer for an intimate detail shot.",
    "Try a lower camera angle for an editorial look.",
    "Turn 45 degrees for a stylish three-quarter profile."
]

def get_post_capture_variety(
    captured_count: int,
    current_scene_type: str = "portrait"
) -> Dict[str, Any]:
    """
    Returns creative follow-up suggestion after a photo has been taken.
    """
    idx = captured_count % len(VARIETY_PROMPTS)
    suggestion = VARIETY_PROMPTS[idx]

    if current_scene_type == "object":
        suggestion = "Captured! Try a top-down flat lay or 45-degree angle next."
    elif current_scene_type == "landscape":
        suggestion = "Captured! Try including a foreground element for added depth."

    return {
        "variety_available": True,
        "captured_count": captured_count,
        "next_framing_suggestion": suggestion
    }
