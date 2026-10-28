package com.majeedar.beandoc.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.majeedar.beandoc.BeanDocApp
import com.majeedar.beandoc.BuildConfig
import com.majeedar.beandoc.ml.ImageLoader
import com.majeedar.beandoc.ml.ImageLoader.Companion.toPixels
import com.majeedar.beandoc.ml.ImagePreprocessor
import com.majeedar.beandoc.ml.Prediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ScanState {
    data object Idle : ScanState
    data object Analyzing : ScanState
    data class Result(val photo: Bitmap, val prediction: Prediction) : ScanState
    data class Error(val reason: Reason, val uri: Uri) : ScanState {
        enum class Reason { IMAGE, MODEL }
    }
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val loader = ImageLoader(application.contentResolver)

    /** Starts analysing [uri]. Returns false (and does nothing) if a photo is already being analysed. */
    fun analyze(uri: Uri): Boolean {
        if (_state.value == ScanState.Analyzing) return false
        _state.value = ScanState.Analyzing
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.Default) { run(uri) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read image", e)
                ScanState.Error(ScanState.Error.Reason.IMAGE, uri)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Classification failed", e)
                ScanState.Error(ScanState.Error.Reason.MODEL, uri)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Classification failed", e)
                ScanState.Error(ScanState.Error.Reason.MODEL, uri)
            }
        }
        return true
    }

    fun retry() {
        val error = _state.value as? ScanState.Error ?: return
        analyze(error.uri)
    }

    fun reset() {
        if (_state.value != ScanState.Analyzing) _state.value = ScanState.Idle
    }

    private suspend fun run(uri: Uri): ScanState.Result {
        val start = SystemClock.elapsedRealtime()
        val classifier = getApplication<BeanDocApp>().classifier
        val square = loader.load(uri)
        val input = ImagePreprocessor.toInputBuffer(square.toPixels(), square.width)
        val photo = Bitmap.createScaledBitmap(square, DISPLAY_SIZE, DISPLAY_SIZE, true)
        if (photo !== square) square.recycle()
        val prediction = classifier.classify(input)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Scan took ${SystemClock.elapsedRealtime() - start} ms: $prediction")
        }
        return ScanState.Result(photo, prediction)
    }

    private companion object {
        const val TAG = "ScanViewModel"

        /** Display copy of the cropped photo; the model input is built from the full-resolution square. */
        const val DISPLAY_SIZE = 512
    }
}
