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
- Older, cheaper phones (2–3 GB RAM, Android 8+)

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
1. Decode the photo to an `ARGB_8888` Bitmap.
2. **Apply EXIF rotation** (camera photos are often stored sideways). Use `androidx.exifinterface`.
3. **Center-crop to a square** (shortest side).
4. **Resize to 224×224** with bilinear filtering.
5. Copy pixels into a direct `ByteBuffer` (native byte order) as float32 R, G, B (0–255), row by row.

Training images were resized to 224×224 without cropping, but a center crop is closer to how a farmer frames a leaf. If parity tests (section 8) fail, try plain resize without crop to compare.

---

## 3. Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) |
| Inference | **LiteRT** (`com.google.ai.edge.litert:litert`), the successor of TensorFlow Lite. The API is still `org.tensorflow.lite.Interpreter`. Fallback: `org.tensorflow:tensorflow-lite:2.16.1`. |
| Image helpers | `androidx.exifinterface` |
| Async | Kotlin coroutines; run inference on `Dispatchers.Default` |
| Architecture | Single-activity, MVVM (`ViewModel` + `StateFlow`) |
| Min SDK | 24 (Android 7.0) |
| Target/compile SDK | Latest stable |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |

Use the **latest stable** versions of every library at build time; check Maven/Google Maven rather than relying on memory.
Prefer LiteRT over legacy TFLite because Google Play requires 16 KB memory page support for apps targeting Android 15+, which recent LiteRT builds provide.

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
│   ├── MainActivity.kt
│   ├── ml/
│   │   ├── BeanClassifier.kt        # loads model, runs inference, returns Prediction
│   │   ├── ImagePreprocessor.kt     # EXIF rotate, center crop, resize, to ByteBuffer
│   │   └── Prediction.kt            # data class: label, confidence, all probabilities
│   ├── data/
│   │   └── DiseaseInfo.kt           # advice content, keyed by label
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
- Handle permission denial gracefully with an explanation and a button to open settings

**Inference**
- `BeanClassifier` is created **once** (e.g. in the ViewModel or an application-scoped singleton) and reused; the model is memory-mapped with `FileUtil.loadMappedFile` or equivalent
- Run on a background dispatcher; show a short progress indicator
- Interpreter options: 2–4 threads. Optionally try the GPU delegate or XNNPACK, but **CPU must always work** as a fallback
- Close the interpreter in `onCleared()` / when no longer needed

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
- "Not a bean leaf" class (needs a retrained model; app should already handle a different `labels.txt` length without code changes)

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
- Downscale large photos while decoding (`inSampleSize` / `ImageDecoder` target size) to avoid out-of-memory errors on cheap phones
- Recycle intermediate bitmaps where practical
- `BeanClassifier` must validate at load time that the model's input shape is `[1, 224, 224, 3]` and the output length equals the number of labels; fail with a clear error otherwise
- Keep `ImagePreprocessor` a pure function (Bitmap in → ByteBuffer out) so it is unit-testable
- Accessibility: content descriptions on icons and images, minimum 48 dp touch targets, text that scales with system font size, and colour never as the only signal (always pair with an icon and text)
- Support light and dark theme

---

## 8. Testing

### 8.1 Unit tests (JVM)
- Confidence → wording mapping (thresholds 0.85 / 0.70)
- Label file parsing
- Argmax and probability-sum sanity checks

### 8.2 Instrumented tests (`androidTest`) — model parity
Goal: prove the Android pipeline gives the **same predictions as Python**.

1. The project owner generates the parity set on the training machine with
   `python tools/make_parity_set.py --data-dir <training folder> --out app/src/androidTest/assets/parity`.
   This copies a few test images per class (named `<class>__<filename>`) and writes `expected.json`.
2. `expected.json` format:
   ```json
   {
     "labels": ["angular_leaf_spot", "bean_rust", "healthy"],
     "input_size": 224,
     "images": {
       "bean_rust__img_001.jpg": {"true": "bean_rust", "pred": "bean_rust", "probs": [0.01, 0.98, 0.01]}
     }
   }
   ```
3. The test runs each image through `ImagePreprocessor` + `BeanClassifier` and asserts:
   - same predicted label as Python
   - each probability within ±0.02 of Python's value

Python uses the same preprocessing as the app: center crop to square, bilinear resize to 224×224, raw 0–255 RGB floats. Small differences in JPEG decoding and resizing between PIL and Android are why the tolerance is ±0.02 rather than exact.

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
3. Add advice strings for any new label
4. Re-generate `expected.json` and re-run the parity test

No Kotlin changes should be needed as long as the input contract stays the same.
