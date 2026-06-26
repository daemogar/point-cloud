# Point Cloud Scanner — Samsung S26 Ultra

Captures GPS-referenced 3-D point clouds using the phone's ToF depth sensor
(ARCore Raw Depth API) and exports them in formats that open in
**SketchUp Make 2017**, Revit, and AutoCAD Civil 3D.

Designed for scanning a ~2-acre property site before construction.

---

## What it does

| Feature | Detail |
|---------|--------|
| **Depth scanning** | ARCore Raw Depth API — uses Samsung's ToF sensor, falls back to ML depth |
| **GPS georeferencing** | Every depth frame is offset by your live GPS position; no survey markers needed |
| **Coverage tracking** | Live point count + % of 2-acre target covered |
| **Voxel deduplication** | 5 cm cells — walking 2 acres won't exhaust RAM |
| **SketchUp Make 2017** | Terrain mesh OBJ (no plugin) + downsampled XYZ (TIG plugin) |
| **Revit / AutoCAD** | LAS 1.4 binary export |
| **Cloud upload** | Google Drive and OneDrive buttons on the export result card |
| **Photo survey mode** | Geotagged JPEG burst for OpenDroneMap / Metashape photogrammetry |

---

## Requirements

### Hardware
- **Samsung Galaxy S26 Ultra** (or S21 Ultra / S22 Ultra / S23 Ultra / S25 Ultra)
- ToF depth sensor required for full Raw Depth quality; S-series flagships all have it
- GPS must be enabled with good sky view while scanning outdoors

### Software (your computer)
- **Android Studio** Meerkat (2024.3) or later — free at developer.android.com
- **JDK 17** — Android Studio bundles one
- A USB cable to side-load the app (no Play Store listing yet)

### On the phone
- Android 8.0 (API 26) or later
- **ARCore** — installed automatically from Play Store when you first run the app
- Google Drive app and/or Microsoft OneDrive app (optional, for cloud upload)

---

## Build & install

### 1 — Clone and open
```
git clone https://github.com/daemogar/point-cloud.git
```
Open Android Studio → **File → Open** → select the `point-cloud` folder.

### 2 — Sync Gradle
Android Studio will prompt "Gradle files have changed — Sync now". Click **Sync Now**.
First sync downloads ~500 MB of dependencies.

### 3 — Enable developer mode on your phone
Settings → About phone → tap **Build number** 7 times → go back to
Settings → Developer options → enable **USB debugging**.

### 4 — Connect phone and run
Plug in via USB, accept the debug prompt on the phone, then click the green
**Run** triangle in Android Studio. The app installs and launches.

> **No Gradle wrapper jar?**  
> If Android Studio shows a missing `gradle-wrapper.jar` error, open a terminal
> in the project folder and run: `gradle wrapper --gradle-version 8.11.1`  
> This generates the binary jar that's too large for Git.

---

## How to scan your 2-acre property

### Overview
Walk the property in a systematic mow-the-lawn pattern with the phone at
chest height, pointed roughly forward and slightly downward. The app captures
depth frames continuously; GPS offsets each frame so the whole property
stitches together automatically.

### Step-by-step

1. **Open the app** — tap **Start Depth Scan**
2. **Grant permissions** — Camera and Location (both required)
3. Wait for the camera feed to appear in the viewfinder (1–3 seconds)
4. Walk to one corner of the property — the first GPS fix becomes your origin
5. Tap the **red record button** to start capturing
6. **Walk rows spaced ~3 m apart** across the entire property
   - Move at roughly **walking pace** (0.5–1 m/s) — faster = fewer depth frames
   - Keep the phone **steady** at chest height, tilted ~15° down
   - Watch the **Coverage %** bar at the top grow
7. At corners, turn slowly (the phone needs ~1 second to re-track)
8. When coverage reaches ~80–100%, tap **Stop** (square button)
9. Tap the **green upload/export button** → choose your format

### Time estimate for 2 acres
| Walk spacing | Time | Coverage |
|---|---|---|
| 3 m rows | ~35 min | full |
| 5 m rows | ~20 min | good for terrain only |

