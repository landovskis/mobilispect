package com.mobilispect.backend.util

object TooManyRequests : Throwable() {
  private fun readResolve(): Any = TooManyRequests
}
