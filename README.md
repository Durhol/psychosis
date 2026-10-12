# 👁️ Psychosis

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://minecraft.net/)
[![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Environment](https://img.shields.io/badge/Environment-Client--Only-orange.svg)]()
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Psychosis** is an analogue psycho-horror ambient overlay mod for Minecraft 1.21.11 (Fabric). Inspired by creepy internet horror, surveillance footage, and intrusive-thought aesthetics, it overlays shaky red tracking frames, angled pointer arrows, and trembling **Impact-font** creepy annotations over structures, doors, windows, entities, and mysterious points in the sky.

---

## 📸 Features

* **🔲 Adaptable 2D Bounding Frames:** Clean camera-projected rectangular frames that dynamically wrap around targeted objects (doors, mobs, players, houses, or sky anomalies).
* **↗️ Angled Pointer Arrows:** Stylized pointer arrows that aim down-diagonally from above the text toward the target frame.
* **🩸 Trembling Impact-Style Font:** Sharp, high-contrast bold typography using the iconic **Impact** font with a dark blood-red outline for perfect readability on all backgrounds (sun, sky, snow, night).
* **⚡ Live Shaking Effect (Jitter):** Active per-frame micro-vibrations for text, frames, and arrows, creating an intense analogue horror / VHS paranoia atmosphere.
* **🛡️ Zero Wallhacks (Strict LOS):** Every potential target passes real-time camera raycast and line-of-sight checks. Nothing is ever shown through walls, terrain, or obstacles.
* **⚙️ In-Game Configuration via Mod Menu:** Full graphical settings screen supporting Mod Menu:
  * Adjust max active markers, render distance, and text scale.
  * Toggle and adjust shake / trembling intensity.
  * Toggle tracking for players, mobs, and sky.
  * Choose marker colors (*Blood Red, Crimson, Creepy Violet, Cold Cyan, Toxic Green, Warning Yellow, Ghost White*).

---

## 🛠️ Installation & Requirements

1. **Minecraft:** `1.21.11`
2. **Fabric Loader:** `>= 0.19.3`
3. **Fabric API:** `0.141.6+1.21.11` (or latest compatible for 1.21.11)
4. *(Optional)* **Mod Menu:** for in-game graphical settings.

Place the downloaded `psychosis-1.0.0.jar` into your Minecraft profile's `mods` folder.

---

## ⚙️ Configuration

The mod can be configured:
1. In-game via **Mod Menu** (click the gear icon next to *Psychosis*).
2. Manually via `config/psychosis.json`:

```json
{
  "maxActiveMarkers": 6,
  "renderDistance": 32.0,
  "enablePlayerTargeting": true,
  "enableMobTargeting": true,
  "enableSkyMarkers": true,
  "phraseRotationInterval": 400,
  "textSize": 1.35,
  "enableJitter": true,
  "jitterIntensity": 1.5,
  "markerColor": 16711680,
  "maxAlpha": 0.85
}
```

---

## 🔨 Building from Source

```bash
git clone https://github.com/<your-username>/psychosis.git
cd psychosis
./gradlew build   # or gradlew.bat build on Windows
```
The compiled mod JAR will be located at `build/libs/psychosis-1.0.0.jar`.

---

## 👤 Author & Contacts

* **Author:** Durhol
* **Telegram:** [@tyxap](https://t.me/tyxap) or [@Durhol](https://t.me/Durhol)

## 📄 License
This project is licensed under the [MIT License](LICENSE).