### Tips
- Scan on an **overcast day** — harsh shadows reduce depth confidence
- If the GPS indicator shows no fix, wait before starting
- Wooded areas: scan from multiple angles, the trees thin out the depth data

---

## Exporting for SketchUp Make 2017

### Method 1 — Terrain Mesh OBJ (recommended, zero plugins)

1. After scanning, tap the export button
2. Under **SketchUp Make 2017**, tap **Terrain Mesh (.obj)**
3. Choose resolution:
   - **High (25 cm)** — best detail (~130 K triangles for 2 acres)
   - **Medium (50 cm)** — good balance (~65 K triangles) ← default
   - **Low (1 m)** — lightweight (~16 K triangles)
4. Tap **Drive** or **OneDrive** to upload, then download to your PC
5. In SketchUp Make 2017:
   - **File → Import**
   - Change "Files of type" to **OBJ File**
   - Select your `.obj` file → **Open**
   - In the import options: tick **Merge coplanar faces** (optional, tidier result)
6. The terrain appears as real geometry you can draw on, snap to, and measure

### Method 2 — Point cloud XYZ (TIG PointCloudMaker plugin, free)

Use this if you want individual point markers rather than a mesh surface.

1. Install the plugin once:
   - In SketchUp Make 2017: **Window → Extension Manager → Install Extension**
   - Download **PointCloudMaker** by TIG from the Extension Warehouse (search "PointCloudMaker") — it is free
2. Export from the app: tap **Point Cloud (.xyz, ≤50K pts)**
3. Upload and download to your PC
4. In SketchUp: **Extensions → TIG → Point Cloud Maker → Import CSV/XYZ**
5. Each point becomes a guide cross — useful for reference but not solid geometry

> **File size note:** The downsampled XYZ file is capped at 50,000 points.
> SketchUp Make 2017 becomes very slow above that number.

---

## Uploading to Google Drive / OneDrive

After any export completes, the result card shows three buttons:

| Button | What it does |
|--------|-------------|
| **Drive** | Opens Google Drive app (if installed) with the file ready to upload |
| **OneDrive** | Opens Microsoft OneDrive app (if installed) |
| **Share icon** | Android system share sheet — works with Dropbox, email, AirDrop (via third-party), etc. |

If neither cloud app is installed, all three buttons fall back to the share sheet.

### Download to your PC from the cloud
- **Google Drive**: drive.google.com → right-click file → Download
- **OneDrive**: onedrive.live.com → select file → Download

---

## What is working now

- [x] ARCore session setup with ToF / ML-depth detection
- [x] Live camera feed in the viewfinder (OpenGL ES OES texture)
- [x] GPS tracking and ENU coordinate georeferencing
- [x] Point cloud accumulation (5 cm voxel deduplication)
- [x] Coverage tracker (% of 2 acres)
- [x] Export: LAS 1.4, PLY, XYZ
- [x] Export: OBJ terrain mesh (SketchUp Make 2017, no plugin)
- [x] Export: downsampled XYZ (TIG PointCloudMaker plugin)
- [x] Cloud upload buttons (Google Drive, OneDrive, share sheet)
- [x] Photo survey capture class (geotagged JPEGs for photogrammetry)

---

## What is NOT done yet — do these before using on site

### Must-do before first scan

**1. Add a Gradle wrapper JAR**
The binary `gradle-wrapper.jar` is not in git.
Open a terminal in the project root and run:
```
gradle wrapper --gradle-version 8.11.1
```
Then sync in Android Studio. This is a one-time step.

**2. Test on a physical device**
ARCore Raw Depth does not work in the Android emulator.
You need to deploy to the actual Samsung phone at least once before going to the field.

**3. Verify ARCore is installed and up to date**
The app handles the "ARCore not supported" error but does not yet prompt the user
to update ARCore from the Play Store when it's outdated.
Manually confirm ARCore is current: Play Store → search "Google Play Services for AR" → update.

### Nice-to-have for the field

