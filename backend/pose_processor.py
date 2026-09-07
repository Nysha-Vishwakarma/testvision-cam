import math
from typing import Dict, List, Tuple, Optional
from models import Keypoint, PoseSkeleton, LimbCorrection, PoseCompareResponse

# Common limb connections for skeleton rendering and calculation
POSE_CONNECTIONS = [
    ("left_shoulder", "right_shoulder"),
    ("left_shoulder", "left_elbow"),
    ("left_elbow", "left_wrist"),
    ("right_shoulder", "right_elbow"),
    ("right_elbow", "right_wrist"),
    ("left_shoulder", "left_hip"),
    ("right_shoulder", "right_hip"),
    ("left_hip", "right_hip"),
    ("left_hip", "left_knee"),
    ("left_knee", "left_ankle"),
    ("right_hip", "right_knee"),
    ("right_knee", "right_ankle"),
]

CONFIDENCE_THRESHOLD = 0.5
MIN_CONFIDENT_KEYPOINTS = 6
MIN_EVALUATED_ANGLES = 3

def is_keypoint_confident(kp: Optional[Keypoint], threshold: float = CONFIDENCE_THRESHOLD) -> bool:
    """Check if keypoint exists and exceeds visibility confidence threshold."""
    if kp is None:
        return False
    return getattr(kp, "visibility", 1.0) >= threshold

def validate_pose_keypoints(keypoints_by_name: Dict[str, Keypoint]) -> Tuple[bool, str]:
    """
    Validates that a detected pose has sufficient confident landmarks for comparison.
    Requires:
    1. At least MIN_CONFIDENT_KEYPOINTS with visibility >= 0.5
    2. Core torso landmarks present (at least one shoulder and one hip, or both shoulders)
    3. Major tracked joints have sufficient visibility
    """
    if not keypoints_by_name:
        return False, "No keypoints provided"

    # 1. Total confident keypoints
    confident_kps = [
        kp for kp in keypoints_by_name.values()
        if is_keypoint_confident(kp, CONFIDENCE_THRESHOLD)
    ]
    if len(confident_kps) < MIN_CONFIDENT_KEYPOINTS:
        return False, f"Too few confident keypoints: {len(confident_kps)} < {MIN_CONFIDENT_KEYPOINTS}"

    # 2. Core landmarks check (shoulders and hips)
    l_sh = keypoints_by_name.get("left_shoulder")
    r_sh = keypoints_by_name.get("right_shoulder")
    l_hip = keypoints_by_name.get("left_hip")
    r_hip = keypoints_by_name.get("right_hip")

    sh_visible = is_keypoint_confident(l_sh) or is_keypoint_confident(r_sh)
    hip_visible = is_keypoint_confident(l_hip) or is_keypoint_confident(r_hip)
    both_sh_visible = is_keypoint_confident(l_sh) and is_keypoint_confident(r_sh)

    if not (both_sh_visible or (sh_visible and hip_visible)):
        return False, "Core torso landmarks (shoulders/hips) not sufficiently visible"

    # 3. Tracked joints check (elbows, knees, etc.)
    tracked_names = [
        "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
        "left_wrist", "right_wrist", "left_hip", "right_hip",
        "left_knee", "right_knee", "left_ankle", "right_ankle"
    ]
    confident_tracked = [
        name for name in tracked_names
        if is_keypoint_confident(keypoints_by_name.get(name))
    ]
    if len(confident_tracked) < 5:
        return False, f"Too few tracked joints: {len(confident_tracked)} < 5"

    return True, "Valid pose"

def calculate_angle(p1: Keypoint, p2: Keypoint, p3: Keypoint) -> float:
    """
    Calculate 2D joint angle in degrees at vertex p2 between (p1 - p2) and (p3 - p2).
    Returns value in range [0.0, 180.0].
    """
    v1_x = float(p1.x - p2.x)
    v1_y = float(p1.y - p2.y)
    v2_x = float(p3.x - p2.x)
    v2_y = float(p3.y - p2.y)

    dot = v1_x * v2_x + v1_y * v2_y
    mag1 = math.sqrt(v1_x * v1_x + v1_y * v1_y)
    mag2 = math.sqrt(v2_x * v2_x + v2_y * v2_y)

    if mag1 < 1e-6 or mag2 < 1e-6:
        return 0.0

    cos_val = dot / (mag1 * mag2)
    cos_val = max(-1.0, min(1.0, cos_val))
    angle_rad = math.acos(cos_val)
    return round(float(math.degrees(angle_rad)), 1)

def extract_joint_angles(keypoints_by_name: Dict[str, Keypoint], min_visibility: float = 0.5) -> Dict[str, float]:
    """Calculate key joint angles from confident keypoints."""
    def confident(name: str) -> bool:
        return is_keypoint_confident(keypoints_by_name.get(name), min_visibility)

    angles = {}

    # Left elbow (shoulder -> elbow -> wrist)
    if confident("left_shoulder") and confident("left_elbow") and confident("left_wrist"):
        angles["left_elbow"] = calculate_angle(
            keypoints_by_name["left_shoulder"],
            keypoints_by_name["left_elbow"],
            keypoints_by_name["left_wrist"]
        )

    # Right elbow (shoulder -> elbow -> wrist)
    if confident("right_shoulder") and confident("right_elbow") and confident("right_wrist"):
        angles["right_elbow"] = calculate_angle(
            keypoints_by_name["right_shoulder"],
            keypoints_by_name["right_elbow"],
            keypoints_by_name["right_wrist"]
        )

    # Left shoulder (elbow -> shoulder -> hip)
    if confident("left_elbow") and confident("left_shoulder") and confident("left_hip"):
        angles["left_shoulder"] = calculate_angle(
            keypoints_by_name["left_elbow"],
            keypoints_by_name["left_shoulder"],
            keypoints_by_name["left_hip"]
        )

    # Right shoulder (elbow -> shoulder -> hip)
    if confident("right_elbow") and confident("right_shoulder") and confident("right_hip"):
        angles["right_shoulder"] = calculate_angle(
            keypoints_by_name["right_elbow"],
            keypoints_by_name["right_shoulder"],
            keypoints_by_name["right_hip"]
        )

    # Left knee (hip -> knee -> ankle)
    if confident("left_hip") and confident("left_knee") and confident("left_ankle"):
        angles["left_knee"] = calculate_angle(
            keypoints_by_name["left_hip"],
            keypoints_by_name["left_knee"],
            keypoints_by_name["left_ankle"]
        )

    # Right knee (hip -> knee -> ankle)
    if confident("right_hip") and confident("right_knee") and confident("right_ankle"):
        angles["right_knee"] = calculate_angle(
            keypoints_by_name["right_hip"],
            keypoints_by_name["right_knee"],
            keypoints_by_name["right_ankle"]
        )

    return angles

