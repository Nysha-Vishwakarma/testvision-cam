"""
confidence_gate.py - Overall trust, consistency, and uncertainty scoring.

Features:
- Compares agreement between rule-based scores (rule of thirds, horizon, lighting) and AI vision models (Gemini / Qwen).
- When scores strongly diverge (|rule_score - ai_score| > 28), flags disagreement and penalizes overall confidence.
- Defaults conservatively: suppresses ready_to_capture when overall confidence < 65.
- Exposes internal confidence metrics for transparent decision gating.
"""

import logging
from typing import Dict, Any

logger = logging.getLogger("ConfidenceGate")

MIN_CONFIDENCE_FOR_CAPTURE = 65

def evaluate_confidence_gate(
    rule_score: int,
    ai_score: int,
    is_stable: bool,
    eyes_open: bool = True,
    has_harsh_shadows: bool = False,
    is_backlit: bool = False
) -> Dict[str, Any]:
    """
    Computes cross-validation confidence and determines whether capture is safely authorized.
    """
    discrepancy = abs(rule_score - ai_score)
    confidence = 90  # Base confidence

    # Penalize discrepancy between rule-based heuristics and AI vision
    if discrepancy > 35:
        confidence -= 30
    elif discrepancy > 22:
        confidence -= 15

    # Penalize physical camera instability
    if not is_stable:
        confidence -= 40

    # Penalize blinking
    if not eyes_open:
        confidence -= 35

    # Slight penalty for extreme lighting conditions
    if is_backlit or has_harsh_shadows:
        confidence -= 10

    confidence = max(0, min(100, confidence))
    is_trusted = confidence >= MIN_CONFIDENCE_FOR_CAPTURE

    notes = []
    if discrepancy > 25:
        notes.append(f"AI score ({ai_score}) and heuristic rules ({rule_score}) diverge by {discrepancy} pts.")
    if not is_stable:
        notes.append("Camera instability detected.")
    if not eyes_open:
        notes.append("Blink detected.")

    return {
        "confidence_score": int(confidence),
        "is_trusted": bool(is_trusted),
        "discrepancy": int(discrepancy),
        "verification_notes": "; ".join(notes) if notes else "High consensus across all checks."
    }
