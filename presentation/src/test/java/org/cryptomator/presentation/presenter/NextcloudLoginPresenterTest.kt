package org.cryptomator.presentation.presenter

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToWebDavUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.NextcloudLoginView
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.PermissionsResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NextcloudLoginPresenterTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withUrl("https://192.168.178.20/remote.php/dav/files/alice").withUsername("alice").withPassword("encryptedSecret").build()
	private val connectToWebDavUseCase = mock<ConnectToWebDavUseCase>()
	private val connectToWebDavLauncher = mock<ConnectToWebDavUseCase.Launcher>()
	private val authenticationExceptionHandler = mock<AuthenticationExceptionHandler>()
	private val exceptionHandlers = mock<ExceptionHandlers>()
	private val view = mock<NextcloudLoginView>()

	private lateinit var inTest: NextcloudLoginPresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(mock())
		whenever(connectToWebDavUseCase.withCloud(any())).thenReturn(connectToWebDavLauncher)
		inTest = NextcloudLoginPresenter(mock<AddOrChangeCloudConnectionUseCase>(), connectToWebDavUseCase, authenticationExceptionHandler, exceptionHandlers)
		inTest.view = view
	}

	@Test
	fun testMissingLocalNetworkPermissionIsRequested() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				connectResultHandler().onError(LocalNetworkPermissionMissingException(cloud))

				activityCompat.verify { ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }
				verify(authenticationExceptionHandler, never()).handleAuthenticationException(any(), any(), any())
				verify(exceptionHandlers, never()).handle(any(), any())
			}
		}
	}

	@Test
	fun testUntrustedCertificateIsHandledAsAnAuthenticationProblem() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)
				val error = WebDavCertificateUntrustedAuthenticationException(cloud, "certificate")

				connectResultHandler().onError(error)

				verify(authenticationExceptionHandler).handleAuthenticationException(any(), eq(error), any())
				activityCompat.verify({ ActivityCompat.requestPermissions(any(), any(), any()) }, never())
			}
		}
	}

	@Test
	fun testDeniedLocalNetworkPermissionDoesntRetryConnecting() {
		inTest.nextcloudWebDavLocalNetworkPermissionResult(PermissionsResult(mock(), false), cloud)

		verify(connectToWebDavUseCase, never()).withCloud(any())
	}

	@Test
	fun testDeniedLocalNetworkPermissionDoesntRestartTheLogin() {
		inTest.nextcloudLocalNetworkPermissionResult(PermissionsResult(mock(), false), "https://192.168.178.20")

		verifyNoInteractions(view)
	}

	// the granted result reconnects, so the handler of the second connect is the one under test
	private fun connectResultHandler(): ResultHandler<Void> {
		inTest.nextcloudWebDavLocalNetworkPermissionResult(PermissionsResult(mock(), true), cloud)
		val handler = argumentCaptor<ResultHandler<Void>>()
		verify(connectToWebDavLauncher).run(handler.capture())
		return handler.firstValue
	}
}
