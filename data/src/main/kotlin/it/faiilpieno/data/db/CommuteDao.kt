package it.faiilpieno.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface CommuteDao {

    @Query("SELECT * FROM places")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun place(id: Long): PlaceEntity?

    @Insert
    suspend fun insertPlace(place: PlaceEntity): Long

    @Update
    suspend fun updatePlace(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Query("SELECT * FROM commutes ORDER BY id")
    fun observeCommutes(): Flow<List<CommuteEntity>>

    @Query("SELECT * FROM commutes WHERE id = :id")
    suspend fun commute(id: Long): CommuteEntity?

    @Query("SELECT * FROM commutes WHERE fromPlaceId = :placeId OR toPlaceId = :placeId")
    suspend fun commutesUsing(placeId: Long): List<CommuteEntity>

    @Insert
    suspend fun insertCommute(commute: CommuteEntity): Long

    @Query("UPDATE commutes SET fromPlaceId = :fromPlaceId, toPlaceId = :toPlaceId, daysMask = :daysMask, roundTrip = :roundTrip WHERE id = :id")
    suspend fun updateCommuteSettings(id: Long, fromPlaceId: Long, toPlaceId: Long, daysMask: Int, roundTrip: Boolean)

    @Query("UPDATE commutes SET distanceM = :distanceM, durationS = :durationS, polyline = :polyline, computedAt = :computedAt, routeAvoidMask = :avoidMask WHERE id = :id")
    suspend fun updateRoute(id: Long, distanceM: Double, durationS: Double, polyline: String, computedAt: Instant, avoidMask: Int)

    @Query("UPDATE commutes SET distanceM = NULL, durationS = NULL, polyline = NULL, computedAt = NULL WHERE id = :id")
    suspend fun clearRoute(id: Long)

    @Query("DELETE FROM commutes WHERE id = :id")
    suspend fun deleteCommute(id: Long)
}
