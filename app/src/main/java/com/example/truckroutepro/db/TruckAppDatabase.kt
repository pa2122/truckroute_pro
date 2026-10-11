package com.example.truckroutepro.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteLocationEntity::class, SavedRouteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TruckAppDatabase : RoomDatabase() {
    abstract fun favoriteLocationDao(): FavoriteLocationDao
    abstract fun savedRouteDao(): SavedRouteDao

    companion object {
        @Volatile
        private var INSTANCE: TruckAppDatabase? = null

        fun getDatabase(context: Context): TruckAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TruckAppDatabase::class.java,
                    "truck_route_pro_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
