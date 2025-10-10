package com.mobilispect.mobile.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mobilispect.mobile.agency.Agency
import com.mobilispect.mobile.agency.AgencyDAO
import com.mobilispect.mobile.route.Route
import com.mobilispect.mobile.route.RouteDAO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    version = 4,
    exportSchema = true,
    entities = [
        Agency::class,
        Route::class,
    ]
)
@TypeConverters(RoomTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun agencyDAO(): AgencyDAO
    abstract fun routeDAO(): RouteDAO
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
) : AppDatabase {
    return builder
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
