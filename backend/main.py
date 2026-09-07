import os
import base64
import io
import json
import logging
from typing import Optional, List
from pydantic import BaseModel
from fastapi import FastAPI, UploadFile, File, Form, Header, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from models import (
    Keypoint,
    PoseSkeleton,
    PoseCompareRequest,
    PoseCompareResponse,
    PoseLibraryItem,
    CompositionAnalysisResponse,
    SmartGridPoseResponse
)
from database import init_db, get_all_poses, get_pose_by_id, log_ai_photo_session, log_smart_grid_pose
from composition_scorer import score_composition
from verification import (
    verify_and_analyze_frame,
    get_session_store_stats,
    get_runtime_groq_config,
    set_runtime_groq_config
)
from rate_limiter import get_rate_limiter_status, set_max_otpm
from head_pose_estimator import analyze_head_pose_from_bytes
from pose_processor import (
    calculate_angle,
    extract_joint_angles,
    compare_skeletons,
    POSE_CONNECTIONS
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("PoseGuideBackend")

app = FastAPI(
    title="Pose Guide Backend API",
    version="1.0.0",
    description="MediaPipe-based Pose extraction, library management, and real-time posture comparison service."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize database on startup
@app.on_event("startup")
def on_startup():
    init_db()
    logger.info("Database initialized with pre-computed pose library.")

@app.get("/")
def root():
    return {
        "status": "online",
        "service": "AI Photographer & Pose Guide Backend",
        "endpoints": {
            "docs": "/docs",
            "health": "/health",
            "analyze_frame": "/api/v1/analyze-frame",
            "pose_library": "/api/v1/poses"
        }
    }

@app.get("/health")
def health_check():
    return {"status": "ok", "service": "pose-guide-backend", "version": "1.0.0"}

# Helper to process image bytes into normalized keypoints
def extract_pose_from_bytes(image_bytes: bytes, pose_name: str = "extracted_pose") -> Optional[PoseSkeleton]:
    """
    Extracts pose keypoints from raw image bytes using MediaPipe Pose.
    Returns None if no person or valid posture is detected, or if image cannot be decoded.
    """
    try:
        import mediapipe as mp
        import cv2
        import numpy as np

        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        if img is None:
            logger.warning("Could not decode image bytes")
            return None

        mp_pose = mp.solutions.pose
        with mp_pose.Pose(static_image_mode=True, min_detection_confidence=0.5) as pose:
            results = pose.process(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
            if not results or not results.pose_landmarks:
                logger.info("MediaPipe: No pose landmarks detected in frame")
                return None

            keypoints: List[Keypoint] = []
            landmark_names = [
                "nose", "left_eye_inner", "left_eye", "left_eye_outer",
                "right_eye_inner", "right_eye", "right_eye_outer",
                "left_ear", "right_ear", "mouth_left", "mouth_right",
                "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
                "left_wrist", "right_wrist", "left_pinky", "right_pinky",
                "left_index", "right_index", "left_thumb", "right_thumb",
                "left_hip", "right_hip", "left_knee", "right_knee",
                "left_ankle", "right_ankle", "left_heel", "right_heel",
                "left_foot_index", "right_foot_index"
            ]

            for idx, landmark in enumerate(results.pose_landmarks.landmark):
                name = landmark_names[idx] if idx < len(landmark_names) else f"kp_{idx}"
                keypoints.append(Keypoint(
                    name=name,
                    x=round(float(landmark.x), 4),
                    y=round(float(landmark.y), 4),
                    z=round(float(landmark.z), 4),
                    visibility=round(float(landmark.visibility), 3)
                ))

            kp_dict = {k.name: k for k in keypoints}
            angles = extract_joint_angles(kp_dict, min_visibility=0.5)

            return PoseSkeleton(
                id=f"extracted_{abs(hash(pose_name)) % 100000}",
                name=pose_name,
                keypoints=keypoints,
                joint_angles=angles
            )
    except Exception as e:
        logger.warning(f"Pose extraction failed or mediapipe not installed: {e}")
        return None

# ==========================================================
# ENDPOINT 1: POST Image Upload -> Normalized Skeleton JSON
# ==========================================================
@app.post("/api/v1/pose/extract", response_model=PoseSkeleton)
async def extract_pose(
    file: Optional[UploadFile] = File(None),
    image_base64: Optional[str] = Form(None),
    pose_name: str = Form("Custom Pose")
):
    """
    Endpoint 1: Upload a Pinterest or custom pose image (via multipart file or base64)
    and receive normalized keypoints and calculated joint angles in clean JSON format.
    """
    image_bytes = None
    if file:
        image_bytes = await file.read()
    elif image_base64:
        # Strip data URL prefix if present
        clean_b64 = image_base64.split(",")[-1] if "," in image_base64 else image_base64
        try:
            image_bytes = base64.b64decode(clean_b64)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid base64 image data: {e}")

    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Must provide either multipart 'file' or 'image_base64'"
        )

    try:
        skeleton = extract_pose_from_bytes(image_bytes, pose_name=pose_name)
        if skeleton is None:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="No person or valid posture detected in image"
            )
        return skeleton
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error during pose extraction: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ==========================================================
# ENDPOINT 2: POST Live Snapshot + Target Skeleton -> Match Score & Limb Corrections
# ==========================================================
@app.post("/api/v1/pose/compare", response_model=PoseCompareResponse)
async def compare_pose(
    target_skeleton: str = Form(...),
    snapshot: Optional[UploadFile] = File(None),
    snapshot_base64: Optional[str] = Form(None)
):
    """
    Endpoint 2: Compares a live camera snapshot against target pose skeleton.
    Returns match score (0-100), primary directional cue, and detailed limb corrections.
    If no pose or insufficient confidence, returns no_pose_detected=True and match_score=None.
    """
    try:
        target_dict = json.loads(target_skeleton)
        target = PoseSkeleton(**target_dict)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid target_skeleton JSON: {e}")

    image_bytes = None
    if snapshot:
        image_bytes = await snapshot.read()
    elif snapshot_base64:
        clean_b64 = snapshot_base64.split(",")[-1] if "," in snapshot_base64 else snapshot_base64
        try:
            image_bytes = base64.b64decode(clean_b64)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid base64 snapshot: {e}")

    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Must provide either multipart 'snapshot' or 'snapshot_base64'"
        )

    try:
        live_skeleton = extract_pose_from_bytes(image_bytes, pose_name="live_user")
        if live_skeleton is None:
            return PoseCompareResponse(
                no_pose_detected=True,
                match_score=None,
                is_matched=False,
                status="NO_POSE_DETECTED",
                primary_feedback="No pose detected — step into frame",
                limb_corrections=[],
                aligned_limbs=[],
                user_skeleton=None
            )

        response = compare_skeletons(target=target, live=live_skeleton)
        return response
    except Exception as e:
        logger.error(f"Pose comparison error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ==========================================================
# ENDPOINT 3: GET Pose Library (Pre-made Poses + Cached Skeletons)
# ==========================================================
@app.get("/api/v1/pose/library", response_model=List[PoseLibraryItem])
def get_pose_library():
    """
    Endpoint 3: Returns curated pose library with pre-computed skeletons and metadata.
    Avoids re-extracting skeletons on client or server per request.
    """
    try:
        poses = get_all_poses()
        return poses
    except Exception as e:
        logger.error(f"Error reading pose library: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ==========================================================
# ENDPOINT 4: POST /api/v1/analyze-frame & /analyze-frame (AI Photographer Mode)
# ==========================================================
@app.post("/api/v1/analyze-frame", response_model=CompositionAnalysisResponse)
@app.post("/analyze-frame", response_model=CompositionAnalysisResponse)
async def analyze_frame(
    snapshot: Optional[UploadFile] = File(None),
    file: Optional[UploadFile] = File(None),
    snapshot_base64: Optional[str] = Form(None),
    image_base64: Optional[str] = Form(None),
    session_id: Optional[str] = Form("default_session"),
    device_motion: Optional[str] = Form(None),
    groq_api_key: Optional[str] = Form(None),
    x_groq_api_key: Optional[str] = Header(None),
    groq_model: Optional[str] = Form(None),
    x_groq_model: Optional[str] = Header(None)
):
    """
    Endpoint 4: Analyzes live camera snapshot for scene composition, subject framing,
    and lighting balance powered by Qwen 3.6-27B multimodal vision.
    Returns: { score, framing_feedback, lighting_feedback, suggested_zoom_delta, suggested_move_direction, ready_to_capture, backend_mode }
    """
    image_bytes = None
    upload = snapshot or file
    if upload:
        image_bytes = await upload.read()
    else:
        b64 = snapshot_base64 or image_base64
        if b64:
            clean_b64 = b64.split(",")[-1] if "," in b64 else b64
            try:
                image_bytes = base64.b64decode(clean_b64)
            except Exception as e:
                raise HTTPException(status_code=400, detail=f"Invalid base64 image: {e}")

    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Must provide either snapshot file or base64 image data"
        )

    api_key_override = (groq_api_key or x_groq_api_key or "").strip() or None
    model_override = (groq_model or x_groq_model or "").strip() or None

    parsed_motion = None
    if device_motion:
        try:
            parsed_motion = json.loads(device_motion)
        except Exception:
            pass

    try:
        result = verify_and_analyze_frame(
            image_bytes=image_bytes,
            session_id=session_id,
            api_key=api_key_override,
            model_name=model_override,
            device_motion=parsed_motion
        )
        log_ai_photo_session(
            session_id=session_id or "default_session",
            score=result["score"],
            framing_feedback=result["framing_feedback"],
            lighting_feedback=result["lighting_feedback"],
            suggested_zoom_delta=result["suggested_zoom_delta"],
            suggested_move_direction=result["suggested_move_direction"],
            ready_to_capture=result["ready_to_capture"],
            previous_instruction_followed=result.get("previous_instruction_followed", True),
            correction_note=result.get("correction_note"),
            guidance_stuck=result.get("guidance_stuck", False)
        )
        return CompositionAnalysisResponse(**result)
    except Exception as e:
        logger.error(f"Frame analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/config")
@app.get("/config")
def get_config():
    """Returns AI Photographer configuration status, rate limiter metrics, and Qwen model info."""
    cfg = get_runtime_groq_config()
    stats = get_session_store_stats()
    return {
        "status": "ok",
        "vision_model": cfg["model"],
        "groq_api_key_configured": cfg["groq_api_key_configured"],
        "groq_api_key_preview": cfg["groq_api_key_preview"],
        "active_sessions": stats["active_sessions"],
        "rate_limiter": cfg.get("rate_limiter", {})
    }

class GroqConfigPayload(BaseModel):
    api_key: str
    model: Optional[str] = "qwen/qwen3.6-27b"
    max_otpm: Optional[int] = None

@app.post("/api/v1/config/groq")
@app.post("/config/groq")
def update_groq_config(payload: GroqConfigPayload):
    """Sets and persists Groq API key and activates Qwen 3.6-27B vision model with optional OTPM limit."""
    api_key = payload.api_key.strip()
    model = "qwen/qwen3.6-27b"
    if not api_key:
        raise HTTPException(status_code=400, detail="API key cannot be empty")

    set_runtime_groq_config(api_key=api_key, model=model)
    if payload.max_otpm is not None and payload.max_otpm > 0:
        set_max_otpm(payload.max_otpm)

    # Persist to backend/.env
    env_file = os.path.join(os.path.dirname(__file__), ".env")
    try:
        lines = []
        if os.path.exists(env_file):
            with open(env_file, "r", encoding="utf-8") as f:
                lines = f.readlines()

        updated_key = False
        updated_model = False
        new_lines = []
        for l in lines:
            if l.startswith("GROQ_API_KEY="):
                new_lines.append(f"GROQ_API_KEY={api_key}\n")
                updated_key = True
            elif l.startswith("GROQ_VISION_MODEL="):
                new_lines.append(f"GROQ_VISION_MODEL={model}\n")
                updated_model = True
            else:
                new_lines.append(l)
        if not updated_key:
            new_lines.append(f"GROQ_API_KEY={api_key}\n")
        if not updated_model:
            new_lines.append(f"GROQ_VISION_MODEL={model}\n")

        with open(env_file, "w", encoding="utf-8") as f:
            f.writelines(new_lines)
    except Exception as e:
        logger.warning(f"Could not persist to .env: {e}")

    return {
        "success": True,
        "message": f"Groq API key configured successfully with model {model}",
        "model": model,
        "api_key_preview": (api_key[:7] + "..." + api_key[-4:]) if len(api_key) >= 12 else "configured"
    }

@app.get("/api/v1/sessions")
@app.get("/sessions")
def get_active_sessions():
    """Returns active verification session store diagnostics."""
    return get_session_store_stats()


# ==========================================================
# ENDPOINT 5: POST /api/v1/smart-grid-pose (Smart Grid Periodic Deep Pose Analysis)
# ==========================================================
@app.post("/api/v1/smart-grid-pose", response_model=SmartGridPoseResponse)
@app.post("/smart-grid-pose", response_model=SmartGridPoseResponse)
async def analyze_smart_grid_pose(
    snapshot: Optional[UploadFile] = File(None),
    file: Optional[UploadFile] = File(None),
    snapshot_base64: Optional[str] = Form(None),
    image_base64: Optional[str] = Form(None),
    session_id: Optional[str] = Form("smart_grid_session")
):
    """
    Endpoint 5: Analyzes periodic snapshot captured during Smart Grid mode.
    Extracts 3D facial landmarks, runs solvePnP for precise rotation matrix (R) and
    translation vector (t), extracts Euler angles (pitch, yaw, roll), checks levelness,
    and returns compositional feedback. Logs session data for future tuning.
    All outputs cast to native Python types.
    """
    image_bytes = None
    upload = snapshot or file
    if upload:
        image_bytes = await upload.read()
    else:
        b64 = snapshot_base64 or image_base64
        if b64:
            clean_b64 = b64.split(",")[-1] if "," in b64 else b64
            try:
                image_bytes = base64.b64decode(clean_b64)
            except Exception as e:
                raise HTTPException(status_code=400, detail=f"Invalid base64 image: {e}")

    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Must provide either snapshot file or base64 image data"
        )

    try:
        res = analyze_head_pose_from_bytes(image_bytes)

        # Log periodic reading to SQLite database
        log_smart_grid_pose(
            session_id=session_id or "smart_grid_session",
            face_detected=res.get("face_detected", False),
            pitch=res.get("pitch", 0.0),
            yaw=res.get("yaw", 0.0),
            roll=res.get("roll", 0.0),
            face_center=res.get("face_center", [0.5, 0.5]),
            is_level=res.get("is_level", True),
            near_intersection=res.get("near_intersection", False),
            composition_note=res.get("composition_note", "")
        )

        return SmartGridPoseResponse(**res)
    except Exception as e:
        logger.error(f"Smart Grid pose analysis error: {e}")
        raise HTTPException(status_code=500, detail=str(e))


