package com.mdskun.forcehabits.data.db

import android.content.Context
import androidx.room.*
import com.mdskun.forcehabits.data.model.Habit
import com.mdskun.forcehabits.data.model.HabitType
import com.mdskun.forcehabits.data.model.ProofType
import com.mdskun.forcehabits.data.model.ScheduleFrequency
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habit_logs")
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val completedDate: String,
    val completedAt: Long = System.currentTimeMillis(),
    val sessionIndex: Int = 0,
    val proofPhotoPath: String = "",
    val proofNote: String = ""
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY reminderHour, reminderMinute")
    fun getAllActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Long): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY completedAt DESC")
    fun getLogsForHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND completedDate = :date ORDER BY completedAt ASC")
    suspend fun getLogsForDate(habitId: Long, date: String): List<HabitLog>

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND completedDate = :date")
    suspend fun countLogsForDate(habitId: Long, date: String): Int

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND completedDate = :date LIMIT 1")
    suspend fun getLogForDate(habitId: Long, date: String): HabitLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLog): Long
}

@TypeConverters(Converters::class)
@Database(
    entities = [Habit::class, HabitLog::class],
    version = 1,           // Reset to 1 — fallbackToDestructiveMigration handles any old DB
    exportSchema = false
)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var INSTANCE: HabitDatabase? = null

        fun getInstance(context: Context): HabitDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitDatabase::class.java,
                    "habit_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter fun fromHabitType(v: HabitType): String = v.name
    @TypeConverter fun toHabitType(v: String): HabitType =
        runCatching { HabitType.valueOf(v) }.getOrDefault(HabitType.CUSTOM)

    @TypeConverter fun fromProofType(v: ProofType): String = v.name
    @TypeConverter fun toProofType(v: String): ProofType =
        runCatching { ProofType.valueOf(v) }.getOrDefault(ProofType.NONE)

    @TypeConverter fun fromFrequency(v: ScheduleFrequency): String = v.name
    @TypeConverter fun toFrequency(v: String): ScheduleFrequency =
        runCatching { ScheduleFrequency.valueOf(v) }.getOrDefault(ScheduleFrequency.DAILY)
}
