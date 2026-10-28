# AGENTS.md — BeanDoc: offline bean leaf disease detection app (Android)

This file tells a coding agent (or a developer) everything needed to build, test and extend the Android app.
Read it fully before writing code. If anything here conflicts with a later instruction from the project owner, the owner wins.

---

## 1. Goal

A farmer opens the app, takes a photo of a bean leaf, and within about a second sees:

1. **What the leaf has**: *Angular leaf spot*, *Bean rust* or *Healthy*
2. **How sure the app is**: a confidence level
3. **What to do about it**: short, practical advice

The app must work **fully offline**. Many users are in rural areas with weak or no mobile data, on low-end Android phones.

### Target users
- Smallholder bean farmers and agricultural extension officers
- Mixed literacy levels, so use icons, colours and short sentences
- Older, cheaper phones (2–3 GB RAM, Android 7.0+ / API 24)

---

## 2. The model (already trained, do not retrain in this repo)

Trained in [Bean-Disease-Classification](https://github.com/majeedar/Bean-Disease-Classification-with-MobileNet-Transformer-Architecture). The model files start in `model/` at the repo root and are moved into `app/src/main/assets/` during setup (see `docs/claude-code-prompts.md`, phase 1).

| Item | Value |
|---|---|
| File | `app/src/main/assets/bean_model.tflite` (~3.3 MB) |
| Labels | `app/src/main/assets/labels.txt` |
| Architecture | MobileNetV3-Large, ImageNet pretrained, fine-tuned |
| Test accuracy | 97.7% (125/128 test images), TFLite and Keras identical |
| Quantization | Dynamic-range (weights int8, float32 input/output) |

### Input/output contract (critical, get this exactly right)

| | Spec |
|---|---|
| Input tensor | `float32[1, 224, 224, 3]`, **NHWC**, **RGB** channel order |
| Input values | **Raw pixel values 0–255 as floats**. Do **NOT** divide by 255, do **NOT** subtract a mean, do **NOT** normalise to [-1, 1]. The model does its own preprocessing internally. |
| Output tensor | `float32[1, 3]` — softmax probabilities that sum to 1 |
| Output order | Same order as the lines of `labels.txt`: |

```
0  angular_leaf_spot
1  bean_rust
2  healthy
```

Read labels from `labels.txt` at runtime; never hard-code the order in more than one place.

### Preprocessing pipeline (must match training)
1. Decode the photo to an `ARGB_8888` Bitmap, downsampling large photos while decoding. Min SDK is 24, so use `BitmapFactory` with `inSampleSize` (`ImageDecoder` exists only on API 28+). Keep at least 224 px on the shortest side.
2. **Apply EXIF rotation** (camera photos are often stored sideways). Use `androidx.exifinterface`.
3. **Center-crop to a square** (shortest side).
4. **Resize to 224×224** with bilinear filtering exactly as `tf.image.resize(method="bilinear", antialias=False)` does it (half-pixel centers), keeping the interpolated values as floats. This is done in pure Kotlin in `ImagePreprocessor`, **not** with `Bitmap.createScaledBitmap`: Android's resampler is implementation-defined (Skia, varies by Android version) and is not guaranteed to match TF's sample positions, while an exact port makes the model input provably identical to Python's. `tools/make_parity_set.py` uses the same TF resize by default (`--resize tf`).
5. Write the values into a direct `ByteBuffer` (native byte order) as float32 R, G, B (0–255), row by row (steps 4 and 5 happen together).

Measured on the emulator: Android's JPEG decoding is bit-identical to PIL's, and the final model input matches Python to within 0.001.

Training images were resized to 224×224 without cropping, but a center crop is closer to how a farmer frames a leaf. If parity tests (section 8) fail, try plain resize without crop to compare.

Training (`bean_leaf_disease.ipynb` in the training repo) loads images with `keras.utils.image_dataset_from_directory(image_size=(224, 224))` and keeps the defaults: bilinear `tf.image.resize`, no antialias, no crop. The iBean training images are square, so the crop is a no-op on them. Do not use the PIL snippet in that repo's README as the reference: `Image.resize` without `resample` is not TF bilinear.

---

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| Inference | **LiteRT** (`com.google.ai.edge.litert:litert`), the successor of TensorFlow Lite. The API is still `org.tensorflow.lite.Interpreter`. `FileUtil.loadMappedFile` lives in `litert-support`; alternatively memory-map the asset yourself (`AssetFileDescriptor` + `FileChannel.map`) and skip that dependency. Fallback: `org.tensorflow:tensorflow-lite:2.16.1`. |
| Image helpers | `androidx.exifinterface` |
| Async | Kotlin coroutines; run inference on `Dispatchers.Default` |
| Architecture | Single-activity, MVVM (`ViewModel` + `StateFlow`) |
| Min SDK | 24 (Android 7.0) |
| Target/compile SDK | Latest stable |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |

Use the **latest stable** versions of every library at build time; check Maven/Google Maven rather than relying on memory.
Prefer LiteRT over legacy TFLite because Google Play requires 16 KB memory page support for apps targeting Android 15+, which recent LiteRT builds provide.

**Stay on the LiteRT 1.x line** (1.4.2 at the time of writing; native libraries verified 16 KB-aligned) rather than 2.x. LiteRT 2.x pulls in Play Services, WorkManager, Room and an alpha `ai-delivery` library for downloading models, adds FOREGROUND_SERVICE / WAKE_LOCK / ACCESS_NETWORK_STATE / RECEIVE_BOOT_COMPLETED permissions, and made the APK ~3× larger, all for features this offline app does not use. Both lines produce identical parity results with this model.

The release build keeps only `arm64-v8a` and `armeabi-v7a` native libraries and compresses them (`useLegacyPackaging`) to keep the download small (~9 MB). The manifest explicitly removes `ACCESS_NETWORK_STATE` and `INTERNET` in case a library adds them.

### Gradle notes
```kotlin
android {
    androidResources {
        noCompress += "tflite"   // model must be stored uncompressed so it can be memory-mapped
    }
}
```

No internet permission is needed. The only runtime permission is `CAMERA`.

---

## 4. Project structure

```
app/src/main/
├── assets/
│   ├── bean_model.tflite
│   └── labels.txt
├── java/<package>/
│   ├── BeanDocApp.kt                # Application: owns the app-scoped BeanClassifier (lazy)
│   ├── MainActivity.kt
│   ├── ml/
│   │   ├── BeanClassifier.kt        # loads model, runs inference, returns Prediction
│   │   ├── ImageLoader.kt           # Uri -> decode (downsampled), EXIF rotate, center crop (Android)
│   │   ├── ImagePreprocessor.kt     # pure Kotlin: square ARGB IntArray -> TF-bilinear 224×224 float ByteBuffer (JVM-testable)
│   │   └── Prediction.kt            # data class: label, confidence, all probabilities
│   ├── data/
│   │   └── DiseaseInfo.kt           # looks up advice/colour/icon by label, with a generic fallback
│   ├── ui/
│   │   ├── home/HomeScreen.kt
│   │   ├── camera/CameraScreen.kt
│   │   ├── result/ResultScreen.kt
│   │   ├── tips/PhotoTipsScreen.kt
│   │   └── theme/
│   └── viewmodel/ScanViewModel.kt
└── res/values/strings.xml           # ALL user-facing text lives here (for translation)
```

---

## 5. Features

### 5.1 Must have (v1)

**Home screen**
- Large "Scan a leaf" button (camera icon)
- Secondary "Choose from gallery" button (system photo picker, `PickVisualMedia`; no storage permission needed)
- Link to "How to take a good photo"

**Camera screen (CameraX)**
- Full-screen preview with a square guide frame overlay and the hint "Place one leaf inside the frame"
- Big shutter button; flash toggle
- Capture with `ImageCapture`, at a reasonable resolution (e.g. ~1280 px longest side is plenty, since the model sees 224 px)
- Save each capture to a file in `cacheDir` (not an in-memory `ImageProxy`) so its rotation is in EXIF. Camera and gallery photos then both arrive as a `Uri` and share one decode path
- Ignore the shutter while a photo is being analysed
- Handle permission denial gracefully with an explanation and a button to open settings

**Inference**
- `BeanClassifier` is an **application-scoped singleton**, created lazily from the `Application` class and reused; the model is memory-mapped (see section 3)
- The `Interpreter` is not thread-safe: serialise `classify()` with a `Mutex`. Never close it while the app is in use. In particular, do **not** close it in `ScanViewModel.onCleared()`, because inference may still be running on another thread
- Run on a background dispatcher; show a short progress indicator
- Interpreter options: 2–4 threads on CPU (XNNPACK is on by default). No GPU delegate in v1; it adds size and device-specific bugs for little gain on this model

**State and navigation**
- One `ScanViewModel`, scoped to the navigation graph, exposes a `StateFlow<ScanState>` (`Idle`, `Analyzing`, `Result`, `Error`). Screens read the photo and result from it; never pass Bitmaps or Uris through navigation arguments
- While the state is `Analyzing`, ignore new photos (shutter or gallery)
- If the Result screen opens with no result (e.g. after process death), return to Home instead of crashing

**Result screen**
- The photo (cropped square, as the model saw it)
- Diagnosis title + colour + icon: green = healthy, orange = angular leaf spot, red-brown = bean rust
- Confidence shown in words, not just a number:

| Top probability | Shown as |
|---|---|
| ≥ 0.85 | "Very likely" |
| 0.70 – 0.85 | "Likely" |
| < 0.70 | **"Not sure"**: show retake advice instead of a diagnosis |

- Below 0.70: do **not** present a diagnosis as fact. Show "We could not recognise this clearly. Please take another photo of one leaf, in daylight, filling the frame." plus a retake button. Optionally show the top guess in small text.
- Advice section for the predicted disease (section 6)
- Buttons: "Scan another leaf", "Photo tips"
- A short disclaimer: "This app can make mistakes. If many plants are affected, ask your local extension officer."

**Photo tips screen**
- One leaf at a time, filling the frame
- Daylight, but avoid harsh direct sun and strong shadows
- Hold the phone steady; tap to focus
- Show the top (upper) side of the leaf where the spots are

**Offline**
- Zero network calls. No analytics SDKs, no ads, no crash reporters that send data, unless the owner explicitly asks later.

### 5.2 Nice to have (v2, do not build unless asked)
- Scan history stored locally (Room): photo thumbnail, result, date
- Local language translations (`values-<lang>/strings.xml`)
- Text-to-speech reading of the result for low-literacy users
- Live classification on the camera preview (`ImageAnalysis`)
- Optional, consent-based saving of photos to help improve the model later
- "Not a bean leaf" class (needs a retrained model; app should already handle a different `labels.txt` length without code changes, via the `DiseaseInfo` fallback in section 7)

---

## 6. Disease advice content

Put this in `strings.xml` (not hard-coded in Kotlin), keyed by label. Keep sentences short.
**This text is general guidance and must be reviewed by a local agronomist or extension officer before release.** Do not invent specific fungicide product names or doses.

### angular_leaf_spot — Angular leaf spot (fungal)
- **What you see:** Grey-brown spots with straight edges, limited by the leaf veins, so they look angular. Spots can join, and leaves may yellow and fall early. Pods can show reddish-brown spots.
- **How it spreads:** Through infected seed, crop leftovers in the soil, and rain splash or wind.
- **What to do now:**
  - Remove and destroy badly infected leaves and plants (do not leave them in the field)
  - Avoid working in the field when plants are wet
  - Ask your extension officer which fungicide is approved locally if the disease is spreading
- **Prevent next season:**
  - Use certified, disease-free seed
  - Rotate with a non-bean crop for at least 2 seasons
  - Plough under or remove crop leftovers after harvest
  - Plant resistant varieties where available

### bean_rust — Bean rust (fungal)
- **What you see:** Small, raised, rusty-brown powdery spots (pustules), mostly on the underside of leaves, often with a yellow ring. Rubbing a spot leaves brown powder on your finger.
- **How it spreads:** Spores carried by wind; favoured by humid weather and morning dew.
- **What to do now:**
  - Remove heavily infected leaves early
  - Do not work among wet plants; you will spread the spores
  - Ask your extension officer which fungicide is approved locally if the disease is spreading
- **Prevent next season:**
  - Plant resistant varieties
  - Give plants enough spacing for air to move
  - Remove volunteer bean plants and crop leftovers
  - Rotate crops

### healthy — Healthy
- **What you see:** No disease signs detected on this leaf.
- **Keep it healthy:**
  - Check your plants every week, especially after rain
  - Scan several leaves from different parts of the field, since one healthy leaf does not mean the whole field is healthy
  - Keep using clean seed and crop rotation

---

## 7. Code guidelines

- Kotlin idioms, no `!!` in production code; handle nulls explicitly
- All UI text in `strings.xml`; no hard-coded user-facing strings
- No blocking work on the main thread (decoding, preprocessing, inference)
- Downscale large photos while decoding (`BitmapFactory` + `inSampleSize`; see section 2) to avoid out-of-memory errors on cheap phones
- Recycle intermediate bitmaps where practical
- `BeanClassifier` must validate at load time that the model's input shape is `[1, 224, 224, 3]` and the output length equals the number of labels; fail with a clear error otherwise
- Keep `ImagePreprocessor` a pure Kotlin function (square ARGB `IntArray` of any size in → 224×224 float `ByteBuffer` out) with no `android.*` imports, so it runs in JVM unit tests. `android.graphics.Bitmap` is a stub on the JVM; all Bitmap work lives in `ImageLoader`
- `DiseaseInfo` maps a label to its advice strings, colour and icon. Find strings by naming convention (`<label>_title`, `<label>_what_you_see`, …). Any label without entries gets a generic neutral colour, a generic icon and a "No advice available for this result yet" text, so a new `labels.txt` never crashes the app
- Accessibility: content descriptions on icons and images, minimum 48 dp touch targets, text that scales with system font size, and colour never as the only signal (always pair with an icon and text)
- Support light and dark theme

---

## 8. Testing

### 8.1 Unit tests (JVM)
- Confidence → wording mapping (thresholds 0.85 / 0.70)
- Label file parsing
- Argmax and probability-sum sanity checks
- `ImagePreprocessor`: a known ARGB pixel becomes the floats R, G, B in that order, still 0–255 (not divided by 255); the buffer is 224×224×3×4 bytes; resized values equal `tf.image.resize` reference values
- `DiseaseInfo` fallback for an unknown label

### 8.2 Instrumented tests (`androidTest`) — model parity
Goal: prove the Android pipeline gives the **same predictions as Python**.

1. The project owner generates the parity set on the training machine with
   `python tools/make_parity_set.py --data-dir <training folder> --out app/src/androidTest/assets/parity`.
   This copies a few test images per class (named `<class>__<filename>`) and writes `expected.json`.
   For the first image of each class it also writes two synthetic variants, because the iBean test images are square and have no EXIF data:
   - `<class>__synthetic_wide__<name>.jpg`: padded to a wide, non-square image (checks the center crop)
   - `<class>__synthetic_exif6__<name>.jpg`: pixels stored sideways with EXIF orientation 6 (checks the rotation)
2. `expected.json` format:
   ```json
   {
     "labels": ["angular_leaf_spot", "bean_rust", "healthy"],
     "input_size": 224,
     "resize": "tf",
     "grid_stride": 16,
     "images": {
       "bean_rust__img_001.jpg": {"true": "bean_rust", "pred": "bean_rust", "probs": [0.01, 0.98, 0.01], "synthetic": false,
                                  "input_grid": [112.25, 140.5, 61.0, "... model input at every 16th row/column, R G B"]}
     }
   }
   ```
3. The test runs each image through the full app pipeline (`ImageLoader` + `ImagePreprocessor` + `BeanClassifier`), starting from a file `Uri`, so EXIF and crop are covered. It asserts:
   - **model input** within ±0.01 of Python's at every grid point (strict: this is the part the app controls)
   - same predicted label as Python
   - each probability within ±0.10 of Python's value

Python uses the same preprocessing as the app: EXIF rotation, center crop to square, bilinear resize to 224×224 without antialiasing (`tf.image.resize`, the training default), raw 0–255 RGB floats.

Why the probability tolerance is ±0.10 and not ±0.02: the dynamic-range quantized model gives slightly different probabilities on different runtimes **even for byte-identical input**. Measured on `bean_rust_test.11`: TF 2.17 Python with XNNPACK 0.825, without XNNPACK 0.911, LiteRT 2.3.2 on Android 0.778. Labels agree on all parity images; borderline images (top probability near 0.5) could flip, so keep the parity set to images Python predicts confidently.

If the input check fails only on synthetic images, the bug is in rotation or crop; if it fails on all images, suspect the resize, channel order or normalisation.

### 8.3 Manual test checklist
- [ ] Photo taken in portrait and landscape gives the same result (EXIF rotation works)
- [ ] Gallery image works
- [ ] Non-leaf photo (hand, soil, wall) mostly shows "Not sure" rather than a confident diagnosis (document the behaviour; a perfect result here needs a retrained model)
- [ ] Airplane mode: everything works
- [ ] Camera permission denied → clear message, app does not crash
- [ ] Rotate the device during inference → no crash, result still shown
- [ ] Runs on a low-end device or emulator (2 GB RAM, API 24) without out-of-memory errors
- [ ] Inference time per photo logged in debug builds; target < 1 s on a mid-range phone

---

## 9. Definition of done (v1)

- [ ] `./gradlew assembleDebug` and `./gradlew test` pass with no errors
- [ ] Parity test passes on an emulator or device
- [ ] All "Must have" features in 5.1 work end-to-end
- [ ] No network permission in the merged manifest
- [ ] APK size under ~20 MB
- [ ] README with build steps, how to swap in a new model (replace the two asset files; keep the input contract), and a screenshot of each screen

---

## 10. Updating the model later

When a new model is trained (for example with an extra "not a bean leaf" class or more field photos):

1. Export it with the same notebook (input still raw 0–255 RGB, 224×224)
2. Replace `bean_model.tflite` and `labels.txt` in `assets/`
3. Add advice strings for any new label, following the `<label>_*` naming convention (section 7). A label without strings still works and shows the generic fallback; add a colour/icon in `DiseaseInfo` only if you want a specific one
4. Re-generate `expected.json` and re-run the parity test

No Kotlin changes are needed as long as the input contract stays the same.

---

## 11. Distribution (APK download website)

The APK is published on a simple static website on the owner's Hetzner VM. The website is separate from the app: the app itself still has **no INTERNET permission** and never checks for updates.

| Item | Rule |
|---|---|
| Direct link | Stable URL that always serves the latest APK: `https://<domain>/beandoc.apk`. Versioned copies are also kept: `beandoc-<versionName>.apk` |
| QR code | Encodes the **stable direct link** (not a versioned file), so printed posters keep working after updates. Generated locally at build time (e.g. Python `segno`); never use an online QR service. Shown on the page and downloadable as **SVG** (printing) and **PNG** (sharing), with the link printed as text underneath |
| Page | One static `index.html` in `site/`, readable on small phones: download button, version, file size, SHA-256, QR code + download buttons, "Install unknown apps" steps, disclaimer. No analytics, trackers, external fonts or CDNs |
| Signing | Every release signed with the **same** release key; keystore and passwords never committed (read from `local.properties` / environment variables). Losing the key means users must uninstall to update |
| Versions | Increase `versionCode` for every published APK |
| Server | Caddy with automatic HTTPS; serve `.apk` as `application/vnd.android.package-archive` |
| Deploy | Script in `deploy/` rsyncs `site/` to the VM. Publishing is the owner's step; an agent prepares it but does not run it |
