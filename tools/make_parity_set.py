"""Generate parity test data for the BeanDoc Android app.

Runs a few test images per class through the TFLite model with the same preprocessing
the app uses (EXIF rotation -> center crop -> 224x224 bilinear, raw 0-255 RGB floats),
then writes:

    <out>/<class>__<filename>                    copies of the images
    <out>/<class>__synthetic_wide__<name>.jpg    non-square copy (checks the center crop)
    <out>/<class>__synthetic_exif6__<name>.jpg   sideways copy + EXIF orientation 6 (checks rotation)
    <out>/expected.json                          predicted label, probabilities and sampled model input

The Android instrumented test checks that the app produces the same results.

Usage:
    python tools/make_parity_set.py --data-dir path/to/training/folder \
        --out app/src/androidTest/assets/parity

--data-dir must contain export/bean_model.tflite, export/labels.txt and test/<class>/...

--resize picks the downscaling method and must match both training and the app:
    tf   tf.image.resize bilinear, no antialiasing. Matches Keras image_dataset_from_directory
         and Android Bitmap.createScaledBitmap(filter = true). Default.
    pil  PIL Image.BILINEAR, which antialiases when shrinking. Use only if training used PIL.
"""
import argparse
import json
import os
import shutil

import numpy as np
import tensorflow as tf
from PIL import Image, ImageOps

SIZE = 224
GRID_STRIDE = 16
EXIF_ORIENTATION = 0x0112


def load_rgb(path):
    return ImageOps.exif_transpose(Image.open(path)).convert("RGB")


def center_crop_resize(path, resize, size=SIZE):
    img = load_rgb(path)
    w, h = img.size
    s = min(w, h)
    img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))
    if resize == "pil":
        return np.asarray(img.resize((size, size), Image.BILINEAR), dtype=np.float32)
    arr = np.asarray(img, dtype=np.float32)
    return tf.image.resize(arr, (size, size), method="bilinear", antialias=False).numpy()


def make_synthetic(src, out_dir, cls, name):
    """Write a non-square and an EXIF-rotated variant of src; return their paths."""
    base = os.path.splitext(name)[0] + ".jpg"
    arr = np.asarray(load_rgb(src))

    # Pad left/right with reflected pixels; a correct center crop recovers the original.
    pad = arr.shape[0] // 4
    wide = np.pad(arr, ((0, 0), (pad, pad), (0, 0)), mode="reflect")
    wide_path = os.path.join(out_dir, f"{cls}__synthetic_wide__{base}")
    Image.fromarray(wide).save(wide_path, "JPEG", quality=95)

    # Store pixels rotated 90 degrees CCW and tag orientation 6 ("rotate 90 CW to display").
    exif = Image.Exif()
    exif[EXIF_ORIENTATION] = 6
    exif_path = os.path.join(out_dir, f"{cls}__synthetic_exif6__{base}")
    Image.fromarray(arr).rotate(90, expand=True).save(
        exif_path, "JPEG", quality=95, exif=exif.tobytes())

    return [wide_path, exif_path]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", required=True)
    ap.add_argument("--out", default="app/src/androidTest/assets/parity")
    ap.add_argument("--per-class", type=int, default=4)
    ap.add_argument("--resize", choices=["tf", "pil"], default="tf")
    args = ap.parse_args()

    model_path = os.path.join(args.data_dir, "export", "bean_model.tflite")
    labels = open(os.path.join(args.data_dir, "export", "labels.txt")).read().split()
    os.makedirs(args.out, exist_ok=True)

    it = tf.lite.Interpreter(model_path=model_path)
    it.allocate_tensors()
    inp, out = it.get_input_details()[0], it.get_output_details()[0]

    def predict(path, cls, synthetic):
        x = center_crop_resize(path, args.resize)
        it.set_tensor(inp["index"], x[None])
        it.invoke()
        p = it.get_tensor(out["index"])[0]
        results[os.path.basename(path)] = {
            "true": cls,
            "pred": labels[int(p.argmax())],
            "probs": [round(float(v), 4) for v in p],
            "synthetic": synthetic,
            # Model input at every GRID_STRIDE-th pixel (row-major, R G B), to check preprocessing exactly.
            "input_grid": [round(float(v), 3) for v in x[::GRID_STRIDE, ::GRID_STRIDE].flatten()],
        }

    results = {}
    for cls in labels:
        cls_dir = os.path.join(args.data_dir, "test", cls)
        for i, name in enumerate(sorted(os.listdir(cls_dir))[: args.per_class]):
            src = os.path.join(cls_dir, name)
            dst = os.path.join(args.out, f"{cls}__{name}")
            shutil.copy(src, dst)
            predict(dst, cls, synthetic=False)
            if i == 0:
                for path in make_synthetic(src, args.out, cls, name):
                    predict(path, cls, synthetic=True)

    with open(os.path.join(args.out, "expected.json"), "w") as f:
        json.dump({"labels": labels, "input_size": SIZE, "resize": args.resize,
                   "grid_stride": GRID_STRIDE, "images": results}, f, indent=1)

    correct = sum(r["true"] == r["pred"] for r in results.values())
    print(f"Wrote {len(results)} images to {args.out} ({correct}/{len(results)} predicted correctly)")


if __name__ == "__main__":
    main()
