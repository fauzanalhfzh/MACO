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
            Analisis dokumen/foto/nota/tabel spreadsheet pengeluaran berikut dengan sangat teliti.
            
            Tugas Anda adalah:
            1. Bongkar transaksi ke tingkat barang / baris detail terkecil (Detail Line Items). JANGAN hanya mengambil nilai Total Akhir atau ringkasannya!
               - Jika gambar adalah Resi/Nota/Kuitansi Belanja Ritel: Ekstrak SETIAP barang/jasa yang dibeli sebagai transaksi terpisah. Berikan nama item dengan contoh format "[NamaToko]: [NamaBarang]". Contoh: "Indomaret: Susu Ultra", "Alfamart: Sabun Cair".
               - Jika gambar adalah Spreadsheet/Tabel Pencatatan (seperti log pengeluaran Excel/Google Sheets): Ekstrak SETIAP baris transaksi secara terpisah. Ambil nama barang, kategori, nominal, dan tanggal per baris yang bersangkutan secara utuh.
            
            2. Untuk SETIAP item/baris pengeluaran yang ditemukan, buat satu objek transaksi dengan struktur:
               - "item_name": String. Berikan deskripsi detail barang yang ringkas tapi jelas (maksimal 4 kata). Contoh: "Kopi Janji Jiwa", "Ayam Droasting", "Sewa Lapangan Badminton", "GoJek Ride".
               - "amount": Double. Harga nominal riil pengeluaran untuk item/baris tersebut setelah diskon/pajak per baris jika ada. Pastikan angka desimal murni tanpa simbol mata uang ("Rp", dll) atau titik/koma pemisah ribuan.
               - "category": Kategori pengeluaran. Anda WAJIB memetakan barang tersebut ke HANYA salah satu dari kategori berikut:
                 * "Makan" (makanan, minuman, restoran, kafe, kopi, cemilan, bahan masakan)
                 * "Kebutuhan Wajib" (sewa kos, kontrakan, listrik, air, asuransi wajib, pajak)
                 * "Wifi" (internet bulanan, kuota data, pulsa telepon)
                 * "Olahraga" (keanggotaan gym, sewa lapangan, pembelian perlengkapan olahraga)
                 * "Belanja" (belanja harian/grocery, sabun, deterjen, pakaian, kebutuhan rumah tangga non-makanan)
                 * "Service" (bengkel kendaraan, servis AC, reparasi gadget, cuci motor/mobil)
                 * "Transportasi" (bensin/pertalite, tarif tol, parkir, ojek online, tiket pesawat/kereta)
                 * "Hiburan" (nonton bioskop, tempat wisata, langganan streaming/Netflix, game top-up, rekreasional)
               - "date": Tanggal transaksi dalam format YYYY-MM-DD. Jika tanggal baris tercantum (misal di Spreadsheet), gunakan tanggal tersebut. Jika tidak tercantum atau kabur di nota belanja, gunakan tanggal hari ini atau tanggal terdekat yang masuk akal.

            Anda harus mendaftarkan SEMUANYA dalam satu JSON array yang rapi. Jangan dibungkus ```json.
            Contoh output:
            [
              {"item_name": "Indomaret: Susu Ultra", "amount": 18500.0, "category": "Makan", "date": "2026-06-01"},
              {"item_name": "Sewa Lapang Badminton", "amount": 10000.0, "category": "Olahraga", "date": "2026-06-01"},
              {"item_name": "Ayam Droasting", "amount": 50000.0, "category": "Makan", "date": "2026-06-01"},
              {"item_name": "Bayar Kos Jono", "amount": 650000.0, "category": "Kebutuhan Wajib", "date": "2026-06-01"}
            ]
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
