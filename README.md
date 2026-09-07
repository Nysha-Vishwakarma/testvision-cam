# VisionCam

A next-generation Android camera application combining real-time on-device computer vision with AI-powered backend intelligence, offering face filters, real-time pose replication guidance, and an AI Photographer mode that automatically frames, adjusts, and captures the perfect shot.

**Developed by students of R. A. Podar College of Commerce & Economics**
Project: *AI Camera*, SY BSc in Data Science and Analytics

---

## Overview

VisionCam reimagines the standard camera app by layering three progressively intelligent features on top of a polished, glassmorphism-styled base camera:

1. **Live Face Filters**: Snapchat-style AR filters that track facial landmarks in real time
2. **Pose Guide**: Upload or select a reference pose, get a live skeleton overlay to replicate it accurately
3. **AI Photographer**: Point the camera at a subject or object and let the app guide framing, adjust zoom/exposure, and auto-capture when the shot is ready

The app is built with a hybrid architecture: anything that must feel instantaneous (filters, live pose tracking, grid/focus/zoom) runs entirely on-device, while anything that can tolerate a short delay (pose extraction, composition scoring, AI-guided photography) is powered by Python.

---

## Python's Role in This Project

> Real-time features run on-device. Delay-tolerant features run on Python.

Early in development, this project deliberately moved away from routing every camera frame through a server. Continuous, live feedback (face filters, pose alignment, grid/focus) requires latency a network round-trip cannot deliver. Python is used where its strengths matter most:

| Feature | Handled By Python |
|---|---|
| Pose image to skeleton extraction | Yes, using MediaPipe (Python) |
| Pose library storage and serving | Yes, FastAPI backend |
| AI Photographer scene analysis and guidance | Yes, vision model analysis in Python |
| Lighting and exposure analysis | Yes, OpenCV |
| Movement verification (confirming guidance was followed) | Yes, frame comparison and session logic in Python |
| Grid lines, tap-to-focus, live filters, live pose tracking | No, these run on-device for real-time speed |

---

## Features

### Feature 1: Base Camera
- Full-screen live viewfinder with front/rear camera toggle
- Tap-to-focus with animated golden reticle
- Pinch-to-zoom with floating zoom indicator
- Vertical glassmorphic exposure slider
- Toggleable rule-of-thirds grid overlay
- Tactile shutter capture with haptic feedback and screen-flash effect
- Three-state flash control (Off, On, Auto)
- Frosted-glass UI across all controls
- Dedicated permission request screen

### Feature 2: Live Face Filters 
- Real-time facial landmark tracking
- Filters dynamically scaled and anchored to actual face size and head rotation, so filters no longer appear fixed-size or mismatched
- Swipeable filter carousel
- Capture includes the rendered filter, not just the raw feed
- Shared 3-way Mode Switcher (Filter, Pose Guide, AI Photographer) introduced here, with smooth swipe/tap transitions and haptic feedback

### Feature 3: Pose Guide 
- Upload a reference photo or choose from a pre-made pose library
- Python backend extracts a normalized skeleton from the reference image
- Live on-device pose detection compares the user's position against the target in real time
- Skeleton overlay renders as an outline, not the source photo, in the pose picker
- Per-limb color feedback, segments turn green as they align with the target pose
- Graceful "No pose detected" state when no valid subject is in frame, so there are no false-positive match scores

### Feature 4: AI Photographer 
- Works on both people and general objects, not limited to faces or bodies
- Uses a vision-capable AI model in Python to analyze scene composition, subject position, and lighting
- Provides plain guidance: move up or down, orbit left or right, step back, and so on
- Automatically adjusts zoom and exposure via existing camera controls
- Closed-loop movement verification, where the Python backend compares successive snapshots to confirm suggested movements were actually made, rather than issuing disconnected instructions
- Falls back to a manual-override state if guidance repeatedly isn't followed
- Auto-captures when framing and lighting cross a readiness threshold, with manual shutter override always available

### App-wide
- Consistent glassmorphism design system across every screen and mode
- Info modal (accessible via a top-corner icon) with project and credits information

---

## Backend Structure (Python)

```
/backend
  /main.py
  /routes/
    pose.py           # Pose extraction and library endpoints
    analyze.py         # AI Photographer scene analysis and verification
  /verification.py     # Movement verification and session logic
  /models/              # Pose library data, session store
```

---

## Getting Started (Backend)

```bash
cd backend
pip install -r requirements.txt
uvicorn main:app --reload
```
Set the following environment variable before running:
```
GROQ_API_KEY=your_groq_api_key_here
```

---

## Roadmap / Future Improvements
- Custom-trained composition scoring model (replacing heuristic + AI-model hybrid in Phase 4) once sufficient usage data is collected
- Expanded, community-uploadable pose library
- iOS support
- Optional cloud sync for saved poses and captured sessions

---

## Student Team

| Student Name | Roll Number |
|---|---:|
| **Nysha Vishwakarma** | 52 |
| **Isha Salgoankar** | 45 |
| **Urvi Ayachit** | 03 |
| **Shreeya Dubey** | 13 |

---

## Acknowledgements

We sincerely thank our **college faculty** for their valuable support, guidance, and encouragement throughout the development of this project. We are grateful for their support in validating and helping us bring our idea to life.

**Thank You!**
