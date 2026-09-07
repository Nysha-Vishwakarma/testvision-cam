"""
Closed-Loop Movement Verification & Groq Vision AI Photographer Logic Module.

Architecture & Responsibilities:
1. Groq Vision Model Integration:
   - Uses Groq's vision model (e.g. llama-3.2-11b-vision-preview) to analyze camera snapshots
     of general objects, products, food, pets, and people.
   - Enforces strict JSON output via schema prompt and json_object response format.
   - Robust retry and fallback to OpenCV heuristic scoring if GROQ_API_KEY is not set or network fails.
2. Fast Movement & Positional Diff:
   - Rapid OpenCV frame-to-frame diff / contour displacement / phase correlation as the fast check.
   - Compares current subject position (x, y, scale) against previous snapshot's position.
3. Closed-Loop Verification:
   - Verifies whether user actually followed the previous movement instruction (e.g. ORBIT_LEFT, MOVE_UP, STEP_BACK).
   - If user didn't move, moved the wrong way, or overcorrected:
     sets previous_instruction_followed=False and provides explicit correction_note.
   - Tracks consecutive attempts per instruction (attempt_count).
   - After ~3-4 failed attempts, flags guidance_stuck=True so the client can show manual override.
4. Server-Side In-Memory Session Store:
   - Keyed by client session_id. Maintains rolling metrics, last instruction, timestamp, attempt count.
   - Clean native Python types for 100% JSON serializability.
"""

import os
import time
import json
import base64
import re
import urllib.request
import urllib.error
import logging
from collections import deque
from typing import Optional, Dict, Any, Tuple, List

from rate_limiter import (
    can_call_groq,
    record_groq_tokens,
    release_groq_reservation,
    handle_groq_429,
    get_rate_limiter_status,
    set_max_otpm,
    get_max_otpm,
    MAX_OTPM,
    DEFAULT_ESTIMATED_TOKENS
)
import stability
import lighting_advanced
import subject_tracker
import capture_timing
import preference_profile
import scene_classifier
import composition_rules
import distance_estimator
import multi_subject
import background_check
import golden_hour
import capture_variety
import confidence_gate
import capture_rate_limiter

logger = logging.getLogger("VerificationEngine")

TARGET_GROQ_MODEL = "qwen/qwen3.6-27b"