**4. Add scan session persistence**
If you close the app before exporting, the current scan is **lost** — it lives
only in RAM.
To fix: serialize `PointCloudAccumulator`'s voxel map to a binary file in
`onStop()` and reload it in `onCreate()`.

**5. Wire up Photo Survey mode UI**
The `PhotoSurveyCapture` class is written but there is no screen for it yet.
Adding it gives you the geotagged-photo workflow for full-property photogrammetry
(better terrain detail than depth-only for large flat areas).

**6. Add ARCore update/install prompt**
In `ScanActivity.initScanning()`, add:
```kotlin
val installStatus = ArCoreApk.getInstance().requestInstall(this, true)
if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) return
```
This handles the case where ARCore needs an update before the session can start.

**7. Replace the placeholder app icon**
The icon is a simple vector placeholder.
In Android Studio: right-click `res` → **New → Image Asset** → configure a proper icon.

---

## File locations on the phone

Exported files are saved to:
```
/sdcard/Android/data/edu.southern.pointcloud/files/point_clouds/
```
You can browse them with any file manager, or connect the phone via USB and
copy them directly to your PC (no cloud required).

---

## Format guide

| Format | Open with | Best for |
|--------|-----------|----------|
| `.obj` | SketchUp Make 2017 File → Import | Site terrain modeling, walls, grades |
| `.xyz` (50K) | TIG PointCloudMaker plugin | Visual reference points in SketchUp |
| `.las` | Revit (Insert → Point Cloud), AutoCAD Civil 3D | Professional survey workflows |
| `.ply` | MeshLab, CloudCompare, newer SketchUp | Post-processing, mesh generation |
| `.xyz` (full) | CloudCompare, MeshLab | Full-density analysis |

---

## Photogrammetry workflow (full terrain, best quality)

For the most detailed terrain model of all 2 acres, combine the depth scan
with the photo survey mode once it has a UI:

1. Walk the property taking a geotagged photo every 2 seconds (auto-burst)
2. Copy the JPEG folder to your PC
3. Process in one of:
   - **OpenDroneMap** (free, open-source) — odm.readthedocs.io
   - **Agisoft Metashape** (paid, ~$179 edu) — very accurate
   - **RealityCapture** (pay-per-use) — fastest
4. Export the resulting dense point cloud as LAS or PLY
5. Import into Revit or SketchUp

---

## Project structure

```
app/src/main/java/edu/southern/pointcloud/
├── scanning/
│   ├── ARCoreScanner.kt          — session lifecycle, depth frame capture
│   ├── CameraPreviewRenderer.kt  — OpenGL ES camera feed (OES texture)
│   ├── DepthFrameProcessor.kt    — depth image → 3-D points in ENU space
│   └── PointCloudAccumulator.kt  — voxel dedup, coverage tracking
├── location/
│   └── LocationTracker.kt        — 500 ms GPS updates via FusedLocationProvider
├── export/
│   ├── LasExporter.kt            — LAS 1.4 binary (Revit / AutoCAD)
│   ├── PlyExporter.kt            — binary PLY with RGB colour
│   ├── XyzExporter.kt            — ASCII XYZ full density
│   ├── TerrainMeshGenerator.kt   — point cloud → DEM grid → triangle mesh
│   ├── ObjExporter.kt            — mesh → Wavefront OBJ (SketchUp Make 2017)
│   ├── SketchUpXyzExporter.kt    — spatially downsampled XYZ (≤50 K pts)
│   ├── CloudExportManager.kt     — Google Drive / OneDrive share intents
│   └── ExportManager.kt          — orchestrates all export formats
├── survey/
│   └── PhotoSurveyCapture.kt     — CameraX burst + GPS EXIF tagging
├── ui/
│   └── scan/
│       ├── ScanActivity.kt       — GL surface + Compose HUD overlay
│       └── ScanViewModel.kt      — state machine for scan + export
└── data/models/
    ├── Point3D.kt                — x,y,z,r,g,b,intensity
    ├── GeoPoint.kt               — WGS-84 + ENU offset math
    └── ScanSession.kt            — session metadata
```
