"""Generate parity test data for the BeanDoc Android app.

Runs a few test images per class through the TFLite model with the same preprocessing
the app uses (center crop -> 224x224 bilinear, raw 0-255 RGB floats), then writes:

    <out>/<class>__<filename>   copies of the images
    <out>/expected.json         predicted label + probabilities from Python

The Android instrumented test checks that the app produces the same results.

Usage:
    python tools/make_parity_set.py --data-dir path/to/training/folder \
        --out app/src/androidTest/assets/parity

--data-dir must contain export/bean_model.tflite, export/labels.txt and test/<class>/...
"""
import argparse
import json
import os
import shutil

import numpy as np
import tensorflow as tf
from PIL import Image

SIZE = 224


def center_crop_resize(path, size=SIZE):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    s = min(w, h)
    img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))
    return np.asarray(img.resize((size, size), Image.BILINEAR), dtype=np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", required=True)
    ap.add_argument("--out", default="app/src/androidTest/assets/parity")
    ap.add_argument("--per-class", type=int, default=4)
    args = ap.parse_args()

    model_path = os.path.join(args.data_dir, "export", "bean_model.tflite")
    labels = open(os.path.join(args.data_dir, "export", "labels.txt")).read().split()
    os.makedirs(args.out, exist_ok=True)

    it = tf.lite.Interpreter(model_path=model_path)
    it.allocate_tensors()
    inp, out = it.get_input_details()[0], it.get_output_details()[0]

    results = {}
    for cls in labels:
        cls_dir = os.path.join(args.data_dir, "test", cls)
        for name in sorted(os.listdir(cls_dir))[: args.per_class]:
            src = os.path.join(cls_dir, name)
            key = f"{cls}__{name}"
            it.set_tensor(inp["index"], center_crop_resize(src)[None])
            it.invoke()
            p = it.get_tensor(out["index"])[0]
            results[key] = {
                "true": cls,
                "pred": labels[int(p.argmax())],
                "probs": [round(float(v), 4) for v in p],
            }
            shutil.copy(src, os.path.join(args.out, key))

    with open(os.path.join(args.out, "expected.json"), "w") as f:
        json.dump({"labels": labels, "input_size": SIZE, "images": results}, f, indent=2)

    correct = sum(r["true"] == r["pred"] for r in results.values())
    print(f"Wrote {len(results)} images to {args.out} ({correct}/{len(results)} predicted correctly)")


if __name__ == "__main__":
    main()
