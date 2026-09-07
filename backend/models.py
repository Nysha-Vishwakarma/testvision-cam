from typing import List, Dict, Optional, Any
try:
    from pydantic import BaseModel, Field
    HAS_PYDANTIC = True
except ImportError:
    HAS_PYDANTIC = False
    class BaseModel:
        def __init__(self, **kwargs):
            for k, v in kwargs.items():
                setattr(self, k, v)
        def model_dump(self) -> Dict[str, Any]:
            result = {}
            for k, v in self.__dict__.items():
                if isinstance(v, list):
                    result[k] = [item.model_dump() if hasattr(item, "model_dump") else item for item in v]
                elif hasattr(v, "model_dump"):
                    result[k] = v.model_dump()
                else:
                    result[k] = v
            return result
    def Field(default=None, **kwargs):
        return default

class Keypoint(BaseModel):
    name: str
    x: float
    y: float
    z: float = 0.0
    visibility: float = 1.0

    def __init__(self, name: str, x: float, y: float, z: float = 0.0, visibility: float = 1.0, **kwargs):
        if HAS_PYDANTIC:
            super().__init__(name=name, x=x, y=y, z=z, visibility=visibility, **kwargs)
        else:
            self.name = str(name)
            self.x = float(x)
            self.y = float(y)
            self.z = float(z)
            self.visibility = float(visibility)

class PoseSkeleton(BaseModel):
    id: str
    name: str
    keypoints: List[Keypoint]
    joint_angles: Dict[str, float] = None

    def __init__(self, id: str, name: str, keypoints: List[Keypoint], joint_angles: Optional[Dict[str, float]] = None, **kwargs):
        angles = joint_angles or {}
        if HAS_PYDANTIC:
            super().__init__(id=id, name=name, keypoints=keypoints, joint_angles=angles, **kwargs)
        else:
            self.id = str(id)
            self.name = str(name)
            self.keypoints = keypoints
            self.joint_angles = {str(k): float(v) for k, v in angles.items()}

class LimbCorrection(BaseModel):
    limb: str
    instruction: str
    delta_degrees: float
    status: str

    def __init__(self, limb: str, instruction: str, delta_degrees: float, status: str, **kwargs):
        if HAS_PYDANTIC:
            super().__init__(limb=limb, instruction=instruction, delta_degrees=delta_degrees, status=status, **kwargs)
        else:
            self.limb = str(limb)
            self.instruction = str(instruction)
            self.delta_degrees = float(delta_degrees)
            self.status = str(status)

class PoseCompareRequest(BaseModel):
    target_skeleton: PoseSkeleton
    snapshot_base64: Optional[str] = None

class PoseCompareResponse(BaseModel):
    no_pose_detected: bool = False
    match_score: Optional[int] = None
    is_matched: bool = False
    status: str = "NO_POSE_DETECTED"
    primary_feedback: str = "No pose detected — step into frame"
    limb_corrections: List[LimbCorrection] = []
    aligned_limbs: List[str] = []
    user_skeleton: Optional[PoseSkeleton] = None

    def __init__(
        self,
        no_pose_detected: bool = False,
        match_score: Optional[int] = None,
        is_matched: bool = False,
        status: str = "NO_POSE_DETECTED",
        primary_feedback: str = "No pose detected — step into frame",
        limb_corrections: Optional[List[LimbCorrection]] = None,
        aligned_limbs: Optional[List[str]] = None,
        user_skeleton: Optional[PoseSkeleton] = None,
        **kwargs
    ):
        corrections = limb_corrections if limb_corrections is not None else []
        aligned = aligned_limbs if aligned_limbs is not None else []
        if HAS_PYDANTIC:
            super().__init__(
                no_pose_detected=no_pose_detected,
                match_score=match_score,
                is_matched=is_matched,
                status=status,
                primary_feedback=primary_feedback,
                limb_corrections=corrections,
                aligned_limbs=aligned,
                user_skeleton=user_skeleton,
                **kwargs
            )
        else:
            self.no_pose_detected = bool(no_pose_detected)
            self.match_score = int(match_score) if match_score is not None else None
            self.is_matched = bool(is_matched)
            self.status = str(status)
            self.primary_feedback = str(primary_feedback)
            self.limb_corrections = corrections
            self.aligned_limbs = [str(x) for x in aligned]
            self.user_skeleton = user_skeleton

class PoseLibraryItem(BaseModel):
    id: str
    name: str
    category: str
    description: str
    difficulty: str
    thumbnail_url: str
    skeleton: PoseSkeleton

    def __init__(
        self,
        id: str,
        name: str,
        category: str,
        description: str,
        difficulty: str,
        thumbnail_url: str,
        skeleton: PoseSkeleton,
        **kwargs
    ):
        if HAS_PYDANTIC:
            super().__init__(
                id=id,
                name=name,
                category=category,
                description=description,
                difficulty=difficulty,
                thumbnail_url=thumbnail_url,
                skeleton=skeleton,
                **kwargs
            )
        else:
            self.id = str(id)
            self.name = str(name)
            self.category = str(category)
            self.description = str(description)
            self.difficulty = str(difficulty)
            self.thumbnail_url = str(thumbnail_url)
            self.skeleton = skeleton

