package it.faiilpieno.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.time.LocalDate

@Database(
    entities = [StationEntity::class, PriceEntity::class, FuelAverageEntity::class, DatasetInfoEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao

    companion object {
        const val NAME = "faiilpieno.db"
    }
}

class Converters {
    @TypeConverter
    fun instantToLong(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun dateToLong(value: LocalDate): Long = value.toEpochDay()

    @TypeConverter
    fun longToDate(value: Long): LocalDate = LocalDate.ofEpochDay(value)
}
