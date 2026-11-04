package org.cryptomator.presentation.workflow

import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException
import org.cryptomator.generator.BoundCallback
import org.cryptomator.presentation.intent.AuthenticateCloudIntentBuilder
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.presenter.Presenter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthenticationExceptionHandlerTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withId(1L).withUrl("https://192.168.178.20/webdav").withUsername("alice").withPassword("encryptedSecret").build()
	private val error = WebDavCertificateUntrustedAuthenticationException(cloud, "certificate")
	private val presenter = mock<Presenter<*>>()
	private val callback = mock<BoundCallback<*, *>>()
	private val intentBuilder = mock<AuthenticateCloudIntentBuilder>(defaultAnswer = Answers.RETURNS_SELF)
	private val cloudModelMapper = mock<CloudModelMapper>()
	private val inTest = AuthenticationExceptionHandler(cloudModelMapper)

	@Test
	fun testConnectionInUseIsNotMarkedAsUnsaved() = withIntents {
		assertTrue(inTest.handleAuthenticationException(presenter, error, callback))

		verify(intentBuilder).withUnsavedConnection(false)
	}

	@Test
	fun testUnsavedConnectionIsMarked() = withIntents {
		assertTrue(inTest.handleAuthenticationExceptionOfUnsavedConnection(presenter, error, callback))

		verify(intentBuilder).withUnsavedConnection(true)
	}

	@Test
	fun testOtherErrorsAreNotHandled() = withIntents {
		assertFalse(inTest.handleAuthenticationExceptionOfUnsavedConnection(presenter, IllegalStateException(), callback))
	}

	private fun withIntents(test: () -> Unit) {
		whenever(cloudModelMapper.toModel(any())).thenReturn(WebDavCloudModel(cloud))
		Mockito.mockStatic(Intents::class.java).use { intents ->
			intents.`when`<AuthenticateCloudIntentBuilder> { Intents.authenticateCloudIntent() }.thenReturn(intentBuilder)
			test()
		}
	}
}
