package com.majeedar.beandoc.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Loads the model once and runs inference. Create one per app (see BeanDocApp) and reuse it.
 * The Interpreter is not thread-safe, so every call is serialised with [mutex].
 */
class BeanClassifier private constructor(
    private val interpreter: Interpreter,
    val labels: List<String>,
) : Closeable {

    private val mutex = Mutex()
    private val output = Array(1) { FloatArray(labels.size) }
    private var closed = false

    /** [input] must come from [ImagePreprocessor.toInputBuffer]. */
    suspend fun classify(input: ByteBuffer): Prediction = mutex.withLock {
        check(!closed) { "BeanClassifier is closed" }
        withContext(Dispatchers.Default) {
            input.rewind()
            interpreter.run(input, output)
            Prediction.from(labels, output[0].copyOf())
        }
    }

    /** Blocks until a running inference finishes. Only for tests or app shutdown; never call on the main thread. */
    override fun close() = runBlocking {
        mutex.withLock {
            if (!closed) {
                closed = true
                interpreter.close()
            }
        }
    }

    companion object {
        const val MODEL_ASSET = "bean_model.tflite"
        const val LABELS_ASSET = "labels.txt"
        private const val MIN_THREADS = 2
        private const val MAX_THREADS = 4

        /** Loads and validates the model. Slow (tens of ms); call off the main thread. */
        fun create(
            context: Context,
            modelAsset: String = MODEL_ASSET,
            labelsAsset: String = LABELS_ASSET,
        ): BeanClassifier {
            val labels = context.assets.open(labelsAsset).bufferedReader().use { Labels.parse(it.readText()) }
            val options = Interpreter.Options().apply {
                numThreads = Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS)
            }
            val interpreter = Interpreter(mapAsset(context, modelAsset), options)
            try {
                validate(interpreter, labels.size)
            } catch (e: IllegalStateException) {
                interpreter.close()
                throw e
            }
            return BeanClassifier(interpreter, labels)
        }

        private fun mapAsset(context: Context, name: String): MappedByteBuffer =
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }

        private fun validate(interpreter: Interpreter, labelCount: Int) {
            val size = ImagePreprocessor.INPUT_SIZE
            val input = interpreter.getInputTensor(0)
            check(input.shape().contentEquals(intArrayOf(1, size, size, 3)) && input.dataType() == DataType.FLOAT32) {
                "Model input must be float32[1, $size, $size, 3], " +
                    "got ${input.dataType()}${input.shape().contentToString()}"
            }
            val output = interpreter.getOutputTensor(0)
            check(output.shape().contentEquals(intArrayOf(1, labelCount))) {
                "Model output ${output.shape().contentToString()} does not match $labelCount labels in $LABELS_ASSET"
            }
        }
    }
}
