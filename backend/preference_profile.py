"""
preference_profile.py - Learning from user corrections and biasing future suggestions.

Features:
- Logs every time the user overrides auto-capture or reverses/ignores suggested directions.
- Aggregates running metrics per session or persistent user identity.
- Adapts default framing bias (e.g. padding margin, preferred zoom offset).
- Persists telemetry to SQLite database.
"""

import sqlite3
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("PreferenceProfile")
DB_PATH = "pose_library.db"

def init_preference_table(db_path: str = DB_PATH):
    """Initializes user preference table in database."""
    try:
        conn = sqlite3.connect(db_path)
        c = conn.cursor()
        c.execute("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                session_id TEXT PRIMARY KEY,
                manual_captures INTEGER DEFAULT 0,
                auto_captures INTEGER DEFAULT 0,
                direction_reversals INTEGER DEFAULT 0,
                avg_zoom_bias REAL DEFAULT 0.0,
                preferred_style TEXT DEFAULT 'balanced',
                last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()
        conn.close()
    except Exception as e:
        logger.debug(f"Preference table init error: {e}")

# Call init on module import
init_preference_table()

def record_user_action(
    session_id: str,
    action_type: str,
    zoom_delta: float = 0.0,
    reversed_instruction: bool = False,
    db_path: str = DB_PATH
) -> Dict[str, Any]:
    """
    Records an action: 'manual_capture', 'auto_capture', or 'directional_adjustment'.
    Updates running averages.
    """
    profile = get_user_profile(session_id, db_path)

    manual_c = profile.get("manual_captures", 0) + (1 if action_type == "manual_capture" else 0)
    auto_c = profile.get("auto_captures", 0) + (1 if action_type == "auto_capture" else 0)
    reversals = profile.get("direction_reversals", 0) + (1 if reversed_instruction else 0)

    # Running average for zoom bias
    curr_zoom_bias = profile.get("avg_zoom_bias", 0.0)
    new_zoom_bias = round(0.8 * curr_zoom_bias + 0.2 * zoom_delta, 3)

    try:
        conn = sqlite3.connect(db_path)
        c = conn.cursor()
        c.execute("""
            INSERT INTO user_preferences (session_id, manual_captures, auto_captures, direction_reversals, avg_zoom_bias, last_updated)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(session_id) DO UPDATE SET
                manual_captures = excluded.manual_captures,
                auto_captures = excluded.auto_captures,
                direction_reversals = excluded.direction_reversals,
                avg_zoom_bias = excluded.avg_zoom_bias,
                last_updated = CURRENT_TIMESTAMP
        """, (session_id, manual_c, auto_c, reversals, new_zoom_bias))
        conn.commit()
        conn.close()
    except Exception as e:
        logger.debug(f"Database error updating user preference: {e}")

    return {
        "manual_captures": manual_c,
        "auto_captures": auto_c,
        "direction_reversals": reversals,
        "avg_zoom_bias": new_zoom_bias
    }

def get_user_profile(session_id: str, db_path: str = DB_PATH) -> Dict[str, Any]:
    """Retrieves user preference profile for session."""
    default_profile = {
        "session_id": session_id,
        "manual_captures": 0,
        "auto_captures": 0,
        "direction_reversals": 0,
        "avg_zoom_bias": 0.0,
        "preferred_style": "balanced"
    }
    try:
        conn = sqlite3.connect(db_path)
        c = conn.cursor()
        c.execute("SELECT manual_captures, auto_captures, direction_reversals, avg_zoom_bias, preferred_style FROM user_preferences WHERE session_id = ?", (session_id,))
        row = c.fetchone()
        conn.close()
        if row:
            return {
                "session_id": session_id,
                "manual_captures": row[0],
                "auto_captures": row[1],
                "direction_reversals": row[2],
                "avg_zoom_bias": float(row[3]),
                "preferred_style": str(row[4])
            }
    except Exception as e:
        logger.debug(f"Error fetching profile: {e}")
    return default_profile

def apply_preference_bias(suggested_zoom: float, profile: Dict[str, Any]) -> float:
    """Adjusts suggested zoom based on learned user bias."""
    bias = profile.get("avg_zoom_bias", 0.0)
    adjusted = suggested_zoom + (0.5 * bias)
    return round(max(-0.5, min(0.5, adjusted)), 2)