def _load_env_file():
    """Auto-loads key-value pairs from .env files into os.environ if not already defined."""
    possible_paths = [
        os.path.join(os.path.dirname(__file__), ".env"),
        os.path.join(os.path.dirname(os.path.dirname(__file__)), ".env"),
        ".env"
    ]
    for env_path in possible_paths:
        if os.path.isfile(env_path):
            try:
                with open(env_path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            k, v = line.split("=", 1)
                            k = k.strip()
                            v = v.strip().strip("'").strip('"')
                            if k and not os.environ.get(k):
                                os.environ[k] = v
            except Exception as e:
                logger.debug(f"Error reading .env at {env_path}: {e}")

_load_env_file()

def _normalize_groq_model(model_raw: Optional[str] = None) -> str:
    """
    Strictly normalizes and enforces the single valid Groq model: qwen/qwen3.6-27b.
    Prevents deprecated models (e.g. llama-3.2-11b-vision-preview) and un-prefixed aliases.
    """
    return TARGET_GROQ_MODEL

# Runtime configuration store
_RUNTIME_CONFIG: Dict[str, str] = {
    "GROQ_VISION_MODEL": TARGET_GROQ_MODEL,
    "GROQ_API_KEY": os.environ.get("GROQ_API_KEY", "").strip()
}

def set_runtime_groq_config(api_key: Optional[str] = None, model: Optional[str] = None):
    """Dynamically updates API key and ensures vision model is strictly qwen/qwen3.6-27b."""
    if api_key is not None:
        cleaned_key = api_key.strip()
        _RUNTIME_CONFIG["GROQ_API_KEY"] = cleaned_key
        os.environ["GROQ_API_KEY"] = cleaned_key
    _RUNTIME_CONFIG["GROQ_VISION_MODEL"] = TARGET_GROQ_MODEL
    os.environ["GROQ_VISION_MODEL"] = TARGET_GROQ_MODEL

def get_runtime_groq_config() -> Dict[str, Any]:
    """Returns status of Groq configuration and rate limiter metrics."""
    key = _RUNTIME_CONFIG.get("GROQ_API_KEY") or os.environ.get("GROQ_API_KEY", "").strip()
    rate_stats = get_rate_limiter_status()
    return {
        "groq_api_key_configured": bool(key),
        "groq_api_key_preview": (key[:7] + "..." + key[-4:]) if len(key) >= 12 else ("configured" if key else "not_configured"),
        "model": TARGET_GROQ_MODEL,
        "rate_limiter": rate_stats
    }

from session_manager import global_session_manager

# Backward-compatible alias for existing tests and consumers
_SESSION_STORE = global_session_manager._store

def _clean_stale_sessions():
    """Prunes inactive sessions to keep memory footprint minimal."""
    return global_session_manager.clean_stale_sessions()

def get_session_store_stats() -> Dict[str, Any]:
    """Returns diagnostic statistics for active sessions."""
    return global_session_manager.get_stats()

def _calculate_opencv_motion_diff(
    prev_bytes: Optional[bytes],
    curr_bytes: bytes
) -> Tuple[float, float, float]:
    """
    Fast positional difference between two successive frames using OpenCV.
    Returns: (dx, dy, d_scale)
      - dx: horizontal shift of subject (+: shifted right in frame, -: shifted left)
      - dy: vertical shift of subject (+: shifted down in frame, -: shifted up)
      - d_scale: scale change (+: subject got bigger, -: subject got smaller)
    """
    if not prev_bytes:
        return (0.0, 0.0, 0.0)

    try:
        import numpy as np
        import cv2

        arr_prev = np.frombuffer(prev_bytes, np.uint8)
        arr_curr = np.frombuffer(curr_bytes, np.uint8)
        img_prev = cv2.imdecode(arr_prev, cv2.IMREAD_COLOR)
        img_curr = cv2.imdecode(arr_curr, cv2.IMREAD_COLOR)

        if img_prev is not None and img_curr is not None:
            h, w = img_curr.shape[:2]
            gray_prev = cv2.cvtColor(img_prev, cv2.COLOR_BGR2GRAY)
            gray_curr = cv2.cvtColor(img_curr, cv2.COLOR_BGR2GRAY)

            # 1. Subject centroid detection via thresholding & largest contour
            def get_subject_box(gray_img):
                blurred = cv2.GaussianBlur(gray_img, (15, 15), 0)
                thresh = cv2.adaptiveThreshold(
                    blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                    cv2.THRESH_BINARY_INV, 11, 2
                )
                contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
                if contours:
                    c = max(contours, key=cv2.contourArea)
                    if cv2.contourArea(c) > (w * h * 0.02):
                        x, y, bw, bh = cv2.boundingRect(c)
                        cx = (x + bw / 2.0) / float(w)
                        cy = (y + bh / 2.0) / float(h)
                        scale = (bw * bh) / float(w * h)
                        return (cx, cy, scale)
                # Fallback to center of mass
                m = cv2.moments(thresh)
                if m["m00"] > 0:
                    cx = (m["m10"] / m["m00"]) / float(w)
                    cy = (m["m01"] / m["m00"]) / float(h)
                    return (cx, cy, 0.35)
                return (0.5, 0.5, 0.35)

            prev_box = get_subject_box(gray_prev)
            curr_box = get_subject_box(gray_curr)

            dx = float(curr_box[0] - prev_box[0])
            dy = float(curr_box[1] - prev_box[1])
            d_scale = float(curr_box[2] - prev_box[2])
            return (dx, dy, d_scale)
    except Exception as e:
        logger.debug(f"OpenCV motion diff fallback: {e}")

    # Pure Python byte difference fallback
    if len(prev_bytes) > 200 and len(curr_bytes) > 200:
        sample_prev = prev_bytes[100:min(len(prev_bytes), 1500)]
        sample_curr = curr_bytes[100:min(len(curr_bytes), 1500)]
        avg_prev = sum(sample_prev) / len(sample_prev)
        avg_curr = sum(sample_curr) / len(sample_curr)
        # Small proxy delta
        diff = (avg_curr - avg_prev) / 255.0
        return (0.0, diff * 0.05, 0.0)

    return (0.0, 0.0, 0.0)

def _clean_and_parse_json(content_str: str) -> Optional[Dict[str, Any]]:
    """Cleans markdown backticks or formatting artifacts and parses JSON."""
    if not content_str:
        return None
    s = content_str.strip()
    if s.startswith("```"):
        first_newline = s.find("\n")
        if first_newline != -1:
            s = s[first_newline + 1:]
        else:
            s = s.lstrip("`")
            if s.lower().startswith("json"):
                s = s[4:].strip()
        if s.endswith("```"):
            s = s[:-3].rstrip()

    start_brace = s.find("{")
    end_brace = s.rfind("}")
    if start_brace != -1 and end_brace != -1 and end_brace > start_brace:
        s = s[start_brace:end_brace + 1]

    try:
        data = json.loads(s)
        if isinstance(data, dict):
            return data
    except Exception as e:
        logger.debug(f"JSON parsing failed: {e}")
    return None

def _validate_photographer_schema(data: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """
    Validates that the parsed dict strictly adheres to the expected schema:
    subject_detected, subject_position, framing_quality, suggested_direction,
    lighting_assessment, ready_to_capture.
    """
    if not isinstance(data, dict):
        return None

    required_keys = [
        "subject_detected",
        "subject_position",
        "framing_quality",
        "suggested_direction",
        "lighting_assessment",
        "ready_to_capture"
    ]
    for k in required_keys:
        if k not in data:
            logger.warning(f"JSON schema validation failed: missing key '{k}' in response")
            return None

    pos = data.get("subject_position")
    if not isinstance(pos, dict) or "x" not in pos or "y" not in pos:
        logger.warning(f"JSON schema validation failed: invalid subject_position '{pos}'")
        return None

    valid_directions = {
        "HOLD_STEADY", "MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT",
        "ORBIT_LEFT", "ORBIT_RIGHT", "STEP_BACK", "STEP_CLOSER"
    }
    raw_dir = str(data.get("suggested_direction", "")).strip().upper()
    direction = raw_dir if raw_dir in valid_directions else "HOLD_STEADY"

    try:
        cx = float(pos.get("x", 0.5))
        cy = float(pos.get("y", 0.5))
        scale = float(pos.get("scale", 0.35))
    except (ValueError, TypeError):
        cx, cy, scale = 0.5, 0.5, 0.35

    try:
        quality = int(data.get("framing_quality", 75))
    except (ValueError, TypeError):
        quality = 75

    ready = bool(data.get("ready_to_capture", False))
    if ready and (quality < 75 or direction != "HOLD_STEADY"):
        ready = False

    return {
        "subject_detected": bool(data.get("subject_detected", True)),
        "subject_type": str(data.get("subject_type", "general")),
        "subject_position": {
            "x": round(cx, 3),
            "y": round(cy, 3),
            "scale": round(scale, 3)
        },
        "framing_quality": max(0, min(100, quality)),
        "suggested_direction": direction,
        "framing_feedback": str(data.get("framing_feedback") or "Framing analyzed."),
        "lighting_assessment": str(data.get("lighting_assessment") or "Lighting assessed."),
        "suggested_zoom_delta": round(float(data.get("suggested_zoom_delta", 0.0)), 2),
        "suggested_exposure_delta": int(data.get("suggested_exposure_delta", 0)),
        "ready_to_capture": ready
    }

def _call_groq_vision_api(
    image_bytes: bytes,
    api_key: str,
    model_name: Optional[str] = None
) -> Tuple[Optional[Dict[str, Any]], str]:
    """
    Calls Groq's multimodal vision endpoint strictly using qwen/qwen3.6-27b
    with client-side sliding-window rate limiting, token reconciliation, and robust JSON validation.

    Guarantees:
    - Never calls llama-3.2-11b-vision-preview or unprefixed qwen3.6-27b.
    - Never exceeds the local OTPM token budget.
    - If budget is exceeded or cooldown is active, returns (None, "") immediately
      so the caller can fall back to OpenCV heuristic scoring.
    - If the first response fails JSON validation, performs a single server-side
      retry with a stricter reminder prompt before falling back.
    - On 429, parses retry delay, activates cooldown in rate limiter, and returns (None, "").
    """
    # 1. Check rate limiter budget before network call
    allowed, res_id, stats = can_call_groq(estimated_tokens=DEFAULT_ESTIMATED_TOKENS)
    if not allowed:
        logger.info(
            f"Rate limiter deferred Groq call: {stats.get('reason')}. "
            f"Flipping immediately to OpenCV heuristic fallback."
        )
        return None, ""

    b64_image = base64.b64encode(image_bytes).decode("utf-8")
    data_uri = f"data:image/jpeg;base64,{b64_image}"
    model = TARGET_GROQ_MODEL

    system_prompt = (
        "You are an expert AI Photographer assistant powered by Qwen 3.6-27B multimodal vision, "
        "analyzing camera viewfinder snapshots.\n"
        "Your task is to analyze general subjects (people, products, pets, food, objects, or scenery) "
        "and provide precise compositional and framing directions to capture a stunning shot.\n\n"
        "CRITICAL REQUIREMENT: You MUST respond ONLY with a single valid raw JSON object. "
        "Do NOT include markdown formatting, do NOT wrap in ```json ``` code blocks, "
        "and do NOT include any introductory or concluding text.\n\n"
        "Your response MUST strictly adhere to this exact schema with ALL fields present:\n"
        "{\n"
        '  "subject_detected": true,\n'
        '  "subject_type": "person",\n'
        '  "subject_position": {"x": 0.50, "y": 0.45, "scale": 0.35},\n'
        '  "framing_quality": 85,\n'
        '  "suggested_direction": "HOLD_STEADY",\n'
        '  "framing_feedback": "Subject is well positioned. Hold steady.",\n'
        '  "lighting_assessment": "Soft balanced natural light.",\n'
        '  "suggested_zoom_delta": 0.0,\n'
        '  "suggested_exposure_delta": 0,\n'
        '  "ready_to_capture": false\n'
        "}\n\n"
        "Rules:\n"
        "- x: 0.0 (left) to 1.0 (right). 0.5 is center.\n"
        "- y: 0.0 (top) to 1.0 (bottom). 0.5 is center.\n"
        "- scale: fraction of frame area occupied by subject (0.05 to 0.90).\n"
        "- Breathing Room: Always preserve 15-20% margin around the subject. Never crop tightly.\n"
        "- If subject occupies >0.42 frame area, suggest STEP_BACK with zoom_delta -0.10.\n"
        "- If subject is too far (<0.10 frame area), suggest STEP_CLOSER with zoom_delta +0.08.\n"
        "- When subject occupies 0.15 to 0.42 frame area with comfortable margin, zoom_delta MUST be 0.0.\n"
        "- ready_to_capture must be true ONLY if framing_quality >= 80 and suggested_direction is HOLD_STEADY.\n"
        "- OUTPUT RAW JSON ONLY."
    )

    messages = [
        {"role": "system", "content": system_prompt},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": "Analyze this viewfinder snapshot for composition and framing guidance. Return raw JSON only."},
                {"type": "image_url", "image_url": {"url": data_uri}}
            ]
        }
    ]

    def _execute_groq_request(
        msg_list: List[Dict[str, Any]],
        max_tokens: int = 300
    ) -> Tuple[Optional[str], Optional[int], Optional[int], Optional[float]]:
        payload = {
            "model": model,
            "messages": msg_list,
            "temperature": 0.1,
            "max_tokens": max_tokens,
            "response_format": {"type": "json_object"}
        }
        req_data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            url="https://api.groq.com/openai/v1/chat/completions",
            data=req_data,
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "User-Agent": f"VisionCam-Photographer/{model}"
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(req, timeout=7.0) as resp:
                if resp.status == 200:
                    body = resp.read().decode("utf-8")
                    data = json.loads(body)
                    choices = data.get("choices", [])
                    raw_content = choices[0].get("message", {}).get("content", "") if choices else ""
                    usage = data.get("usage", {})
                    out_tokens = usage.get("completion_tokens", DEFAULT_ESTIMATED_TOKENS)
                    return raw_content, out_tokens, 200, None
        except urllib.error.HTTPError as e:
            err_body = ""
            try:
                err_body = e.read().decode("utf-8")
            except Exception:
                pass
            logger.warning(f"Groq API HTTP Error {e.code}: {e.reason} - {err_body}")

            retry_delay = None
            if e.code == 429:
                if e.headers:
                    val = e.headers.get("Retry-After")
                    if val:
                        try:
                            retry_delay = float(val)
                        except (ValueError, TypeError):
                            pass
                if retry_delay is None and err_body:
                    match = re.search(r"try again in ([\d\.]+)s", err_body, re.IGNORECASE)
                    if match:
                        try:
                            retry_delay = float(match.group(1))
                        except ValueError:
                            pass
            return None, None, e.code, retry_delay
        except Exception as e:
            logger.warning(f"Groq API request error: {e}")
            return None, None, None, None

        return None, None, None, None

    # First attempt
    content_str, actual_tokens, status_code, retry_delay = _execute_groq_request(messages, max_tokens=300)

    if status_code == 429:
        handle_groq_429(retry_delay)
        release_groq_reservation(res_id)
        return None, ""

    if status_code != 200 or not content_str:
        release_groq_reservation(res_id)
        return None, ""

    # Deduct actual tokens used
    if res_id and actual_tokens is not None:
        record_groq_tokens(res_id, actual_tokens)

    # Validate schema
    parsed = _clean_and_parse_json(content_str)
    validated = _validate_photographer_schema(parsed)
    if validated is not None:
        logger.info(f"Successfully analyzed frame via Groq vision ({model})")
        return validated, model

    # JSON validation failed: attempt a single server-side retry with stricter reminder prompt
    logger.warning(
        f"Groq response JSON validation failed (sample: {content_str[:120] if content_str else 'empty'}). "
        f"Attempting single server-side retry with stricter reminder prompt."
    )

    retry_allowed, retry_res_id, retry_stats = can_call_groq(estimated_tokens=DEFAULT_ESTIMATED_TOKENS)
    if not retry_allowed:
        logger.info(
            f"Rate limiter deferred retry: {retry_stats.get('reason')}. "
            f"Falling back immediately to OpenCV heuristics."
        )
        return None, ""

    retry_messages = list(messages)
    retry_messages.append({"role": "assistant", "content": content_str})
    retry_messages.append({
        "role": "user",
        "content": (
            "STRICT JSON VALIDATION ERROR: Your previous response failed schema validation. "
            "You MUST output ONLY a valid raw JSON object matching the exact schema with all fields: "
            "subject_detected, subject_type, subject_position, framing_quality, suggested_direction, "
            "framing_feedback, lighting_assessment, suggested_zoom_delta, suggested_exposure_delta, "
            "ready_to_capture. NO MARKDOWN, NO BACKTICKS, NO SURROUNDING TEXT."
        )
    })

    retry_content, retry_tokens, retry_status, retry_429 = _execute_groq_request(retry_messages, max_tokens=300)

    if retry_status == 429:
        handle_groq_429(retry_429)
        release_groq_reservation(retry_res_id)
        return None, ""

    if retry_status != 200 or not retry_content:
        release_groq_reservation(retry_res_id)
        return None, ""

    if retry_res_id and retry_tokens is not None:
        record_groq_tokens(retry_res_id, retry_tokens)

    retry_parsed = _clean_and_parse_json(retry_content)
    retry_validated = _validate_photographer_schema(retry_parsed)
    if retry_validated is not None:
        logger.info(f"Successfully analyzed frame on retry via Groq vision ({model})")
        return retry_validated, model

    logger.warning("Groq vision retry also failed JSON validation. Falling back to OpenCV heuristics.")
    return None, ""

