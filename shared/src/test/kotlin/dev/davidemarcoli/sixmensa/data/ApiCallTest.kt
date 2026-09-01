package dev.davidemarcoli.sixmensa.data

import dev.davidemarcoli.sixmensa.data.remote.ApiError
import dev.davidemarcoli.sixmensa.data.remote.ApiException
import dev.davidemarcoli.sixmensa.data.remote.apiCall
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The backend answers errors as plain text with `content-type: text/html` on the menu
 * routes, but as JSON on `/history/{restaurant}`. Both bodies were captured live.
 */
class ApiCallTest {

    private fun httpException(code: Int, body: String, contentType: String = "text/html") =
        HttpException(Response.error<Any>(code, body.toResponseBody(contentType.toMediaType())))

    private suspend fun errorFrom(t: Throwable): ApiError {
        val result = apiCall<Unit> { throw t }
        val failure = result.exceptionOrNull()
        assertTrue("expected ApiException, got $failure", failure is ApiException)
        return (failure as ApiException).error
    }

    @Test
    fun `maps the plain-text invalid restaurant body`() = runTest {
        assertEquals(
            ApiError.InvalidRestaurant,
            errorFrom(httpException(400, "Invalid restaurant")),
        )
    }

    @Test
    fun `maps the plain-text menu not found body`() = runTest {
        assertEquals(
            ApiError.MenuNotFound,
            errorFrom(httpException(404, "Menu not found")),
        )
    }

    @Test
    fun `keeps the json error body for the history route`() = runTest {
        val body = """{"error":"Invalid restaurant. Use \"htp\" or \"ht201\"."}"""
        val error = errorFrom(httpException(400, body, "application/json"))

        assertTrue(error is ApiError.Http)
        assertEquals(400, (error as ApiError.Http).code)
        assertTrue(error.body.contains("Invalid restaurant"))
    }

    @Test
    fun `maps other http codes generically`() = runTest {
        val error = errorFrom(httpException(500, "Internal Server Error"))
        assertTrue(error is ApiError.Http)
        assertEquals(500, (error as ApiError.Http).code)
    }

    @Test
    fun `maps io failures to network`() = runTest {
        assertEquals(ApiError.Network, errorFrom(IOException("no route to host")))
    }

    @Test
    fun `maps deserialization failures to malformed`() = runTest {
        val error = errorFrom(SerializationException("unexpected token"))
        assertTrue(error is ApiError.Malformed)
    }

    @Test
    fun `passes successes through`() = runTest {
        val result = apiCall { 42 }
        assertEquals(42, result.getOrNull())
    }
}
