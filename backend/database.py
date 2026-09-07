import sqlite3
import json
from typing import List, Optional
from models import Keypoint, PoseSkeleton, PoseLibraryItem
from pose_processor import extract_joint_angles

DB_PATH = "pose_library.db"

SAMPLE_POSES = [
    {
        "id": "casual_lean",
        "name": "Casual Lean",
        "category": "Casual",
        "description": "Relaxed street-style lean with one hand in pocket and slightly turned torso.",
        "difficulty": "Easy",
        "thumbnail_url": "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?w=300",
        "keypoints": [
            {"name": "nose", "x": 0.48, "y": 0.16},
            {"name": "left_eye", "x": 0.47, "y": 0.14},
            {"name": "right_eye", "x": 0.50, "y": 0.14},
            {"name": "left_ear", "x": 0.44, "y": 0.15},
            {"name": "right_ear", "x": 0.53, "y": 0.15},
            {"name": "left_shoulder", "x": 0.40, "y": 0.26},
            {"name": "right_shoulder", "x": 0.58, "y": 0.28},
            {"name": "left_elbow", "x": 0.35, "y": 0.38},
            {"name": "right_elbow", "x": 0.62, "y": 0.40},
            {"name": "left_wrist", "x": 0.41, "y": 0.49},
            {"name": "right_wrist", "x": 0.58, "y": 0.51},
            {"name": "left_hip", "x": 0.43, "y": 0.52},
            {"name": "right_hip", "x": 0.55, "y": 0.53},
            {"name": "left_knee", "x": 0.44, "y": 0.70},
            {"name": "right_knee", "x": 0.58, "y": 0.73},
            {"name": "left_ankle", "x": 0.45, "y": 0.90},
            {"name": "right_ankle", "x": 0.60, "y": 0.91},
        ]
    },
    {
        "id": "golden_hour_portrait",
        "name": "Golden Hour Portrait",
        "category": "Portraits",
        "description": "Elegant chest-up profile with gentle hand-to-chin touch and angled gaze.",
        "difficulty": "Easy",
        "thumbnail_url": "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=300",
        "keypoints": [
            {"name": "nose", "x": 0.52, "y": 0.24},
            {"name": "left_eye", "x": 0.49, "y": 0.22},
            {"name": "right_eye", "x": 0.54, "y": 0.22},
            {"name": "left_ear", "x": 0.45, "y": 0.24},
            {"name": "right_ear", "x": 0.57, "y": 0.24},
            {"name": "left_shoulder", "x": 0.38, "y": 0.38},
            {"name": "right_shoulder", "x": 0.62, "y": 0.39},
            {"name": "left_elbow", "x": 0.42, "y": 0.52},
            {"name": "right_elbow", "x": 0.64, "y": 0.55},
            {"name": "left_wrist", "x": 0.51, "y": 0.32},
            {"name": "right_wrist", "x": 0.60, "y": 0.68},
            {"name": "left_hip", "x": 0.42, "y": 0.76},
            {"name": "right_hip", "x": 0.58, "y": 0.77},
            {"name": "left_knee", "x": 0.42, "y": 0.92},
            {"name": "right_knee", "x": 0.58, "y": 0.92},
            {"name": "left_ankle", "x": 0.42, "y": 0.98},
            {"name": "right_ankle", "x": 0.58, "y": 0.98},
        ]
    },
    {
        "id": "runway_stride",
        "name": "Runway Stride",
        "category": "Streetwear",
        "description": "Dynamic walking motion with forward stride, swinging arms, and elongating posture.",
        "difficulty": "Medium",
        "thumbnail_url": "https://images.unsplash.com/photo-1509631179647-0177331693ae?w=300",
        "keypoints": [
            {"name": "nose", "x": 0.50, "y": 0.14},
            {"name": "left_eye", "x": 0.48, "y": 0.12},
            {"name": "right_eye", "x": 0.52, "y": 0.12},
            {"name": "left_ear", "x": 0.45, "y": 0.13},
            {"name": "right_ear", "x": 0.55, "y": 0.13},
            {"name": "left_shoulder", "x": 0.42, "y": 0.24},
            {"name": "right_shoulder", "x": 0.58, "y": 0.24},
            {"name": "left_elbow", "x": 0.36, "y": 0.35},
            {"name": "right_elbow", "x": 0.64, "y": 0.34},
            {"name": "left_wrist", "x": 0.32, "y": 0.46},
            {"name": "right_wrist", "x": 0.67, "y": 0.44},
            {"name": "left_hip", "x": 0.46, "y": 0.50},
            {"name": "right_hip", "x": 0.54, "y": 0.50},
            {"name": "left_knee", "x": 0.40, "y": 0.68},
            {"name": "right_knee", "x": 0.60, "y": 0.70},
            {"name": "left_ankle", "x": 0.35, "y": 0.88},
            {"name": "right_ankle", "x": 0.65, "y": 0.89},
        ]
    },
    {
        "id": "confident_cross_arm",
        "name": "Confident Cross-Arm",
        "category": "Editorial",
        "description": "Bold editorial look with folded arms, squared shoulders, and poised gaze.",
        "difficulty": "Easy",
        "thumbnail_url": "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=300",
        "keypoints": [
            {"name": "nose", "x": 0.50, "y": 0.15},
            {"name": "left_eye", "x": 0.48, "y": 0.13},
            {"name": "right_eye", "x": 0.52, "y": 0.13},
            {"name": "left_ear", "x": 0.45, "y": 0.14},
            {"name": "right_ear", "x": 0.55, "y": 0.14},
            {"name": "left_shoulder", "x": 0.37, "y": 0.25},
            {"name": "right_shoulder", "x": 0.63, "y": 0.25},
            {"name": "left_elbow", "x": 0.35, "y": 0.40},
            {"name": "right_elbow", "x": 0.65, "y": 0.40},
            {"name": "left_wrist", "x": 0.56, "y": 0.42},
            {"name": "right_wrist", "x": 0.44, "y": 0.42},
            {"name": "left_hip", "x": 0.43, "y": 0.54},
            {"name": "right_hip", "x": 0.57, "y": 0.54},
            {"name": "left_knee", "x": 0.44, "y": 0.72},
            {"name": "right_knee", "x": 0.56, "y": 0.72},
            {"name": "left_ankle", "x": 0.44, "y": 0.90},
            {"name": "right_ankle", "x": 0.56, "y": 0.90},
        ]
    },
    {
        "id": "athletic_stretch",
        "name": "Athletic Stretch",
        "category": "Fitness",
        "description": "Dynamic side stretch with raised arm over head and bent lateral stance.",
        "difficulty": "Pro",
        "thumbnail_url": "https://images.unsplash.com/photo-1518611012118-696072aa579a?w=300",
        "keypoints": [
            {"name": "nose", "x": 0.48, "y": 0.17},
            {"name": "left_eye", "x": 0.46, "y": 0.15},
            {"name": "right_eye", "x": 0.50, "y": 0.15},
            {"name": "left_ear", "x": 0.43, "y": 0.16},
            {"name": "right_ear", "x": 0.53, "y": 0.16},
            {"name": "left_shoulder", "x": 0.39, "y": 0.27},
            {"name": "right_shoulder", "x": 0.59, "y": 0.26},
            {"name": "left_elbow", "x": 0.33, "y": 0.37},
            {"name": "right_elbow", "x": 0.64, "y": 0.14},
            {"name": "left_wrist", "x": 0.40, "y": 0.48},
            {"name": "right_wrist", "x": 0.53, "y": 0.06},
            {"name": "left_hip", "x": 0.42, "y": 0.53},
            {"name": "right_hip", "x": 0.56, "y": 0.53},
            {"name": "left_knee", "x": 0.38, "y": 0.71},
            {"name": "right_knee", "x": 0.60, "y": 0.73},
            {"name": "left_ankle", "x": 0.36, "y": 0.91},
            {"name": "right_ankle", "x": 0.63, "y": 0.92},
        ]
    }
]

