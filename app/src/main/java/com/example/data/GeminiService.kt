package com.example.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

object GeminiService {
    private const val TAG = "GeminiService"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Retrieve active API Key from BuildConfig or fall back to an override value if set by user
    fun getApiKey(userOverride: String?): String {
        val buildKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
        return if (!userOverride.isNullOrEmpty()) {
            userOverride
        } else if (!buildKey.isNullOrEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            buildKey
        } else {
            ""
        }
    }

    suspend fun analyzeReceipt(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        userApiKey: String? = null
    ): ReceiptAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(userApiKey)
        if (apiKey.isEmpty()) {
            return@withContext ReceiptAnalysisResult.Error("API Key Gemini tidak ditemukan. Silakan masukkan API Key di Pengaturan atau konfigurasi file .env")
        }

        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val prompt = """
            Anda adalah asisten keuangan pribadi cerdas bernama MACO.
            Analisis gambar nota/resi/kuitansi pengeluaran berikut dengan sangat teliti.
            Ekstrak data berikut untuk SETIAP transaksi yang Anda temukan (bisa lebih dari satu transaksi):
            1. "item_name": Nama toko, deskripsi barang utama, atau pekerjaan. Maksimal 3 kata.
            2. "amount": Jumlah pengeluaran total nominal riil dalam angka double/desimal.
            3. "category": Kategori pengeluaran. Anda WAJIB memilih HANYA salah satu: Makan, Kebutuhan Wajib, Wifi, Olahraga, Belanja, Service, Transportasi, Hiburan.
            4. "date": Tanggal transaksi dalam format YYYY-MM-DD. Jika tidak terlihat, gunakan tanggal hari ini.

            Anda harus mengembalikan respon HANYA berupa text format JSON array solid sederhana. Jangan dibungkus ```json.
            Contoh output:
            [{"item_name": "Makan Bakso Jono", "amount": 45000.0, "category": "Makan", "date": "2026-05-28"}]
        """.trimIndent()

        try {
            // Build Gemini request payload structure
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                val inlineData = JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Image)
                                }
                                put("inlineData", inlineData)
                            })
                        }
                        put("parts", partsArray)
                    })
                }
                put("contents", contentsArray)
                
                // Add generationConfig to specify response format
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                Log.d(TAG, "Gemini Raw Response: $bodyString")
                
                if (!response.isSuccessful) {
                    val errMsg = if (bodyString != null) {
                        try {
                            JSONObject(bodyString).getJSONObject("error").getString("message")
                        } catch (e: Exception) {
                            "HTTP Error ${response.code}"
                        }
                    } else {
                        "HTTP Error ${response.code}"
                    }
                    return@withContext ReceiptAnalysisResult.Error("Gagal menghubungi Gemini AI: $errMsg")
                }

                if (bodyString.isNullOrEmpty()) {
                    return@withContext ReceiptAnalysisResult.Error("Respon kosong dari Gemini AI")
                }

                // Parse standard Gemini structure
                val rootJson = JSONObject(bodyString)
                val candidates = rootJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext ReceiptAnalysisResult.Error("Gemini tidak dapat mengekstrak teks pendukung.")
                }
                
                val textResponse = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                Log.d(TAG, "Extracted text payload: $textResponse")
                
                // Parse flat json array out of extracted text
                var cleanResponse = textResponse.trim()
                if (cleanResponse.startsWith("```json")) {
                    cleanResponse = cleanResponse.substring(7)
                } else if (cleanResponse.startsWith("```")) {
                    cleanResponse = cleanResponse.substring(3)
                }
                if (cleanResponse.endsWith("```")) {
                    cleanResponse = cleanResponse.substring(0, cleanResponse.length - 3)
                }
                cleanResponse = cleanResponse.trim()
                
                // fallback if single object returned
                val items = mutableListOf<ReceiptItem>()
                try {
                    val parsedArray = JSONArray(cleanResponse)
                    for (i in 0 until parsedArray.length()) {
                        val parsedObj = parsedArray.getJSONObject(i)
                        val itemName = parsedObj.optString("item_name", "Nota Belanja")
                        val amount = parsedObj.optDouble("amount", 0.0)
                        val category = parsedObj.optString("category", "Belanja")
                        val date = parsedObj.optString("date", "2026-05-28")
                        items.add(ReceiptItem(itemName, amount, category, date))
                    }
                } catch (e: Exception) {
                    val parsedObj = JSONObject(cleanResponse)
                    val itemName = parsedObj.optString("item_name", "Nota Belanja")
                    val amount = parsedObj.optDouble("amount", 0.0)
                    val category = parsedObj.optString("category", "Belanja")
                    val date = parsedObj.optString("date", "2026-05-28")
                    items.add(ReceiptItem(itemName, amount, category, date))
                }
                
                ReceiptAnalysisResult.Success(items)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing receipt analysis", e)
            ReceiptAnalysisResult.Error("Terjadi kesalahan analisis: ${e.localizedMessage}")
        }
    }
}

data class ReceiptItem(
    val itemName: String,
    val amount: Double,
    val category: String,
    val date: String
)

sealed class ReceiptAnalysisResult {
    data class Success(val items: List<ReceiptItem>) : ReceiptAnalysisResult()
    data class Error(val message: String) : ReceiptAnalysisResult()
}
