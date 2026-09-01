package dev.davidemarcoli.sixmensa.data.remote

import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/**
 * The backend reports errors in two different shapes for the same conceptual problem:
 * `GET /foo/-1` answers `400` with the **plain text** body `Invalid restaurant`
 * (`content-type: text/html`), while `GET /history/foo` answers `400` with **JSON**
 * `{"error": "..."}`. Retrofit throws before the converter runs on non-2xx, so the raw
 * body survives in either case.
 */
sealed interface ApiError {
    data object InvalidRestaurant : ApiError
    data object MenuNotFound : ApiError
    data class Http(val code: Int, val body: String) : ApiError
    data object Network : ApiError
    data class Malformed(val cause: Throwable) : ApiError
}

class ApiException(val error: ApiError) : Exception(error.toString())

suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: HttpException) {
        // errorBody() is single-read — take the string immediately and never pass the
        // ResponseBody around.
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty().trim()
        Result.failure(
            ApiException(
                when {
                    body.equals("Invalid restaurant", ignoreCase = true) -> ApiError.InvalidRestaurant
                    body.equals("Menu not found", ignoreCase = true) -> ApiError.MenuNotFound
                    else -> ApiError.Http(e.code(), body)
                },
            ),
        )
    } catch (e: SerializationException) {
        Result.failure(ApiException(ApiError.Malformed(e)))
    } catch (e: IOException) {
        Result.failure(ApiException(ApiError.Network))
    }