def init_db(db_path: str = DB_PATH):
    """Initialize SQLite database and populate sample poses."""
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS poses (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            description TEXT NOT NULL,
            difficulty TEXT NOT NULL,
            thumbnail_url TEXT NOT NULL,
            skeleton_json TEXT NOT NULL
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS ai_photo_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            score INTEGER,
            framing_feedback TEXT,
            lighting_feedback TEXT,
            suggested_zoom_delta REAL,
            suggested_move_direction TEXT,
            ready_to_capture INTEGER,
            previous_instruction_followed INTEGER DEFAULT 1,
            correction_note TEXT,
            guidance_stuck INTEGER DEFAULT 0
        )
    """)

    # Safe migration for existing SQLite databases
    try:
        cursor.execute("ALTER TABLE ai_photo_sessions ADD COLUMN previous_instruction_followed INTEGER DEFAULT 1")
    except Exception:
        pass
    try:
        cursor.execute("ALTER TABLE ai_photo_sessions ADD COLUMN correction_note TEXT")
    except Exception:
        pass
    try:
        cursor.execute("ALTER TABLE ai_photo_sessions ADD COLUMN guidance_stuck INTEGER DEFAULT 0")
    except Exception:
        pass

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS smart_grid_poses (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            face_detected INTEGER,
            pitch REAL,
            yaw REAL,
            roll REAL,
            face_center_x REAL,
            face_center_y REAL,
            is_level INTEGER,
            near_intersection INTEGER,
            composition_note TEXT
        )
    """)

    for p in SAMPLE_POSES:
        keypoints = [
            Keypoint(
                name=k["name"],
                x=float(k["x"]),
                y=float(k["y"]),
                z=0.0,
                visibility=1.0
            ) for k in p["keypoints"]
        ]
        kp_dict = {k.name: k for k in keypoints}
        angles = extract_joint_angles(kp_dict)

        skeleton = PoseSkeleton(
            id=p["id"],
            name=p["name"],
            keypoints=keypoints,
            joint_angles=angles
        )

        cursor.execute("""
            INSERT OR REPLACE INTO poses (id, name, category, description, difficulty, thumbnail_url, skeleton_json)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            p["id"],
            p["name"],
            p["category"],
            p["description"],
            p["difficulty"],
            p["thumbnail_url"],
            json.dumps(skeleton.model_dump())
        ))

    conn.commit()
    conn.close()

def get_all_poses(db_path: str = DB_PATH) -> List[PoseLibraryItem]:
    """Retrieve all library poses with pre-computed skeletons."""
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT id, name, category, description, difficulty, thumbnail_url, skeleton_json FROM poses")
    rows = cursor.fetchall()
    conn.close()

    items = []
    for r in rows:
        skeleton_dict = json.loads(r[6])
        kps = [Keypoint(**kp) if isinstance(kp, dict) else kp for kp in skeleton_dict.get("keypoints", [])]
        skeleton = PoseSkeleton(
            id=str(skeleton_dict.get("id", "")),
            name=str(skeleton_dict.get("name", "")),
            keypoints=kps,
            joint_angles=skeleton_dict.get("joint_angles", {})
        )
        items.append(PoseLibraryItem(
            id=str(r[0]),
            name=str(r[1]),
            category=str(r[2]),
            description=str(r[3]),
            difficulty=str(r[4]),
            thumbnail_url=str(r[5]),
            skeleton=skeleton
        ))
    return items

def get_pose_by_id(pose_id: str, db_path: str = DB_PATH) -> Optional[PoseLibraryItem]:
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT id, name, category, description, difficulty, thumbnail_url, skeleton_json FROM poses WHERE id = ?", (pose_id,))
    row = cursor.fetchone()
    conn.close()
    if not row:
        return None
    skeleton_dict = json.loads(row[6])
    kps = [Keypoint(**kp) if isinstance(kp, dict) else kp for kp in skeleton_dict.get("keypoints", [])]
    skeleton = PoseSkeleton(
        id=str(skeleton_dict.get("id", "")),
        name=str(skeleton_dict.get("name", "")),
        keypoints=kps,
        joint_angles=skeleton_dict.get("joint_angles", {})
    )
    return PoseLibraryItem(
        id=str(row[0]),
        name=str(row[1]),
        category=str(row[2]),
        description=str(row[3]),
        difficulty=str(row[4]),
        thumbnail_url=str(row[5]),
        skeleton=skeleton
    )

def log_ai_photo_session(
    session_id: str,
    score: int,
    framing_feedback: str,
    lighting_feedback: str,
    suggested_zoom_delta: float,
    suggested_move_direction: str,
    ready_to_capture: bool,
    previous_instruction_followed: bool = True,
    correction_note: Optional[str] = None,
    guidance_stuck: bool = False,
    db_path: str = DB_PATH
):
    """Logs composition session record for potential offline training telemetry."""
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        cursor.execute("""
            INSERT INTO ai_photo_sessions
            (session_id, score, framing_feedback, lighting_feedback, suggested_zoom_delta, suggested_move_direction, ready_to_capture, previous_instruction_followed, correction_note, guidance_stuck)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            str(session_id),
            int(score),
            str(framing_feedback),
            str(lighting_feedback),
            float(suggested_zoom_delta),
            str(suggested_move_direction),
            1 if ready_to_capture else 0,
            1 if previous_instruction_followed else 0,
            str(correction_note) if correction_note else None,
            1 if guidance_stuck else 0
        ))
        conn.commit()
        conn.close()
    except Exception as e:
        pass

def log_smart_grid_pose(
    session_id: str,
    face_detected: bool,
    pitch: float,
    yaw: float,
    roll: float,
    face_center: List[float],
    is_level: bool,
    near_intersection: bool,
    composition_note: str,
    db_path: str = DB_PATH
):
    """Logs periodic smart grid pose readings to database for future analysis/tuning."""
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()
        fc_x = float(face_center[0]) if face_center and len(face_center) > 0 else 0.5
        fc_y = float(face_center[1]) if face_center and len(face_center) > 1 else 0.5
        cursor.execute("""
            INSERT INTO smart_grid_poses
            (session_id, face_detected, pitch, yaw, roll, face_center_x, face_center_y, is_level, near_intersection, composition_note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            str(session_id),
            1 if face_detected else 0,
            float(pitch),
            float(yaw),
            float(roll),
            fc_x,
            fc_y,
            1 if is_level else 0,
            1 if near_intersection else 0,
            str(composition_note)
        ))
        conn.commit()
        conn.close()
    except Exception as e:
        pass


