package com.mobilispect.mobile.data

import androidx.room.RoomDatabaseConstructor

actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    actual override fun initialize(): AppDatabase = AppDatabase_Impl()
}
