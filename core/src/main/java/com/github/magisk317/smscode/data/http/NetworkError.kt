package com.github.magisk317.smscode.data.http

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

sealed class NetworkError {
    data class Http(val code: Int, val message: String?) : NetworkError()
    data class Io(val message: String?) : NetworkError()
    data class Serialization(val message: String?) : NetworkError()
    data class Unexpected(val message: String?, val throwable: Throwable) : NetworkError()
    data class MultiSource(val primary: NetworkError, val secondary: NetworkError) : NetworkError()
}

fun Throwable.toNetworkError(): NetworkError = when (this) {
    is HttpException -> NetworkError.Http(code(), message())
    is IOException -> NetworkError.Io(message)
    is SerializationException -> NetworkError.Serialization(message)
    else -> NetworkError.Unexpected(message, this)
}
