package org.cryptomator.presentation.workflow;

import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.generator.BoundCallback;
import org.cryptomator.presentation.intent.AuthenticateCloudIntentBuilder;
import org.cryptomator.presentation.model.mappers.CloudModelMapper;
import org.cryptomator.presentation.presenter.Presenter;

import javax.inject.Inject;

import static org.cryptomator.presentation.intent.Intents.authenticateCloudIntent;

public class AuthenticationExceptionHandler {

	private final CloudModelMapper cloudModelMapper;

	@Inject
	public AuthenticationExceptionHandler(CloudModelMapper cloudModelMapper) {
		this.cloudModelMapper = cloudModelMapper;
	}

	public boolean handleAuthenticationException(Presenter<?> presenter, Throwable e, BoundCallback callback) {
		return handle(presenter, e, callback, false);
	}

	public boolean handleAuthenticationExceptionOfUnsavedConnection(Presenter<?> presenter, Throwable e, BoundCallback callback) {
		return handle(presenter, e, callback, true);
	}

	private boolean handle(Presenter<?> presenter, Throwable e, BoundCallback callback, boolean unsavedConnection) {
		if (e instanceof AuthenticationException) {
			AuthenticationException authenticationException = (AuthenticationException) e;
			AuthenticateCloudIntentBuilder intentBuilder = authenticateCloudIntent() //
					.withCloud(cloudModelMapper.toModel(authenticationException.getCloud())) //
					.withError(authenticationException) //
					.withUnsavedConnection(unsavedConnection);
			if (authenticationException.getRecoveryAction().isPresent()) {
				intentBuilder = intentBuilder.withRecoveryAction(authenticationException.getRecoveryAction().get());
			}
			presenter.requestActivityResult( //
					callback, //
					intentBuilder.build(presenter));
			return true;
		} else {
			return false;
		}
	}

}
