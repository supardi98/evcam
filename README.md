# 📷 EV Cam — Evolution Camera

A professional-grade, open-source Android camera app built with **Jetpack Compose** and **CameraX**.

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

### 🎥 Video Mode
- HD / FHD / 4K quality selection
- Clean viewfinder (no peaking/histogram overlay)

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
| Camera | CameraX (Preview, ImageCapture, VideoCapture, ImageAnalysis) |
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
