package com.mobilispect.backend.infastructure.transit_land

data class MetroAreaResult(
    val metro_areas: List<MetroAreaResultItem> = emptyList()
)

data class MetroAreaResultItem(
    val onestop_id: String?,
    val name: String?
)
