package com.newdreamdata.upiledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun appToString(a: PayApp): String = a.name
    @TypeConverter fun stringToApp(s: String): PayApp = runCatching { PayApp.valueOf(s) }.getOrDefault(PayApp.OTHER)
    @TypeConverter fun sourceToString(s: Source): String = s.name
    @TypeConverter fun stringToSource(s: String): Source = runCatching { Source.valueOf(s) }.getOrDefault(Source.MANUAL)
}

@Database(entities = [Expense::class, Mapping::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenses(): ExpenseDao
    abstract fun mappings(): MappingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "upi_ledger.db")
                .build()
                .also { instance = it }
        }
    }
}
