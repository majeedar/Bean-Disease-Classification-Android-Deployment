# Building BeanDoc with Claude Code — phase by phase

Copy each prompt into Claude Code (VS Code) **one phase at a time**. After each phase:

1. Build and run the app on your phone or emulator
2. Check the "You verify" list
3. Commit: `git add -A && git commit -m "Phase N: ..."`

Small phases make mistakes easy to spot and undo. If something breaks, paste the full error into Claude Code and ask it to fix it before moving on.

---

## Before you start

- Install Android Studio (latest stable) and open it once, so it installs the Android SDK.
- Create an emulator in Android Studio (Device Manager), or connect your phone with USB debugging on.
- Make sure `model/bean_model.tflite` and `model/labels.txt` exist in this repo.
- Tell Claude Code where the SDK is if it asks (Windows default: `C:\Users\<you>\AppData\Local\Android\Sdk`).

---

## Phase 1 — Project setup

```
Read AGENTS.md. Create the Android project in the app/ folder of this repo, following
sections 3 and 4: Kotlin, Jetpack Compose with Material 3, Gradle Kotlin DSL with a
version catalog, min SDK 24, latest stable target/compile SDK, Gradle wrapper included.
Package name: com.majeedar.beandoc. App name: BeanDoc.

Move model/bean_model.tflite and model/labels.txt into app/src/main/assets/ (git mv),
add noCompress for tflite, and add the LiteRT, CameraX, exifinterface and lifecycle/
viewmodel dependencies we will need. For now, MainActivity shows a simple screen with
the text "BeanDoc".

Add an Android .gitignore. Make sure ./gradlew assembleDebug succeeds.
```

**You verify:** the app installs and shows "BeanDoc"; `app/src/main/assets/` contains both model files.

---

## Phase 2 — Classifier core + unit tests

```
Implement ml/Prediction.kt, ml/ImagePreprocessor.kt and ml/BeanClassifier.kt exactly per
AGENTS.md section 2 (input contract, preprocessing pipeline) and section 7 (shape
validation at load time, pure preprocessing function, CPU fallback, close()).
BeanClassifier reads labels from labels.txt and does not hard-code the class order.

Add the JVM unit tests from section 8.1 (confidence wording thresholds 0.85/0.70, label
parsing, argmax/probability sanity). Run ./gradlew test and make it pass.
```

**You verify:** `./gradlew test` passes.

---

## Phase 3 — Parity test against Python

First, on your training machine (folder with `export/` and `test/`), run:

```bash
python tools/make_parity_set.py --data-dir <training folder> --out app/src/androidTest/assets/parity
```

Then prompt:

```
Implement the instrumented parity test from AGENTS.md section 8.2. Test images and
expected.json are in app/src/androidTest/assets/parity/. For every entry, run the image
through ImagePreprocessor + BeanClassifier and assert the same predicted label and each
probability within ±0.02. Print a per-image table in the test log.
```

**You verify:** `./gradlew connectedAndroidTest` passes on your device/emulator. If it fails, stop here and fix preprocessing before building UI. This is the most important test in the project.

---

## Phase 4 — Gallery flow + result screen

```
Build the Home screen and the Result screen from AGENTS.md section 5.1, with
ScanViewModel (StateFlow) and navigation. Home has "Scan a leaf" (leave the camera
button disabled for now), "Choose from gallery" using the system photo picker, and a
"Photo tips" link (placeholder screen is fine).

Result screen: square photo, diagnosis with colour + icon + text, confidence in words,
"Not sure" handling below 0.70, advice from strings.xml (section 6), "Scan another
leaf" button and the disclaimer. Inference runs off the main thread with a progress
indicator. Decode large images with downsampling.
```

**You verify:** pick test images from your gallery and check the results match the notebook; a non-leaf photo shows "Not sure" or a low confidence.

---

## Phase 5 — Camera

```
Build the Camera screen from AGENTS.md section 5.1 with CameraX: full-screen preview,
square guide overlay with hint text, shutter button, flash toggle, tap to focus.
Request CAMERA permission with a clear explanation, and handle permanent denial with a
button to open app settings. Captured photos go through the same pipeline as gallery
images. Enable the "Scan a leaf" button on Home.
```

**You verify:** take photos in portrait and landscape (same result), deny permission (no crash), rotate the phone during analysis (no crash).

---

## Phase 6 — Photo tips, polish, accessibility

```
Build the Photo tips screen (section 5.1). Then go through section 7: content
descriptions, 48 dp touch targets, font scaling, light/dark theme, colour never the
only signal. Add an app icon (simple leaf design). Log inference time in debug builds
only.
```

**You verify:** set the phone's font size to largest and use dark mode; everything is still readable and usable.

---

## Phase 7 — Release checklist

```
Go through the Definition of done in AGENTS.md section 9 and the manual checklist in
section 8.3. Confirm there is no INTERNET permission in the merged manifest, check APK
size, run all tests, and update README.md: tick finished features, add build steps and
placeholders for screenshots of each screen. Report anything I must check by hand.
```

**You verify:** airplane mode test, install on the cheapest phone you can find, add real screenshots to the README.

---

## Tips for working with Claude Code

- Start each session with: *"Read CLAUDE.md and AGENTS.md, then tell me what phase we're on."*
- If it starts adding features you didn't ask for, say: *"Only do phase N."*
- Ask it to **run the build and tests itself** after changes, not just write code.
- Paste complete error messages, not summaries.
- Commit after every working phase so you can always go back.
