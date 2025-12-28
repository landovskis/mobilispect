package com.mobilispect.backend.util

import org.springframework.web.reactive.function.client.WebClientRequestException

class NetworkError(e: WebClientRequestException) : Throwable(e) {}
