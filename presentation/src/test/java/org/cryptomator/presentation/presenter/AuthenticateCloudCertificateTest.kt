package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.Intent
import android.util.Base64
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.security.cert.X509Certificate
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthenticateCloudCertificateTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withId(1L).withUrl("https://192.168.178.20/webdav").withUsername("alice").withPassword("encryptedSecret").withCertificate("old certificate").build()
	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
	private val getCloudsUseCase = mock<GetCloudsUseCase>()
	private val getCloudsLauncher = mock<GetCloudsUseCase.Launcher>()
	private var storedClouds = listOf<Cloud>(cloud)
	private val cloudModelMapper = mock<CloudModelMapper>()
	private val activity = mock<Activity>()
	private val view = mock<AuthenticateCloudView>()
	private val intent = mock<AuthenticateCloudIntent>()

	private lateinit var inTest: AuthenticateCloudPresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(activity)
		whenever(view.intent()).thenReturn(intent)
		whenever(cloudModelMapper.toModel(any())).thenReturn(WebDavCloudModel(cloud))
		whenever(addOrChangeCloudConnectionUseCase.withCloud(any())).thenReturn(addOrChangeCloudConnectionLauncher)
		whenever(getCloudsUseCase.withCloudType(any())).thenReturn(getCloudsLauncher)
		doAnswer { it.getArgument<ResultHandler<List<Cloud>>>(0).onSuccess(storedClouds) }.whenever(getCloudsLauncher).run(any())
		inTest = AuthenticateCloudPresenter(
			mock<ExceptionHandlers>(),
			cloudModelMapper,
			addOrChangeCloudConnectionUseCase,
			getCloudsUseCase,
			mock<GetUsernameUseCase>(),
			mock<AddExistingVaultWorkflow>(),
			mock<CreateNewVaultWorkflow>()
		).also { it.view = view }
	}

	@Test
	fun testAcceptedCertificateIsStoredBeforeTheCallerRetries() = withAndroidClasses {
		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		val stored = argumentCaptor<WebDavCloud>()
		verify(addOrChangeCloudConnectionUseCase).withCloud(stored.capture())
		assertEquals(cloud.id(), stored.firstValue.id())
		assertNotEquals(cloud.certificate(), stored.firstValue.certificate())
		verify(activity, never()).setResult(any(), any())

		storeResultHandler().onSuccess(null)

		verify(activity).setResult(eq(Activity.RESULT_OK), any())
		assertEquals("new certificate", certificateIn(returnedCloud()))
	}

	@Test
	fun testOldCopyOfTheConnectionDoesntBringBackAnOldPassword() = withAndroidClasses {
		storedClouds = listOf(WebDavCloud.aCopyOf(cloud).withPassword("changedSecret").build())

		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		val stored = argumentCaptor<WebDavCloud>()
		verify(addOrChangeCloudConnectionUseCase).withCloud(stored.capture())
		assertEquals("changedSecret", stored.firstValue.password())
		assertEquals("new certificate", certificateIn(stored.firstValue))
	}

	@Test
	fun testDeletedConnectionIsNotBroughtBack() = withAndroidClasses {
		storedClouds = emptyList()

		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
		verify(view).finish()
	}

	@Test
	fun testConnectionWithoutACertificateIsNotPinnedToTheAcceptedOne() = withAndroidClasses {
		storedClouds = listOf(WebDavCloud.aCopyOf(cloud).withCertificate(null).withPassword("changedSecret").build())

		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
		verify(activity).setResult(eq(Activity.RESULT_OK), any())
		// the caller retries with the result in this session, so it holds the stored connection plus the accepted certificate
		val returned = returnedCloud()
		assertEquals("new certificate", certificateIn(returned))
		assertEquals("changedSecret", returned.password())
	}

	@Test
	fun testFailedStoreDoesntReportSuccess() = withAndroidClasses {
		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		storeResultHandler().onError(IllegalStateException())

		verify(activity, never()).setResult(eq(Activity.RESULT_OK), any())
	}

	@Test
	fun testCertificateOfAnUnsavedConnectionGoesBackToTheCaller() = withAndroidClasses {
		whenever(intent.unsavedConnection()).thenReturn(true)

		inTest.onAcceptWebDavCertificateClicked(cloud, mock<X509Certificate>())

		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
		verify(activity).setResult(eq(Activity.RESULT_OK), any())
	}

	private fun certificateIn(cloud: WebDavCloud) = cloud.certificate().removePrefix("-----BEGIN CERTIFICATE-----\n").removeSuffix("-----END CERTIFICATE-----")

	private fun withAndroidClasses(test: () -> Unit) {
		Mockito.mockStatic(Base64::class.java).use { base64 ->
			base64.`when`<String> { Base64.encodeToString(anyOrNull(), any()) }.thenReturn("new certificate")
			Mockito.mockConstruction(Intent::class.java).use { test() }
		}
	}

	private fun returnedCloud(): WebDavCloud {
		val returned = argumentCaptor<Cloud>()
		verify(cloudModelMapper).toModel(returned.capture())
		return returned.firstValue as WebDavCloud
	}

	private fun storeResultHandler(): ResultHandler<Void?> {
		val handler = argumentCaptor<ResultHandler<Void?>>()
		verify(addOrChangeCloudConnectionLauncher).run(handler.capture())
		return handler.firstValue
	}
}
