package org.cryptomator.presentation.ui.activity

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityNextcloudLoginBinding
import org.cryptomator.presentation.presenter.NextcloudLoginPresenter
import org.cryptomator.presentation.ui.activity.view.NextcloudLoginView
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import androidx.core.widget.doAfterTextChanged
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Activity
class NextcloudLoginActivity : BaseActivity<ActivityNextcloudLoginBinding>(ActivityNextcloudLoginBinding::inflate), NextcloudLoginView {

	@Inject
	lateinit var presenter: NextcloudLoginPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_nextcloud_login_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		binding.signInButton.setOnClickListener { signIn() }
		binding.cancelButton.setOnClickListener { presenter.cancelLogin() }
		binding.serverEditText.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				signIn()
			}
			false
		}
		binding.serverEditText.doAfterTextChanged { text ->
			binding.signInButton.isEnabled = text?.toString()?.trim()?.toHttpUrlOrNull() != null
		}
		binding.content.applySystemBarsPadding(bottom = true)
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	private fun signIn() {
		(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.serverEditText.windowToken, 0)
		presenter.startLogin(binding.serverEditText.text.toString())
	}

	override fun showWaitingForApproval() {
		binding.waitingGroup.visibility = View.VISIBLE
		binding.signInButton.isEnabled = false
		binding.serverEditText.isEnabled = false
	}

	override fun showIdle() {
		binding.waitingGroup.visibility = View.GONE
		binding.signInButton.isEnabled = true
		binding.serverEditText.isEnabled = true
	}

}
