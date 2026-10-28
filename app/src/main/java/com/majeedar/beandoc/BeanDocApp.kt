package com.majeedar.beandoc

import android.app.Application
import com.majeedar.beandoc.ml.BeanClassifier

class BeanDocApp : Application() {
    /**
     * One classifier for the whole app, created on first use (off the main thread) and never closed:
     * closing it while an inference runs would crash in native code.
     */
    val classifier: BeanClassifier by lazy { BeanClassifier.create(this) }
}
