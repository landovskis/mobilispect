package com.mobilispect.mobile.data.cloud

sealed interface APIError
object NetworkError : APIError, Exception()
