package com.mobilispect.common.data.route

import com.mobilispect.mobile.route.Route
import com.mobilispect.mobile.route.RouteRef

interface RouteNetworkDataSource {
    suspend operator fun invoke(routeRef: RouteRef): Result<Route?>
}
