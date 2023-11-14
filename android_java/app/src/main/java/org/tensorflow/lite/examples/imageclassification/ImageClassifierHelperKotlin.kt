/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tensorflow.lite.examples.imageclassification

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tensorflow.lite.examples.imageclassification.fragments.DynamicBitmapSource
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.classifier.Classifications
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import org.tensorflow.lite.task.vision.classifier.ImageClassifier.ImageClassifierOptions
import java.lang.IllegalStateException
import kotlin.math.max

/** Helper class for wrapping Image Classification actions  */
class ImageClassifierHelperKotlin(
    private val context: Context,
    private val imageClassifierListener: ClassifierListener?,
    private val bitmapSource: DynamicBitmapSource?,
    private val index: Int
) : ViewModel() {
    var threshold: Float = 0.5f
    var numThreads: Int = 2
    var maxResults: Int = 3
    private var currentDelegate: Int = 0
    private var currentModel: Int = 0
    var taskPeriod: Long = 500
    private var run = false
    private var job: Job? = null
    private var inferenceTime: Long = 0
    private var executionCount = 0
    private var totalExecutionTime: Long = 1
    private var imageClassifier: ImageClassifier? = null

    /** Helper class for wrapping Image Classification actions  */
    init {
        setupImageClassifier()
    }

    fun setCurrentDelegate(currentDelegate: Int) {
        this.currentDelegate = currentDelegate
    }

    fun getCurrentDelegate(): String {
        return delegateName
    }

    fun setCurrentModel(currentModel: Int) {
        this.currentModel = currentModel
    }

    fun getCurrentModel(): String {
        return this.modelName
    }

    fun getIndex(): Int {
        return index
    }

    fun calculateThroughput(): Long {
        return (executionCount * 1000) / max(1L, (totalExecutionTime / 1000000));
    }

    private fun setupImageClassifier() {
        val optionsBuilder = ImageClassifierOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)
        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(
            numThreads
        )
        when (currentDelegate) {
            DELEGATE_CPU -> {}
            DELEGATE_GPU -> if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                baseOptionsBuilder.useGpu()
            } else {
                imageClassifierListener?.onError(
                    "GPU is not supported on "
                            + "this device"
                )
            }

            DELEGATE_NNAPI -> baseOptionsBuilder.useNnapi()
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        val modelName = modelName

        try {
            imageClassifier = ImageClassifier.createFromFileAndOptions(
                context,
                modelName,
                optionsBuilder.build()
            )
        } catch (e: IllegalStateException) {
            imageClassifierListener?.onError(
                "Image classifier failed to "
                        + "initialize. See error logs for details"
            )
            Log.e(
                TAG, "TFLite failed to load model with error: "
                        + e.message
            )
        }
    }

    private fun resetRtData() {
        totalExecutionTime = 0
        executionCount = 0
        inferenceTime = SystemClock.uptimeMillis()
    }

    fun startCollect() = runBlocking <Unit>{
        run = true
        resetRtData()
        launch {
            collectStream()
        }
    }

    fun pauseCollect() {
        run = false
        job?.cancel()
    }

    private suspend fun collectStream() {
        job = viewModelScope.launch(Dispatchers.IO) {
            bitmapSource?.bitmapStream?.collect {
                if (it != null && run) {
                    classify(it.latestBitmap, it.latestImageRotation)

                }
            }
        }
    }

    @Throws(InterruptedException::class)
    fun classify(image: Bitmap?, imageRotation: Int) {
        if (imageClassifier == null) {
            setupImageClassifier()
        }

        // Inference time is the difference between the system time at the start
        // and finish of the process
        val startTime = SystemClock.uptimeMillis()
        if (inferenceTime != 0L) {
            totalExecutionTime += startTime - inferenceTime
        }

        // Create preprocessor for the image.
        // See https://www.tensorflow.org/lite/inference_with_metadata/
        //            lite_support#imageprocessor_architecture
        val imageProcessor = ImageProcessor.Builder().add(Rot90Op(-imageRotation / 90)).build()

        // Preprocess the image and convert it into a TensorImage for classification.
        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

        // Classify the input image
        val result = imageClassifier?.classify(tensorImage)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime
        val timeLeftInPeriod = taskPeriod - inferenceTime
        if (timeLeftInPeriod > 0) {
            runBlocking {
                delay(timeLeftInPeriod)
            }
        }
        totalExecutionTime += inferenceTime
        executionCount++
        imageClassifierListener?.onResults(result, inferenceTime)
    }

    fun clearImageClassifier() {
        imageClassifier = null
    }

    /** Listener for passing results back to calling class  */
    interface ClassifierListener {
        fun onError(error: String?)
        fun onResults(results: List<Classifications?>?, inferenceTime: Long)
    }

    private val modelName: String
        get() {
            val modelName: String = when (currentModel) {
                MODEL_MOBILENETV1 -> "mobilenetv1.tflite"
                MODEL_EFFICIENTNETV0 -> "efficientnet-lite0.tflite"
                MODEL_EFFICIENTNETV1 -> "efficientnet-lite1.tflite"
                MODEL_EFFICIENTNETV2 -> "efficientnet-lite2.tflite"
                else -> "mobilenetv1.tflite"
            }
            return modelName
        }
    private val delegateName: String
        get() {
            var delegateName = "unknown"
            when (currentDelegate) {
                DELEGATE_CPU -> delegateName = "CPU"
                DELEGATE_GPU -> delegateName = "GPU"
                DELEGATE_NNAPI -> delegateName = "NPU"
            }
            return delegateName
        }

    companion object {
        private const val TAG = "ImageClassifierHelper"
        private const val DELEGATE_CPU = 0
        private const val DELEGATE_GPU = 1
        private const val DELEGATE_NNAPI = 2
        private const val MODEL_MOBILENETV1 = 0
        private const val MODEL_EFFICIENTNETV0 = 1
        private const val MODEL_EFFICIENTNETV1 = 2
        private const val MODEL_EFFICIENTNETV2 = 3
    }
}