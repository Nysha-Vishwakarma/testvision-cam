"""
capture_rate_limiter.py - Prevents auto-capture spamming.

Features:
- Enforces multi-frame confirmation (requires ready_to_capture for N consecutive cycles).
- Imposes an explicit cooldown period (e.g. 4.0 seconds) after auto-capture fires.
- Prevents rapid burst captures when a user holds steady in an optimal pose.
"""

import time
import logging
from typing import Dict, Any

logger = logging.getLogger("CaptureRateLimiter")

CAPTURE_COOLDOWN_SECONDS = 4.0
REQUIRED_STEADY_CYCLES = 2

def evaluate_capture_permission(
    tentative_ready: bool,
    session_capture_state: Dict[str, Any],
    now: float = 0.0
) -> Dict[str, Any]:
    """
    Evaluates whether an auto-capture shutter trigger is permitted or on cooldown.
    """
    curr_time = now or time.time()
    last_capture = session_capture_state.get("last_capture_time", 0.0)
    time_since_capture = curr_time - last_capture
    is_on_cooldown = time_since_capture < CAPTURE_COOLDOWN_SECONDS

    consecutive_count = session_capture_state.get("consecutive_ready_count", 0)

    if tentative_ready and not is_on_cooldown:
        consecutive_count += 1
    elif not tentative_ready:
        consecutive_count = 0

    session_capture_state["consecutive_ready_count"] = consecutive_count

    # Fire capture only when consecutive threshold is satisfied and not on cooldown
    trigger_capture = (consecutive_count >= REQUIRED_STEADY_CYCLES) and not is_on_cooldown

    if trigger_capture:
        # Mark capture timestamp and reset ready counter to enter cooldown
        session_capture_state["last_capture_time"] = curr_time
        session_capture_state["consecutive_ready_count"] = 0
        session_capture_state["post_capture_active"] = True

    return {
        "trigger_capture": bool(trigger_capture),
        "is_on_cooldown": bool(is_on_cooldown),
        "cooldown_remaining_sec": max(0.0, round(CAPTURE_COOLDOWN_SECONDS - time_since_capture, 1)) if is_on_cooldown else 0.0,
        "consecutive_ready_count": consecutive_count
    }
