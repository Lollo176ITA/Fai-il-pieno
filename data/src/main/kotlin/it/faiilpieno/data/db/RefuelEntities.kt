package it.faiilpieno.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import it.faiilpieno.domain.tank.Refuel
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Nessuna chiave esterna verso `stations`: l'import giornaliero MIMIT svuota quella tabella e
 * cancellerebbe i rifornimenti a cascata. [stationId] è solo un riferimento informativo.
 */
@Entity(tableName = "refuels", indices = [Index("at")])
data class RefuelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val liters: Double?,
    val amountCents: Int?,
    val isFull: Boolean,
    val stationId: Long?,
)

fun RefuelEntity.toDomain() = Refuel(id, at, liters, amountCents, isFull, stationId)

@Dao
interface RefuelDao {

    @Query("SELECT * FROM refuels ORDER BY at")
    fun observeAll(): Flow<List<RefuelEntity>>

    @Query("SELECT * FROM refuels ORDER BY at")
    suspend fun all(): List<RefuelEntity>

    @Insert
    suspend fun insert(refuel: RefuelEntity): Long

    @Query("DELETE FROM refuels WHERE id = :id")
    suspend fun delete(id: Long)
}
