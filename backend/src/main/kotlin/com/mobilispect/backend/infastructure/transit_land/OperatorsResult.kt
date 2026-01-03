package com.mobilispect.backend.infastructure.transit_land

import com.mobilispect.backend.TransitLandOperator

/** The result of fetching operators from Transit.land API with pagination support. */
class OperatorsResult(val operators: Collection<TransitLandOperator>, val after: Int? = null)
