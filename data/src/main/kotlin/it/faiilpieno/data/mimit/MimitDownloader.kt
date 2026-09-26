package it.faiilpieno.data.mimit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject

class MimitDownloader @Inject constructor(private val client: OkHttpClient) {

    /** Scarica [url] in [target] passando da un file temporaneo, così un download interrotto non lascia file a metà. */
    suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} per $url")
            response.body.byteStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    companion object {
        const val STATIONS_URL = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
        const val PRICES_URL = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
        private const val USER_AGENT = "FaiIlPieno/0.1 (Android)"
    }
}
