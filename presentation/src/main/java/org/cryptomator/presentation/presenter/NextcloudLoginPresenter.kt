package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToWebDavUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.ui.activity.view.NextcloudLoginView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.crypto.CredentialCryptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber

/**
 * Signs in to a Nextcloud server with Login Flow v2: the server hands out a login URL that is
 * opened in the browser, the app polls until the user approves, and the server returns an app
 * password. The result is stored as a regular WebDAV cloud pointing at the user's files.
 */
@PerView
class NextcloudLoginPresenter @Inject internal constructor( //
	private val addOrChangeCloudConnectionUseCase: AddOrChangeCloudConnectionUseCase,  //
	private val connectToWebDavUseCase: ConnectToWebDavUseCase,  //
	private val authenticationExceptionHandler: AuthenticationExceptionHandler,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<NextcloudLoginView>(exceptionMappings) {

	private val http = OkHttpClient.Builder() //
		.connectTimeout(15, TimeUnit.SECONDS) //
		.readTimeout(30, TimeUnit.SECONDS) //
		.build()

	private var flow: Disposable? = null

	private class LoginFlow(val loginUrl: String, val pollEndpoint: String, val pollToken: String)

	private class Credentials(val server: String, val loginName: String, val appPassword: String)

	fun startLogin(serverInput: String) {
		val server = normalizeServer(serverInput)
		if (server == null) {
			Toast.makeText(context(), R.string.screen_nextcloud_login_msg_server_invalid, Toast.LENGTH_SHORT).show()
			return
		}
		flow?.dispose()
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		flow = Single.fromCallable { initiate(server) } //
			.subscribeOn(Schedulers.io()) //
			.observeOn(AndroidSchedulers.mainThread()) //
			.subscribe({ loginFlow ->
				view?.showProgress(ProgressModel.COMPLETED)
				view?.showWaitingForApproval()
				startIntent(Intent(Intent.ACTION_VIEW, Uri.parse(loginFlow.loginUrl)))
				waitForApproval(loginFlow)
			}, { e ->
				view?.showProgress(ProgressModel.COMPLETED)
				view?.showIdle()
				Timber.tag("NextcloudLogin").e(e, "Login flow could not be started")
				view?.showError(R.string.screen_nextcloud_login_msg_server_unreachable)
			})
	}

	private fun waitForApproval(loginFlow: LoginFlow) {
		flow = Single.fromCallable { pollUntilApproved(loginFlow) } //
			.subscribeOn(Schedulers.io()) //
			.observeOn(AndroidSchedulers.mainThread()) //
			.subscribe({ credentials ->
				authenticate(toCloud(credentials))
			}, { e ->
				view?.showIdle()
				Timber.tag("NextcloudLogin").e(e, "Login flow did not complete")
				view?.showError(R.string.screen_nextcloud_login_msg_timeout)
			})
	}

	fun cancelLogin() {
		flow?.dispose()
		flow = null
		view?.showIdle()
	}

	private fun normalizeServer(input: String): String? {
		var server = input.trim().trimEnd('/')
		if (server.isEmpty()) {
			return null
		}
		if (!server.startsWith("http://") && !server.startsWith("https://")) {
			server = "https://$server"
		}
		server = server.removeSuffix("/index.php").trimEnd('/')
		return if (server.toHttpUrlOrNull() != null) server else null
	}

	private fun initiate(server: String): LoginFlow {
		val request = Request.Builder() //
			.url("$server/index.php/login/v2") //
			.header("User-Agent", USER_AGENT) //
			.post(ByteArray(0).toRequestBody(null)) //
			.build()
		http.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				throw IOException("Login flow request failed with HTTP ${response.code}")
			}
			val json = JSONObject(response.body?.string() ?: throw IOException("Empty login flow response"))
			val poll = json.getJSONObject("poll")
			return LoginFlow(json.getString("login"), poll.getString("endpoint"), poll.getString("token"))
		}
	}

	private fun pollUntilApproved(loginFlow: LoginFlow): Credentials {
		val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
		var lastFailure: String? = null
		while (System.currentTimeMillis() < deadline) {
			var delay = POLL_INTERVAL_MS
			try {
				val request = Request.Builder() //
					.url(loginFlow.pollEndpoint) //
					.header("User-Agent", USER_AGENT) //
					.post(FormBody.Builder().add("token", loginFlow.pollToken).build()) //
					.build()
				http.newCall(request).execute().use { response ->
					if (response.isSuccessful) {
						return parseCredentials(response.body?.string() ?: throw IOException("Empty poll response"))
					}
					// 404 means "not approved yet". Anything else is not final either: Nextcloud throttles
					// frequent polls (429) and proxies hiccup, so back off and keep polling until the deadline.
					if (response.code != 404) {
						lastFailure = "HTTP ${response.code}"
						delay = response.header("Retry-After")?.toLongOrNull()?.times(1000L) ?: POLL_BACKOFF_MS
						Timber.tag("NextcloudLogin").w("Poll answered HTTP %d, retrying in %d ms", response.code, delay)
					}
				}
			} catch (e: IOException) {
				lastFailure = e.message
				delay = POLL_BACKOFF_MS
				Timber.tag("NextcloudLogin").w(e, "Poll failed, retrying in %d ms", delay)
			}
			Thread.sleep(delay)
		}
		throw IOException("Login was not approved within the time limit" + (lastFailure?.let { " (last failure: $it)" } ?: ""))
	}

	private fun parseCredentials(body: String): Credentials {
		val json = JSONObject(body)
		return Credentials(json.getString("server").trimEnd('/'), json.getString("loginName"), json.getString("appPassword"))
	}

	private fun toCloud(credentials: Credentials): WebDavCloud {
		val encryptedPassword = CredentialCryptor.getInstance(context()).encrypt(credentials.appPassword)
		return WebDavCloud.aWebDavCloudCloud() //
			.withUrl("${credentials.server}/remote.php/dav/files/${credentials.loginName}") //
			.withUsername(credentials.loginName) //
			.withPassword(encryptedPassword) //
			.build()
	}

	private fun authenticate(cloud: WebDavCloud) {
		view?.showProgress(ProgressModel(ProgressStateModel.AUTHENTICATION))
		connectToWebDavUseCase //
			.withCloud(cloud) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onSuccess(void: Void?) {
					onCloudAuthenticated(cloud)
				}

				override fun onError(e: Throwable) {
					view?.showProgress(ProgressModel.COMPLETED)
					view?.showIdle()
					if (!authenticationExceptionHandler.handleAuthenticationException(this@NextcloudLoginPresenter, e, ActivityResultCallbacks.handledAuthenticationNextcloud())) {
						super.onError(e)
					}
				}
			})
	}

	@Callback
	fun handledAuthenticationNextcloud(result: ActivityResult) {
		if (result.intent().extras?.getBoolean(AuthenticateCloudPresenter.WEBDAV_ACCEPTED_UNTRUSTED_CERTIFICATE, false) == true) {
			authenticate((result.singleResult as CloudModel).toCloud() as WebDavCloud)
		}
	}

	private fun onCloudAuthenticated(cloud: Cloud) {
		addOrChangeCloudConnectionUseCase //
			.withCloud(cloud) //
			.run(DefaultResultHandler())
		finishWithResult(CloudConnectionListPresenter.SELECTED_CLOUD, cloud)
	}

	override fun destroyed() {
		flow?.dispose()
		flow = null
		super.destroyed()
	}

	init {
		unsubscribeOnDestroy(addOrChangeCloudConnectionUseCase, connectToWebDavUseCase)
	}

	companion object {

		private const val USER_AGENT = "Latch"
		private const val POLL_INTERVAL_MS = 3000L
		private const val POLL_BACKOFF_MS = 10000L

		// Nextcloud keeps a login flow token for 20 minutes.
		private const val POLL_TIMEOUT_MS = 20L * 60L * 1000L
	}
}
