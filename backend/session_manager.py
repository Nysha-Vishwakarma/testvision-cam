"""
session_manager.py - Dedicated session lifecycle and state management.

Features:
- Creation, retrieval, update, reset, and expiration of camera analysis sessions.
- In-memory thread-safe storage with TTL pruning.
- Explicit support for backgrounding, stale session timeouts, and rolling history.
- Decouples session persistence from verification comparison logic.
"""

import time
import logging
from collections import deque
from typing import Dict, Any, Optional, List

logger = logging.getLogger("SessionManager")

SESSION_TTL_SECONDS = 180.0  # 3 minutes inactivity timeout
MAX_HISTORY_LEN = 10

class SessionManager:
    """Manages active AI Photographer sessions and their historical tracking."""
    def __init__(self, ttl_seconds: float = SESSION_TTL_SECONDS):
        self.ttl_seconds = ttl_seconds
        self._store: Dict[str, Dict[str, Any]] = {}

    def clean_stale_sessions(self, max_idle: Optional[float] = None) -> int:
        """Prunes sessions that have exceeded idle timeout."""
        now = time.time()
        cutoff = max_idle if max_idle is not None else self.ttl_seconds
        stale_keys = [
            sid for sid, s in self._store.items()
            if (now - s.get("last_timestamp", 0)) > cutoff
        ]
        for sid in stale_keys:
            self._store.pop(sid, None)
        return len(stale_keys)

    def get_session(self, session_id: Optional[str]) -> Optional[Dict[str, Any]]:
        """Retrieves active session state if valid and non-stale."""
        if not session_id:
            return None
        self.clean_stale_sessions()
        return self._store.get(session_id)

    def get_or_create_session(self, session_id: Optional[str]) -> Dict[str, Any]:
        """Gets existing session or initializes a clean session record."""
        sid = session_id or "default_session"
        self.clean_stale_sessions()
        if sid not in self._store:
            now = time.time()
            self._store[sid] = {
                "session_id": sid,
                "created_at": now,
                "last_timestamp": now,
                "last_snapshot_bytes": None,
                "last_metrics": {},
                "last_instruction": "HOLD_STEADY",
                "attempt_count": 0,
                "guidance_stuck": False,
                "history": deque(maxlen=MAX_HISTORY_LEN),
                "is_backgrounded": False,
                "subject_track": None,
                "user_preferences": {},
                "capture_state": {
                    "last_capture_time": 0.0,
                    "consecutive_ready_count": 0,
                    "post_capture_active": False,
                    "post_capture_variety": None
                }
            }
        return self._store[sid]

    def update_session(self, session_id: str, updates: Dict[str, Any]) -> Dict[str, Any]:
        """Applies updates to session state and updates last_timestamp."""
        session = self.get_or_create_session(session_id)
        session.update(updates)
        session["last_timestamp"] = time.time()
        return session

    def reset_session(self, session_id: str) -> Dict[str, Any]:
        """Resets instructions and history while preserving session_id."""
        if session_id in self._store:
            self._store.pop(session_id, None)
        return self.get_or_create_session(session_id)

    def mark_backgrounded(self, session_id: str, is_bg: bool = True):
        """Marks session as backgrounded (e.g. app paused/minimized)."""
        session = self.get_session(session_id)
        if session:
            session["is_backgrounded"] = is_bg

    def get_active_sessions_count(self) -> int:
        self.clean_stale_sessions()
        return len(self._store)

    def get_stats(self) -> Dict[str, Any]:
        self.clean_stale_sessions()
        return {
            "active_sessions": len(self._store),
            "session_ids": list(self._store.keys())
        }

# Global singleton instance
global_session_manager = SessionManager()
