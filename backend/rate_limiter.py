"""
Sliding-Window Token Rate Limiter for Groq API Integration.

Specifically enforces the Output Tokens Per Minute (OTPM) rate limit on Groq
for the qwen/qwen3.6-27b vision model.

Key Features:
1. Precise sliding-window tracking: Sums actual and estimated output tokens within
   a trailing 60-second window (not a naive fixed boundary reset).
2. Pre-flight budget evaluation: Evaluates whether estimated token cost will exceed
   remaining OTPM budget before any external HTTP call is fired.
3. Fallback enabler: Returns immediate refusal when budget is tight, prompting the
   caller to fall back to OpenCV heuristic scoring without latency or errors.
4. Post-response reconciliation: Replaces pre-flight estimates with actual
   completion tokens reported in Groq's usage payload.
5. Graceful 429 handling: Respects Retry-After delays and initiates a local
   cooldown to avoid hammering rate-limited endpoints.
"""

import os
import time
import re
import threading
import logging
from typing import Optional, Tuple, Dict, Any, List

logger = logging.getLogger("RateLimiter")

# Configurable constants
# Groq current tier output token per minute (OTPM) limit for qwen/qwen3.6-27b
MAX_OTPM: int = int(os.environ.get("GROQ_MAX_OTPM", 1000))
WINDOW_SECONDS: float = 60.0
# Estimated output tokens for camera analysis JSON payload (~120-180 tokens)
DEFAULT_ESTIMATED_TOKENS: int = int(os.environ.get("GROQ_ESTIMATED_OUTPUT_TOKENS", 200))
DEFAULT_429_BACKOFF_SECONDS: float = 15.0


