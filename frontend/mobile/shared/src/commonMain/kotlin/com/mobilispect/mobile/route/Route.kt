package com.mobilispect.mobile.route

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobilispect.mobile.data.cloud.NetworkRoute

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "short_name")
    val shortName: String,

    @ColumnInfo(name = "long_name")
    val longName: String,

    @ColumnInfo(name = "agency_id")
    val agencyID: String,
)

fun NetworkRoute.asEntity(): Route = Route(id, shortName, longName, agencyID)
