package com.mobilispect.backend.infastructure

import kotlinx.serialization.Serializable

@Serializable
internal class PointGeometry(val coordinates: Array<Double>, val type: String)
