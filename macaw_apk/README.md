# Macaw Rescue AI v2 — offline SmolVLM2

This is an Android ARM64 APK integrating SceneSense v1.3's AGPL JNI runtime into the Macaw Fieldbook offline UI. `INTERNET` is used **only** for explicit first-use model download from Hugging Face (model ~437MB; projector ~109MB, both SHA-256 verified). Afterward image/video inference runs on-device using `libvisionai.so` llama.cpp native backend. No private user media leaves the device.

How to use: install APK, open `AI Analysis`, tap `Download offline model` with sufficient storage and Wi-Fi, select a photo or short video, then `Analyse on device`. AI output remains pending until explicitly confirmed. Video samples three frames (early/middle/late), so brief warning gestures can be missed. The offline rule-based recommendations are conservative and not medical diagnostics. Keep records backed up.

Build uses GitHub Actions to fetch a pinned official upstream release and extract its ARM64 native libraries. See THIRD_PARTY.md for licence and provenance. APK is debug-signed. Device end-to-end inference requires testing on an ARM64 phone after model download. No cloud service or home server required.