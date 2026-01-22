package com.mobilispect.mobile

import android.content.Context
import androidx.room.Room.databaseBuilder
import androidx.room.RoomDatabase
import com.mobilispect.mobile.data.AppDatabase

fun getDatabaseBuilder(ctx: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = ctx.applicationContext
    val dbFile = appContext.getDatabasePath("mobilispect.db")
    return databaseBuilder<AppDatabase>(
        context = appContext, name = dbFile.absolutePath
    )
}