class SlidingWindowTokenRateLimiter:
    """
    Thread-safe sliding-window token rate limiter for tracking Groq OTPM.
    """

    def __init__(self, max_otpm: int = MAX_OTPM, window_seconds: float = WINDOW_SECONDS):
        self._max_otpm = max(1, max_otpm)
        self._window_seconds = max(1.0, window_seconds)
        self._lock = threading.Lock()
        self._entries: List[Dict[str, Any]] = []
        self._cooldown_until: float = 0.0
        self._counter: int = 0

    @property
    def max_otpm(self) -> int:
        with self._lock:
            return self._max_otpm

    def set_max_otpm(self, new_limit: int):
        with self._lock:
            self._max_otpm = max(1, new_limit)
            logger.info(f"SlidingWindowTokenRateLimiter: MAX_OTPM set to {self._max_otpm}")

    def get_max_otpm(self) -> int:
        return self.max_otpm

    def _prune_expired(self, now: float):
        """Removes entries older than window_seconds from the sliding window."""
        cutoff = now - self._window_seconds
        self._entries = [e for e in self._entries if e.get("timestamp", 0) > cutoff]

    def get_used_tokens(self, now: Optional[float] = None) -> int:
        """Calculates total output tokens used within the trailing window."""
        current_time = now if now is not None else time.time()
        self._prune_expired(current_time)
        return sum(e.get("tokens", 0) for e in self._entries)

    def can_request(
        self,
        estimated_tokens: int = DEFAULT_ESTIMATED_TOKENS,
        now: Optional[float] = None
    ) -> Tuple[bool, Optional[str], Dict[str, Any]]:
        """
        Pre-flight check: determines if the request fits in the remaining OTPM budget.
        
        If permitted:
          Creates a provisional token reservation with unique reservation_id,
          so concurrent requests do not exceed the limit before completion.
        
        Returns:
          (allowed: bool, reservation_id: Optional[str], stats: Dict[str, Any])
        """
        with self._lock:
            current_time = now if now is not None else time.time()

            # 1. Check if an active 429 backoff cooldown is in effect
            if current_time < self._cooldown_until:
                wait_remaining = round(self._cooldown_until - current_time, 2)
                used = self.get_used_tokens(current_time)
                logger.warning(
                    f"RateLimiter: In 429 cooldown ({wait_remaining}s remaining). "
                    f"Deferring to heuristic fallback."
                )
                return False, None, {
                    "allowed": False,
                    "reason": f"Active 429 cooldown ({wait_remaining}s remaining)",
                    "used_tokens": used,
                    "remaining_tokens": max(0, self._max_otpm - used),
                    "max_otpm": self._max_otpm,
                    "in_cooldown": True,
                    "cooldown_remaining_seconds": wait_remaining
                }

            # 2. Prune old records and compute remaining budget
            used = self.get_used_tokens(current_time)
            remaining = self._max_otpm - used

            # 3. Check if estimated cost exceeds remaining budget
            if estimated_tokens > remaining:
                logger.info(
                    f"RateLimiter: Request would exceed OTPM budget! "
                    f"Estimated={estimated_tokens} > Remaining={remaining} (Used {used}/{self._max_otpm} in last {self._window_seconds}s). "
                    f"Deferring immediately to OpenCV heuristic fallback."
                )
                return False, None, {
                    "allowed": False,
                    "reason": (
                        f"Budget exceeded: est {estimated_tokens} > remaining {remaining} "
                        f"(used {used}/{self._max_otpm})"
                    ),
                    "used_tokens": used,
                    "remaining_tokens": remaining,
                    "max_otpm": self._max_otpm,
                    "in_cooldown": False,
                    "cooldown_remaining_seconds": 0.0
                }

            # 4. Budget available: reserve provisional tokens
            self._counter += 1
            reservation_id = f"res_{int(current_time * 1000)}_{self._counter}"
            self._entries.append({
                "reservation_id": reservation_id,
                "timestamp": current_time,
                "tokens": estimated_tokens,
                "is_estimate": True
            })

            new_used = used + estimated_tokens
            new_remaining = max(0, self._max_otpm - new_used)

            logger.info(
                f"RateLimiter: Pre-flight OK (reserved {estimated_tokens} tokens). "
                f"Window used: {new_used}/{self._max_otpm}, remaining: {new_remaining}."
            )

            return True, reservation_id, {
                "allowed": True,
                "reason": "OK",
                "reservation_id": reservation_id,
                "used_tokens": new_used,
                "remaining_tokens": new_remaining,
                "max_otpm": self._max_otpm,
                "in_cooldown": False,
                "cooldown_remaining_seconds": 0.0
            }

    def record_actual_tokens(
        self,
        reservation_id: str,
        actual_tokens: int,
        now: Optional[float] = None
    ):
        """
        Replaces provisional token estimate with actual completion_tokens from Groq response.
        """
        with self._lock:
            current_time = now if now is not None else time.time()
            self._prune_expired(current_time)

            updated = False
            for entry in self._entries:
                if entry.get("reservation_id") == reservation_id:
                    entry["tokens"] = max(0, int(actual_tokens))
                    entry["is_estimate"] = False
                    entry["timestamp"] = current_time
                    updated = True
                    break

            if not updated:
                self._entries.append({
                    "reservation_id": reservation_id,
                    "timestamp": current_time,
                    "tokens": max(0, int(actual_tokens)),
                    "is_estimate": False
                })

            total_used = sum(e.get("tokens", 0) for e in self._entries)
            logger.info(
                f"RateLimiter: Reconciled reservation {reservation_id} -> {actual_tokens} tokens. "
                f"Total used in window: {total_used}/{self._max_otpm}."
            )

    def cancel_reservation(self, reservation_id: Optional[str]):
        """
        Releases reserved tokens if the API call was aborted or failed prior to consuming output tokens.
        """
        if not reservation_id:
            return
        with self._lock:
            initial_count = len(self._entries)
            self._entries = [e for e in self._entries if e.get("reservation_id") != reservation_id]
            if len(self._entries) < initial_count:
                logger.info(f"RateLimiter: Released reservation {reservation_id} on non-completion.")

    def record_429(
        self,
        retry_after_seconds: Optional[float] = None,
        now: Optional[float] = None
    ):
        """
        Initiates a backoff cooldown when a 429 response is encountered from Groq.
        """
        with self._lock:
            current_time = now if now is not None else time.time()
            delay = (
                max(1.0, float(retry_after_seconds))
                if retry_after_seconds is not None and retry_after_seconds > 0
                else DEFAULT_429_BACKOFF_SECONDS
            )
            self._cooldown_until = max(self._cooldown_until, current_time + delay)
            logger.warning(
                f"RateLimiter: 429 detected! Cooldown established for {delay:.2f}s "
                f"(until {self._cooldown_until:.2f})."
            )

    def get_status(self, now: Optional[float] = None) -> Dict[str, Any]:
        """Diagnostic state of the rate limiter."""
        with self._lock:
            current_time = now if now is not None else time.time()
            used = self.get_used_tokens(current_time)
            cooldown_left = max(0.0, round(self._cooldown_until - current_time, 2))
            return {
                "used_tokens": used,
                "remaining_tokens": max(0, self._max_otpm - used),
                "max_otpm": self._max_otpm,
                "window_seconds": self._window_seconds,
                "in_cooldown": cooldown_left > 0,
                "cooldown_remaining_seconds": cooldown_left,
                "active_entries_count": len(self._entries)
            }

    def reset(self):
        """Clears all tracking history and cooldowns (useful in test suites)."""
        with self._lock:
            self._entries.clear()
            self._cooldown_until = 0.0
            self._counter = 0


# Global singleton instance
global_rate_limiter = SlidingWindowTokenRateLimiter(
    max_otpm=MAX_OTPM,
    window_seconds=WINDOW_SECONDS
)

# Helper functions for clean modular usage
def can_call_groq(
    estimated_tokens: int = DEFAULT_ESTIMATED_TOKENS
) -> Tuple[bool, Optional[str], Dict[str, Any]]:
    return global_rate_limiter.can_request(estimated_tokens=estimated_tokens)

def record_groq_tokens(reservation_id: str, actual_tokens: int):
    global_rate_limiter.record_actual_tokens(reservation_id, actual_tokens)

def release_groq_reservation(reservation_id: Optional[str]):
    global_rate_limiter.cancel_reservation(reservation_id)

def handle_groq_429(retry_after_seconds: Optional[float] = None):
    global_rate_limiter.record_429(retry_after_seconds)

def get_rate_limiter_status() -> Dict[str, Any]:
    return global_rate_limiter.get_status()

def set_max_otpm(new_limit: int):
    global_rate_limiter.set_max_otpm(new_limit)

def get_max_otpm() -> int:
    return global_rate_limiter.get_max_otpm()