def compare_skeletons(target: PoseSkeleton, live: Optional[PoseSkeleton]) -> PoseCompareResponse:
    """
    Compare live user skeleton with target pose skeleton.
    If no subject is in frame or landmarks are below confidence threshold,
    returns explicit status with no_pose_detected=True and match_score=None.
    """
    if live is None or not live.keypoints:
        return PoseCompareResponse(
            no_pose_detected=True,
            match_score=None,
            is_matched=False,
            status="NO_POSE_DETECTED",
            primary_feedback="No pose detected — step into frame",
            limb_corrections=[],
            aligned_limbs=[],
            user_skeleton=live
        )

    # Validate landmarks confidence & core presence
    kp_map = {kp.name: kp for kp in live.keypoints}
    is_valid, validation_reason = validate_pose_keypoints(kp_map)
    if not is_valid:
        return PoseCompareResponse(
            no_pose_detected=True,
            match_score=None,
            is_matched=False,
            status="NO_POSE_DETECTED",
            primary_feedback="No pose detected — step into frame",
            limb_corrections=[],
            aligned_limbs=[],
            user_skeleton=live
        )

    target_angles = target.joint_angles or {}
    live_angles = live.joint_angles or {}

    corrections: List[LimbCorrection] = []
    aligned_limbs: List[str] = []
    total_penalty = 0.0
    evaluated_count = 0

    # Angle tolerances
    TOLERANCE_DEG = 18.0

    joint_display_names = {
        "left_elbow": "Left elbow",
        "right_elbow": "Right elbow",
        "left_shoulder": "Left shoulder",
        "right_shoulder": "Right shoulder",
        "left_knee": "Left knee",
        "right_knee": "Right knee"
    }

    for joint_name, target_deg in target_angles.items():
        if joint_name in live_angles:
            evaluated_count += 1
            live_deg = live_angles[joint_name]
            diff = float(live_deg - target_deg)
            abs_diff = abs(diff)

            display_name = joint_display_names.get(joint_name, joint_name.replace("_", " ").title())

            if abs_diff <= TOLERANCE_DEG:
                aligned_limbs.append(joint_name)
                corrections.append(LimbCorrection(
                    limb=joint_name,
                    instruction=f"{display_name}: perfectly aligned",
                    delta_degrees=round(abs_diff, 1),
                    status="ALIGNED"
                ))
            else:
                penalty = min(25.0, abs_diff * 0.8)
                total_penalty += penalty

                if "elbow" in joint_name or "knee" in joint_name:
                    if diff < 0:
                        instruction = f"{display_name}: straighten {int(round(abs_diff))}°"
                        status = "ADJUST_EXTEND"
                    else:
                        instruction = f"{display_name}: bend {int(round(abs_diff))}°"
                        status = "ADJUST_BEND"
                else:
                    if diff < 0:
                        instruction = f"{display_name}: raise {int(round(abs_diff))}°"
                        status = "ADJUST_UP"
                    else:
                        instruction = f"{display_name}: lower {int(round(abs_diff))}°"
                        status = "ADJUST_DOWN"

                corrections.append(LimbCorrection(
                    limb=joint_name,
                    instruction=instruction,
                    delta_degrees=round(abs_diff, 1),
                    status=status
                ))

    # Minimum angle count check
    if evaluated_count < MIN_EVALUATED_ANGLES:
        return PoseCompareResponse(
            no_pose_detected=True,
            match_score=None,
            is_matched=False,
            status="NO_POSE_DETECTED",
            primary_feedback="No pose detected — step into frame",
            limb_corrections=[],
            aligned_limbs=[],
            user_skeleton=live
        )

    raw_score = 100.0 - (total_penalty / max(1, evaluated_count) * 3.0)
    match_score = int(round(max(0.0, min(100.0, raw_score))))

    # Primary feedback is the largest misalignment
    misaligned = [c for c in corrections if c.status != "ALIGNED"]
    if match_score >= 85:
        status = "EXCELLENT"
        primary_feedback = "Flawless pose! Hold steady for photo"
    elif match_score >= 65:
        status = "GOOD"
        primary_feedback = misaligned[0].instruction if misaligned else "Looking good! Fine-tune posture"
    else:
        status = "ADJUSTING"
        primary_feedback = misaligned[0].instruction if misaligned else "Align body with guide skeleton"

    return PoseCompareResponse(
        no_pose_detected=False,
        match_score=int(match_score),
        is_matched=bool(match_score >= 80),
        status=status,
        primary_feedback=primary_feedback,
        limb_corrections=corrections,
        aligned_limbs=aligned_limbs,
        user_skeleton=live
    )
