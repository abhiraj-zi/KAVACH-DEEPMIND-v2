package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [IncidentReport::class, EmergencyContact::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kavach_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Prepopulate default emergency contacts for demonstration
                        scope.launch(Dispatchers.IO) {
                            val dao = getDatabase(context, scope).incidentDao()
                            dao.insertContact(EmergencyContact(name = "Teammate SOS", phone = "+15550199", relation = "Kavach Operator"))
                            dao.insertContact(EmergencyContact(name = "Campus Security", phone = "+15550100", relation = "Dispatch"))
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
