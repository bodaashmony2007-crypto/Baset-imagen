package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.ImageConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.Exception

class MainViewModel : ViewModel() {

    private val _originalImageUri = MutableStateFlow<Uri?>(null)
    val originalImageUri: StateFlow<Uri?> = _originalImageUri.asStateFlow()

    private val _enhancedImageBase64 = MutableStateFlow<String?>(null)
    val enhancedImageBase64: StateFlow<String?> = _enhancedImageBase64.asStateFlow()

    private val _analysisResult = MutableStateFlow<String?>(null)
    val analysisResult: StateFlow<String?> = _analysisResult.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    fun setOriginalImageUri(uri: Uri?) {
        _originalImageUri.value = uri
        _enhancedImageBase64.value = null
        _analysisResult.value = null
    }

    fun analyzeImage(context: Context, prompt: String) {
        val uri = _originalImageUri.value ?: return
        _isProcessing.value = true
        _analysisResult.value = null
        viewModelScope.launch {
            try {
                val base64Image = uriToBase64(context, uri)
                if (base64Image != null) {
                    val request = GenerateContentRequest(
                        contents = listOf(Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                            )
                        ))
                    )
                    val response = RetrofitClient.service.generateContent(
                        model = "gemini-3.1-pro-preview",
                        apiKey = BuildConfig.GEMINI_API_KEY,
                        request = request
                    )
                    _analysisResult.value = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
            } catch (e: Exception) {
                _analysisResult.value = "Error analyzing image: ${e.message}"
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun enhanceImage(context: Context, prompt: String, resolution: String, ratio: String) {
        val uri = _originalImageUri.value
        _isProcessing.value = true
        _enhancedImageBase64.value = null
        _processingProgress.value = 0f
        viewModelScope.launch {
            try {
                _processingProgress.value = 0.3f
                val parts = mutableListOf<Part>()
                parts.add(Part(text = prompt))
                
                if (uri != null) {
                    val base64Image = uriToBase64(context, uri)
                    if (base64Image != null) {
                        parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
                    }
                }
                
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = parts)),
                    generationConfig = GenerationConfig(
                        imageConfig = ImageConfig(aspectRatio = ratio, imageSize = resolution),
                        responseModalities = listOf("TEXT", "IMAGE")
                    )
                )
                
                _processingProgress.value = 0.6f
                // We use gemini-3.1-flash-image-preview for general edit/generate, or gemini-3-pro-image-preview for high-quality
                val model = if (resolution == "4K") "gemini-3-pro-image-preview" else "gemini-3.1-flash-image-preview"
                
                val response = RetrofitClient.service.generateContent(
                    model = model,
                    apiKey = BuildConfig.GEMINI_API_KEY,
                    request = request
                )
                
                _processingProgress.value = 0.9f
                
                val candidateParts = response.candidates?.firstOrNull()?.content?.parts
                val imagePart = candidateParts?.find { it.inlineData != null }
                if (imagePart?.inlineData != null) {
                    _enhancedImageBase64.value = imagePart.inlineData.data
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                _processingProgress.value = 1f
                _isProcessing.value = false
            }
        }
    }

    private fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