class CompositionAnalysisResponse(BaseModel):
    score: int
    framing_feedback: str
    lighting_feedback: str
    suggested_zoom_delta: float = 0.0
    suggested_move_direction: str = "HOLD_STEADY"
    ready_to_capture: bool = False
    previous_instruction_followed: bool = True
    correction_note: Optional[str] = None
    guidance_stuck: bool = False
    subject_type: str = "general"
    backend_mode: str = "groq_vision"
    stability: Optional[Dict[str, Any]] = None
    confidence: Optional[Dict[str, Any]] = None
    composition_rules: Optional[Dict[str, Any]] = None
    distance: Optional[Dict[str, Any]] = None
    variety_suggestion: Optional[str] = None

    def __init__(
        self,
        score: int = 0,
        framing_feedback: str = "",
        lighting_feedback: str = "",
        suggested_zoom_delta: float = 0.0,
        suggested_move_direction: str = "HOLD_STEADY",
        ready_to_capture: bool = False,
        previous_instruction_followed: bool = True,
        correction_note: Optional[str] = None,
        guidance_stuck: bool = False,
        subject_type: str = "general",
        backend_mode: str = "groq_vision",
        stability: Optional[Dict[str, Any]] = None,
        confidence: Optional[Dict[str, Any]] = None,
        composition_rules: Optional[Dict[str, Any]] = None,
        distance: Optional[Dict[str, Any]] = None,
        variety_suggestion: Optional[str] = None,
        **kwargs
    ):
        if HAS_PYDANTIC:
            super().__init__(
                score=score,
                framing_feedback=framing_feedback,
                lighting_feedback=lighting_feedback,
                suggested_zoom_delta=suggested_zoom_delta,
                suggested_move_direction=suggested_move_direction,
                ready_to_capture=ready_to_capture,
                previous_instruction_followed=previous_instruction_followed,
                correction_note=correction_note,
                guidance_stuck=guidance_stuck,
                subject_type=subject_type,
                backend_mode=backend_mode,
                stability=stability,
                confidence=confidence,
                composition_rules=composition_rules,
                distance=distance,
                variety_suggestion=variety_suggestion,
                **kwargs
            )
        else:
            self.score = int(score)
            self.framing_feedback = str(framing_feedback)
            self.lighting_feedback = str(lighting_feedback)
            self.suggested_zoom_delta = float(suggested_zoom_delta)
            self.suggested_move_direction = str(suggested_move_direction)
            self.ready_to_capture = bool(ready_to_capture)
            self.previous_instruction_followed = bool(previous_instruction_followed)
            self.correction_note = str(correction_note) if correction_note is not None else None
            self.guidance_stuck = bool(guidance_stuck)
            self.subject_type = str(subject_type)
            self.backend_mode = str(backend_mode)
            self.stability = stability
            self.confidence = confidence
            self.composition_rules = composition_rules
            self.distance = distance
            self.variety_suggestion = variety_suggestion

class SmartGridPoseResponse(BaseModel):
    face_detected: bool
    pitch: float
    yaw: float
    roll: float
    rotation_matrix: List[List[float]]
    translation_vector: List[float]
    face_center: List[float]
    is_level: bool
    near_intersection: bool
    composition_note: str

    def __init__(
        self,
        face_detected: bool = False,
        pitch: float = 0.0,
        yaw: float = 0.0,
        roll: float = 0.0,
        rotation_matrix: Optional[List[List[float]]] = None,
        translation_vector: Optional[List[float]] = None,
        face_center: Optional[List[float]] = None,
        is_level: bool = True,
        near_intersection: bool = False,
        composition_note: str = "",
        **kwargs
    ):
        rot = rotation_matrix or [[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]
        trans = translation_vector or [0.0, 0.0, 0.0]
        fc = face_center or [0.5, 0.5]
        if HAS_PYDANTIC:
            super().__init__(
                face_detected=face_detected,
                pitch=pitch,
                yaw=yaw,
                roll=roll,
                rotation_matrix=rot,
                translation_vector=trans,
                face_center=fc,
                is_level=is_level,
                near_intersection=near_intersection,
                composition_note=composition_note,
                **kwargs
            )
        else:
            self.face_detected = bool(face_detected)
            self.pitch = float(pitch)
            self.yaw = float(yaw)
            self.roll = float(roll)
            self.rotation_matrix = rot
            self.translation_vector = trans
            self.face_center = fc
            self.is_level = bool(is_level)
            self.near_intersection = bool(near_intersection)
            self.composition_note = str(composition_note)


