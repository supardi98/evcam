# 📷 EV Cam — Evolution Camera

A professional-grade, open-source Android camera app built with **Jetpack Compose** and **Camera2 API**.

> Built by [@supardi98](https://github.com/supardi98) · AI-assisted by **Antigravity** (Google DeepMind)

---

## ✨ Features

### 📸 Photo Mode
- **Manual Controls (Pro Mode):** ISO, Shutter Speed, White Balance (Auto/Daylight/Cloudy/Custom Kelvin), EV Compensation
- **Focus Peaking** — highlights in-focus edges in green for precise manual focusing
- **Live Histogram** — real-time RGB/Luminance exposure graph
- **Burst Mode** — long-press shutter to capture multiple shots continuously
- **Timer + Burst Count** — countdown timer (3s/5s/10s) with burst count (1x/3x/5x/10x)
- **Watermark** — customizable text overlay at 4 corners (auto-repositions in landscape)
- **Geotagging** — embed GPS coordinates & address in saved photos
- **Auto-Rotation** — saved photos/videos rotate to match device orientation
- **Resolution Control** — Ultra, Max, High, Medium, Low quality selection

### 🎥 Video Mode
- **Resolution Control:** SD, HD, FHD, 2K (QHD), 4K (UHD) selection
- **High-Speed Recording:** Support for 60fps and 120fps high-speed video recording
- **Horizon Lock:** Locks video orientation during recording so you can rotate the phone without ruining the video
- Clean viewfinder (no peaking/histogram overlay)

### 🎬 Stop Motion Mode
- **Onion Skinning:** Transparent overlay of the previous frame with adjustable opacity
- **Auto-Capture Timer:** Set interval (1s, 2s, 3s, 5s, Custom) for hands-free capturing
- **Video Export:** Export captured frames to an MP4 file with adjustable frame rates (12fps, 15fps, 24fps, 30fps)
- **Dedicated Resolution & Ratio:** Independent controls for aspect ratio (1:1, 4:3, 16:9) and video output resolution
- **Auto Save:** Progress and settings are persisted seamlessly across app sessions

### 🌐 IP Webcam (Web Server)
- **Live Stream:** Turn your phone into a wireless webcam over local Wi-Fi
- **Web Interface:** Access the camera feed directly from any web browser
- **Dynamic IP Display:** Shows the local IP and port right on the screen

### 🎨 UI & UX
- Dark, premium UI with Jetpack Compose
- Live virtual horizon / level indicator
- Grid overlays (Rule of Thirds, Square, Golden Ratio)
- Zoom controls (1x / 2x / 5x + pinch-to-zoom)
- Torch toggle, aspect ratio switcher (4:3, 16:9, 1:1)
- Shutter flash animation + sound
- Keep screen on / max brightness options
- All settings persisted via SharedPreferences

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Camera | Camera2 API, ImageReader, MediaCodec, MediaMuxer |
| Networking | Ktor Server (IP Webcam) |
| Min SDK | Android 8.0 (API 26) |
| Build | Gradle (Kotlin DSL) |

---

## 🚀 Getting Started

```bash
git clone https://github.com/supardi98/evcam.git
cd evcam
./gradlew installDebug
```

### Permissions Required
- `CAMERA`
- `RECORD_AUDIO`
- `ACCESS_FINE_LOCATION` (for geotagging)
- `WRITE_EXTERNAL_STORAGE` (Android ≤ 9)

---

## 👥 Contributors

| Contributor | Role |
|---|---|
| [@supardi98](https://github.com/supardi98) | Creator & Developer |
| [Antigravity](https://deepmind.google) | AI Pair Programmer (Google DeepMind) |

---

## 📄 License

MIT License — feel free to fork, modify, and build upon this project.