def _analyze_heuristics_general_subject(
    image_bytes: bytes
) -> Dict[str, Any]:
    """
    OpenCV-based fallback analysis capable of general subject detection
    (products, food, pets, general objects, landscapes, and people).
    """
    from composition_scorer import _analyze_lighting_heuristics

    # Default metrics
    subject_box = (0.50, 0.45, 0.38) # (cx, cy, scale)
    subject_type = "general"

    # 1. Lighting assessment
    lighting_subscore, lighting_feedback, exp_adj = _analyze_lighting_heuristics(image_bytes)

    # 2. General subject detection via OpenCV contours / saliency
    try:
        import numpy as np
        import cv2

        arr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if img is not None:
            h, w = img.shape[:2]
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

            # Try face first if human present
            face_detected = False
            try:
                import mediapipe as mp
                mp_face = mp.solutions.face_detection
                with mp_face.FaceDetection(min_detection_confidence=0.5) as face_det:
                    results = face_det.process(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
                    if results and results.detections:
                        bb = results.detections[0].location_data.relative_bounding_box
                        subject_box = (
                            float(bb.xmin + bb.width / 2.0),
                            float(bb.ymin + bb.height / 2.0),
                            float(bb.width * bb.height)
                        )
                        subject_type = "person"
                        face_detected = True
            except Exception:
                pass

            # General object / product contour analysis if no face
            if not face_detected:
                blurred = cv2.GaussianBlur(gray, (17, 17), 0)
                thresh = cv2.adaptiveThreshold(
                    blurred, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                    cv2.THRESH_BINARY_INV, 13, 2
                )
                contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
                if contours:
                    c = max(contours, key=cv2.contourArea)
                    x, y, bw, bh = cv2.boundingRect(c)
                    area_ratio = (bw * bh) / float(w * h)
                    if area_ratio > 0.03:
                        subject_box = (
                            float((x + bw / 2.0) / w),
                            float((y + bh / 2.0) / h),
                            float(area_ratio)
                        )
                        subject_type = "object"
    except Exception as e:
        logger.debug(f"OpenCV general subject heuristic fallback: {e}")

    cx, cy, scale = subject_box

    # 3. Framing logic & direction recommendation with 15-20% breathing room padding
    BREATHING_PADDING_RATIO = 0.18
    padded_scale = min(1.0, scale * (1.0 + 2.0 * BREATHING_PADDING_RATIO))

    framing_quality = 55
    suggested_direction = "HOLD_STEADY"
    framing_feedback = "Framing is balanced."
    zoom_delta = 0.0

    if padded_scale > 0.58 or scale > 0.42:
        suggested_direction = "STEP_BACK"
        framing_feedback = "Subject lacks breathing room — step back or zoom out for a natural frame."
        zoom_delta = -0.10
        framing_quality = 48
    elif padded_scale < 0.14 or scale < 0.10:
        suggested_direction = "STEP_CLOSER"
        framing_feedback = "Subject is too far — step closer or zoom in slightly."
        zoom_delta = +0.08
        framing_quality = 50
    elif cx < 0.30:
        suggested_direction = "ORBIT_LEFT"
        framing_feedback = "Shift or orbit left to center the subject."
        framing_quality = 52
    elif cx > 0.70:
        suggested_direction = "ORBIT_RIGHT"
        framing_feedback = "Shift or orbit right to center the subject."
        framing_quality = 52
    elif cy < 0.25:
        suggested_direction = "MOVE_DOWN"
        framing_feedback = "Tilt camera down slightly to balance headroom."
        framing_quality = 58
    elif cy > 0.65:
        suggested_direction = "MOVE_UP"
        framing_feedback = "Tilt camera up slightly."
        framing_quality = 58
    else:
        # Balanced rule-of-thirds with generous breathing room
        framing_quality = 85
        suggested_direction = "HOLD_STEADY"
        framing_feedback = "Subject is well positioned with comfortable breathing space. Hold steady."

    total_score = max(0, min(100, int((lighting_subscore / 40.0 * 35.0) + (framing_quality * 0.65))))
    ready_to_capture = (total_score >= 75 and suggested_direction == "HOLD_STEADY")

    return {
        "subject_detected": True,
        "subject_type": str(subject_type),
        "subject_position": {
            "x": round(float(cx), 3),
            "y": round(float(cy), 3),
            "scale": round(float(scale), 3)
        },
        "framing_quality": int(framing_quality),
        "total_score": int(total_score),
        "suggested_direction": str(suggested_direction),
        "framing_feedback": str(framing_feedback),
        "lighting_assessment": str(lighting_feedback),
        "suggested_zoom_delta": round(float(zoom_delta), 2),
        "suggested_exposure_delta": int(exp_adj),
        "ready_to_capture": bool(ready_to_capture)
    }

def verify_and_analyze_frame(
    image_bytes: bytes,
    session_id: Optional[str] = None,
    api_key: Optional[str] = None,
    model_name: Optional[str] = None,
    device_motion: Optional[Dict[str, float]] = None
) -> Dict[str, Any]:
    """
    Primary server-side logic module for AI Photographer mode powered by Qwen 3.6-27B
    and Phase 4 modular intelligence pipeline.
    
    Execution Order:
    1. Retrieve/init session from session_manager.
    2. Stability check (stability.py) - fast pre-screening for motion/blur.
    3. Cheap local checks:
       - scene_classifier.py (portrait, group, object, landscape)
       - lighting_advanced.py (backlighting, harsh shadows, color temperature)
       - distance_estimator.py (framing scale & distance category)
       - background_check.py (clutter & distraction)
       - multi_subject.py (group cutoff & group eye check)
    4. Multimodal API Call (Qwen 3.6-27B) or heuristic fallback (skipped if skip_vision_api=True).
    5. Subject tracking (subject_tracker.py) - persistent identity & IoU.
    6. Rule-based composition scoring (composition_rules.py).
    7. Timing check (capture_timing.py) - eyes open, smiles, settling.
    8. User preference adaptation (preference_profile.py).
    9. Cross-validation & confidence gate (confidence_gate.py).
    10. Movement verification (closed-loop previous instruction adherence).
    11. Anti-spam capture rate limiting & variety suggestions (capture_rate_limiter.py, capture_variety.py).
    12. Session store update and rich response return.
    """
    sid = session_id or "default_session"
    now = time.time()
    session = global_session_manager.get_or_create_session(sid)
    prev_bytes = session.get("last_snapshot_bytes")

    # Step 1: Stability Pre-screening
    stability_res = stability.evaluate_stability(
        curr_bytes=image_bytes,
        prev_bytes=prev_bytes,
        device_motion=device_motion
    )
    is_camera_stable = stability_res.get("stable", True)

    # Step 2: Cheap Local Pre-Checks
    scene_res = scene_classifier.classify_scene(image_bytes)
    curr_scene_type = scene_res.get("scene_type", "portrait")
    skip_api = scene_res.get("skip_vision_api", False)

    # Initial rough subject box estimate from previous session or default center
    last_metrics = session.get("last_metrics", {})
    est_box = last_metrics.get("subject_position", {"x": 0.5, "y": 0.5, "scale": 0.35})

    lighting_adv = lighting_advanced.analyze_advanced_lighting(image_bytes, subject_box=est_box)
    dist_res = distance_estimator.estimate_subject_distance(est_box, scene_type=curr_scene_type)
    bg_res = background_check.analyze_background_clutter(image_bytes, subject_box=est_box)
    multi_res = multi_subject.analyze_multi_subject(image_bytes, scene_type=curr_scene_type)
    gh_res = golden_hour.evaluate_lighting_quality(
        has_harsh_shadows=lighting_adv.get("has_harsh_shadows", False),
        color_temperature=lighting_adv.get("color_temperature", "neutral"),
        timestamp=now
    )

    # Step 3: Multimodal Vision Model or Local Heuristic
    groq_api_key = (
        api_key
        or _RUNTIME_CONFIG.get("GROQ_API_KEY")
        or os.environ.get("GROQ_API_KEY", "")
    ).strip()
    active_model = _normalize_groq_model(
        model_name
        or _RUNTIME_CONFIG.get("GROQ_VISION_MODEL")
        or os.environ.get("GROQ_VISION_MODEL", "qwen/qwen3.6-27b")
    )
    analysis_raw = None
    backend_mode = "opencv_heuristic"

    # Only call expensive vision API if key is present and not explicitly skipped
    if groq_api_key and not (skip_api and scene_res.get("confidence", 0) >= 92):
        analysis_raw, used_model = _call_groq_vision_api(image_bytes, groq_api_key, model_name=active_model)
        if analysis_raw:
            backend_mode = f"groq_{used_model}"

    if not analysis_raw:
        analysis_raw = _analyze_heuristics_general_subject(image_bytes)

    # Extract model metrics
    subj_pos = analysis_raw.get("subject_position", {})
    curr_cx = float(subj_pos.get("x", 0.5))
    curr_cy = float(subj_pos.get("y", 0.5))
    curr_scale = float(subj_pos.get("scale", 0.35))
    refined_box = {"x": curr_cx, "y": curr_cy, "scale": curr_scale}

    framing_quality = int(analysis_raw.get("framing_quality", 70))
    suggested_direction = str(analysis_raw.get("suggested_direction", "HOLD_STEADY"))
    framing_feedback = str(analysis_raw.get("framing_feedback", "Framing analyzed."))
    lighting_assessment = str(analysis_raw.get("lighting_assessment", "Lighting balanced."))
    zoom_delta = float(analysis_raw.get("suggested_zoom_delta", 0.0))
    exp_delta = int(analysis_raw.get("suggested_exposure_delta", 0))
    subj_type = str(analysis_raw.get("subject_type", curr_scene_type))

    ai_score = analysis_raw.get("total_score")
    if ai_score is None:
        ai_score = max(0, min(100, int(framing_quality)))

    # Step 4: Subject Tracking across frames (IoU identity)
    track_res = subject_tracker.update_subject_track(refined_box, session)

    # Step 5: Deterministic Composition Rules
    comp_rules = composition_rules.evaluate_composition_rules(image_bytes, subject_box=refined_box, scene_type=subj_type)
    rule_score = comp_rules.get("rule_score", 80)

    # Step 6: Timing Check (Eyes open, smile, motion settled)
    timing_res = capture_timing.evaluate_capture_timing(image_bytes, subject_type=subj_type, subject_box=refined_box)
    eyes_open = timing_res.get("eyes_open", True) and multi_res.get("all_eyes_open", True)

    # Step 7: Apply User Preference Bias
    user_prof = preference_profile.get_user_profile(sid)
    zoom_delta = preference_profile.apply_preference_bias(zoom_delta, user_prof)

    # Blend scoring: 50% AI model + 50% deterministic composition rules
    blended_score = int(round(0.5 * ai_score + 0.5 * rule_score))

    # Incorporate advanced lighting & background checks into feedback if noteworthy
    if lighting_adv.get("is_backlit") or lighting_adv.get("has_harsh_shadows"):
        lighting_assessment = lighting_adv.get("actionable_guidance", lighting_assessment)
        exp_delta = lighting_adv.get("suggested_exposure_delta", exp_delta)
    elif gh_res.get("aesthetic_tip"):
        lighting_assessment = f"{lighting_assessment} {gh_res['aesthetic_tip']}"

    if bg_res.get("is_cluttered") and blended_score >= 70:
        framing_feedback = f"{framing_feedback} Note: {bg_res.get('guidance')}"

    if not multi_res.get("group_balanced"):
        framing_feedback = multi_res.get("guidance", framing_feedback)

    # Step 8: Closed-Loop Movement Verification
    diff_dx, diff_dy, diff_dscale = _calculate_opencv_motion_diff(prev_bytes, image_bytes)
    prev_pos = last_metrics.get("subject_position", {})
    prev_cx = float(prev_pos.get("x", curr_cx))
    prev_cy = float(prev_pos.get("y", curr_cy))
    prev_scale = float(prev_pos.get("scale", curr_scale))

    effective_dx = diff_dx if abs(diff_dx) > 0.015 else (curr_cx - prev_cx)
    effective_dy = diff_dy if abs(diff_dy) > 0.015 else (curr_cy - prev_cy)
    effective_dscale = diff_dscale if abs(diff_dscale) > 0.015 else (curr_scale - prev_scale)

    previous_instruction_followed = True
    correction_note: Optional[str] = None
    guidance_stuck = False
    attempt_count = 0

    has_prior_history = (prev_bytes is not None) or bool(last_metrics) or (session.get("last_instruction") not in (None, "HOLD_STEADY"))

    if has_prior_history:
        last_instr = session.get("last_instruction", "HOLD_STEADY")
        prev_attempts = session.get("attempt_count", 0)

        if last_instr in ("ORBIT_LEFT", "MOVE_LEFT"):
            if effective_dx >= 0.02:
                previous_instruction_followed = True
                correction_note = "Good adjustment! Movement registered."
            elif effective_dx <= -0.025:
                previous_instruction_followed = False
                correction_note = "You moved right instead of left — please reverse"
            elif abs(effective_dx) < 0.02 and abs(effective_dy) < 0.03:
                previous_instruction_followed = False
                correction_note = "No movement detected — please continue moving left"
            elif effective_dx > 0.38:
                previous_instruction_followed = False
                correction_note = "Moved too far left — ease back toward center"

        elif last_instr in ("ORBIT_RIGHT", "MOVE_RIGHT"):
            if effective_dx <= -0.02:
                previous_instruction_followed = True
                correction_note = "Good adjustment! Movement registered."
            elif effective_dx >= 0.025:
                previous_instruction_followed = False
                correction_note = "You moved left instead of right — please reverse"
            elif abs(effective_dx) < 0.02 and abs(effective_dy) < 0.03:
                previous_instruction_followed = False
                correction_note = "No movement detected — please continue moving right"
            elif effective_dx < -0.38:
                previous_instruction_followed = False
                correction_note = "Moved too far right — ease back toward center"

        elif last_instr == "MOVE_UP":
            if effective_dy >= 0.02:
                previous_instruction_followed = True
                correction_note = "Good adjustment! Tilting up registered."
            elif effective_dy <= -0.025:
                previous_instruction_followed = False
                correction_note = "You tilted down instead of up — please tilt up"
            elif abs(effective_dy) < 0.02:
                previous_instruction_followed = False
                correction_note = "No tilt detected — please tilt camera up"

        elif last_instr == "MOVE_DOWN":
            if effective_dy <= -0.02:
                previous_instruction_followed = True
                correction_note = "Good adjustment! Tilting down registered."
            elif effective_dy >= 0.025:
                previous_instruction_followed = False
                correction_note = "You tilted up instead of down — please tilt down"
            elif abs(effective_dy) < 0.02:
                previous_instruction_followed = False
                correction_note = "No tilt detected — please tilt camera down"

        elif last_instr == "STEP_BACK":
            if effective_dscale <= -0.02:
                previous_instruction_followed = True
                correction_note = "Distance adjusted well."
            elif effective_dscale >= 0.03:
                previous_instruction_followed = False
                correction_note = "You moved closer instead of stepping back — please step back"
            elif abs(effective_dscale) < 0.02:
                previous_instruction_followed = False
                correction_note = "No distance change detected — please step back"

        elif last_instr == "STEP_CLOSER":
            if effective_dscale >= 0.02:
                previous_instruction_followed = True
                correction_note = "Distance adjusted well."
            elif effective_dscale <= -0.03:
                previous_instruction_followed = False
                correction_note = "You moved farther away — please step closer"
            elif abs(effective_dscale) < 0.02:
                previous_instruction_followed = False
                correction_note = "No distance change detected — please step closer"

        elif last_instr == "HOLD_STEADY":
            if abs(effective_dx) < 0.04 and abs(effective_dy) < 0.04:
                previous_instruction_followed = True
                correction_note = "Holding steady."
            else:
                previous_instruction_followed = False
                correction_note = "Camera shifted — please hold steady"

        if not previous_instruction_followed:
            attempt_count = prev_attempts + 1
            if attempt_count >= 3:
                guidance_stuck = True
                correction_note = "Having trouble detecting movement — feel free to adjust manually and tap capture"
        else:
            attempt_count = 0
            guidance_stuck = False

    # Step 9: Confidence Gate & Trust Evaluation
    conf_gate = confidence_gate.evaluate_confidence_gate(
        rule_score=rule_score,
        ai_score=ai_score,
        is_stable=is_camera_stable,
        eyes_open=eyes_open,
        has_harsh_shadows=lighting_adv.get("has_harsh_shadows", False),
        is_backlit=lighting_adv.get("is_backlit", False)
    )

    # Instruction Sequencing
    active_direction = suggested_direction
    active_feedback = framing_feedback

    if not previous_instruction_followed and has_prior_history and not guidance_stuck:
        active_direction = session.get("last_instruction", suggested_direction)
        active_feedback = correction_note or framing_feedback
    elif guidance_stuck:
        active_direction = "HOLD_STEADY"
        active_feedback = "Adjust manually and tap capture"
    elif not is_camera_stable:
        active_direction = "HOLD_STEADY"
        active_feedback = stability_res.get("feedback", "Hold steady — camera movement detected.")
    elif not eyes_open:
        active_direction = "HOLD_STEADY"
        active_feedback = timing_res.get("guidance", "Eyes closed — please keep eyes open!")

    # Step 10: Multi-frame Capture Readiness & Rate Limiting
    tentative_ready = (
        blended_score >= 75
        and active_direction == "HOLD_STEADY"
        and previous_instruction_followed
        and not guidance_stuck
        and is_camera_stable
        and eyes_open
        and conf_gate.get("is_trusted", True)
    )

    capture_state = session.setdefault("capture_state", {
        "last_capture_time": 0.0,
        "consecutive_ready_count": 0,
        "post_capture_active": False,
        "post_capture_variety": None
    })

    rate_check = capture_rate_limiter.evaluate_capture_permission(
        tentative_ready=tentative_ready,
        session_capture_state=capture_state,
        now=now
    )
    final_ready_to_capture = rate_check.get("trigger_capture", False)

    # Step 11: Post-Capture Variety Prompt
    variety_data = None
    if capture_state.get("post_capture_active", False):
        user_prof_latest = preference_profile.get_user_profile(sid)
        captured_total = user_prof_latest.get("auto_captures", 0) + user_prof_latest.get("manual_captures", 0) + 1
        variety_data = capture_variety.get_post_capture_variety(captured_total, current_scene_type=subj_type)
        if final_ready_to_capture:
            active_feedback = "Capturing now! Hold steady."
        elif rate_check.get("is_on_cooldown", False) and variety_data:
            active_feedback = variety_data.get("next_framing_suggestion", active_feedback)

    # Step 12: Update Session Store via session_manager
    session_history = session.get("history", deque(maxlen=10))
    session_history.append({
        "timestamp": now,
        "direction": active_direction,
        "score": blended_score
    })

    session_updates = {
        "last_snapshot_bytes": image_bytes,
        "last_metrics": {
            "subject_position": refined_box,
            "framing_quality": framing_quality,
            "suggested_direction": active_direction,
            "framing_feedback": active_feedback,
            "lighting_assessment": lighting_assessment,
            "suggested_zoom_delta": zoom_delta,
            "suggested_exposure_delta": exp_delta,
            "ready_to_capture": final_ready_to_capture
        },
        "last_instruction": active_direction,
        "attempt_count": attempt_count,
        "guidance_stuck": guidance_stuck,
        "history": session_history,
        "capture_state": capture_state
    }
    global_session_manager.update_session(sid, session_updates)

    # Log action if capture triggered
    if final_ready_to_capture:
        preference_profile.record_user_action(sid, "auto_capture", zoom_delta=zoom_delta)

    return {
        "score": int(blended_score),
        "framing_feedback": str(active_feedback),
        "lighting_feedback": str(lighting_assessment),
        "suggested_zoom_delta": round(float(zoom_delta), 2),
        "suggested_move_direction": str(active_direction),
        "ready_to_capture": bool(final_ready_to_capture),
        "previous_instruction_followed": bool(previous_instruction_followed),
        "correction_note": str(correction_note) if correction_note else None,
        "guidance_stuck": bool(guidance_stuck),
        "subject_type": str(subj_type),
        "backend_mode": str(backend_mode),
        "stability": stability_res,
        "confidence": conf_gate,
        "composition_rules": comp_rules,
        "distance": dist_res,
        "variety_suggestion": variety_data.get("next_framing_suggestion") if variety_data else None
    }
