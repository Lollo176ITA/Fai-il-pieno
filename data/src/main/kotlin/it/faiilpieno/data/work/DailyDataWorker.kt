package it.faiilpieno.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.faiilpieno.data.repository.PriceRepository
import it.faiilpieno.domain.parser.MimitFormatException
import java.io.IOException

/** Scarica e importa i dati MIMIT. Riprova (con backoff) solo per errori di rete. */
@HiltWorker
class DailyDataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PriceRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.refresh(force = inputData.getBoolean(KEY_FORCE, false))
        Result.success()
    } catch (e: IOException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(errorData(ERROR_NETWORK))
    } catch (e: MimitFormatException) {
        Result.failure(errorData(ERROR_FORMAT))
    }

    private fun errorData(code: String) = workDataOf(KEY_ERROR to code)

    companion object {
        const val KEY_FORCE = "force"
        const val KEY_ERROR = "error"
        const val ERROR_NETWORK = "network"
        const val ERROR_FORMAT = "format"
        private const val MAX_ATTEMPTS = 3
    }
}
