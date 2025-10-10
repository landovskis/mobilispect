package com.mobilispect.backend.transit_land

import kotlinx.serialization.Serializable

@Serializable
internal class Meta(val after: Int, val next: String? = null)
