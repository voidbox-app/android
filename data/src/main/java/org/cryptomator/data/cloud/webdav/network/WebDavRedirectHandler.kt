package org.cryptomator.data.cloud.webdav.network

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.ProtocolException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

internal class WebDavRedirectHandler(private val httpClient: OkHttpClient) {

	@Throws(IOException::class)
	fun executeFollowingRedirects(request: Request?, cancelled: (() -> Boolean)? = null): Response {
		var request = request
		var response: Response
		var redirectCount = 0
		do {
			if (redirectCount > MAX_REDIRECT_COUNT) {
				throw ProtocolException("Too many redirects: $redirectCount")
			}
			response = execute(httpClient.newCall(request!!), cancelled)
			request = redirectedRequestFor(response)
			redirectCount++
		} while (request != NO_REDIRECTED_REQUEST)
		return response
	}

	// Cancelling the OkHttp call is the only way to stop waiting for a server that has the whole
	// body but has not answered yet; the data source's own cancel check never runs at that point.
	@Throws(IOException::class)
	private fun execute(call: Call, cancelled: (() -> Boolean)?): Response {
		if (cancelled == null) {
			return call.execute()
		}
		val watch = cancelWatcher.scheduleWithFixedDelay({ if (cancelled()) call.cancel() }, CANCEL_POLL_MS, CANCEL_POLL_MS, TimeUnit.MILLISECONDS)
		try {
			return call.execute()
		} finally {
			watch.cancel(false)
		}
	}

	private fun redirectedRequestFor(response: Response): Request? {
		return when (response.code) {
			300, 301, 302, 303, 307, 308 -> createRedirectedRequest(response)
			else -> NO_REDIRECTED_REQUEST
		}
	}

	private fun createRedirectedRequest(response: Response): Request? {
		val url = redirectUrl(response)
		return if (url === NO_REDIRECT_URL) {
			NO_REDIRECTED_REQUEST
		} else createRedirectedRequest(response, url!!)
	}

	private fun createRedirectedRequest(response: Response, url: HttpUrl): Request {
		val requestBuilder = response.request.newBuilder().url(url)
		if (methodShouldBeChangedToGet(response)) {
			changeMethodToGet(requestBuilder)
		}
		if (!connectionMatches(response.request.url, url)) {
			requestBuilder.removeHeader("Authorization")
		}
		return requestBuilder.build()
	}

	private fun methodShouldBeChangedToGet(response: Response): Boolean {
		return (response.code == 300 //
				|| response.code == 303)
	}

	private fun changeMethodToGet(requestBuilder: Request.Builder) {
		requestBuilder.method("GET", null) //
			.removeHeader("Transfer-Encoding") //
			.removeHeader("Content-Length") //
			.removeHeader("Content-Type")
	}

	private fun connectionMatches(url1: HttpUrl, url2: HttpUrl): Boolean {
		return url1.scheme == url2.scheme && url1.host == url2.host && url1.port == url2.port
	}

	private fun redirectUrl(response: Response): HttpUrl? {
		val location = response.header("Location") ?: return NO_REDIRECT_URL
		return response.request.url.resolve(location)
	}

	companion object {

		private const val CANCEL_POLL_MS = 200L
		private val cancelWatcher = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "webdav-cancel-watch").apply { isDaemon = true } }

		private const val MAX_REDIRECT_COUNT = 20
		private val NO_REDIRECTED_REQUEST: Request? = null
		private val NO_REDIRECT_URL: HttpUrl? = null
	}
}
