package org.cryptomator.presentation.ui.activity

import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.intent.TextEditorIntent
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.presenter.EditorPosition
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.presenter.TextEditorRetainedState
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.ui.dialog.UnsavedChangesDialog
import org.cryptomator.presentation.ui.fragment.TextEditorFragment
import org.cryptomator.presentation.ui.fragment.TextScreen
import org.cryptomator.presentation.ui.fragment.TextViewerFragment
import javax.inject.Inject

@Activity
class TextEditorActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate),
	TextEditorView,
	UnsavedChangesDialog.Callback,
	SearchView.OnQueryTextListener {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	@Inject
	lateinit var licenseEnforcer: LicenseEnforcer

	@InjectIntent
	lateinit var textEditorIntent: TextEditorIntent

	private val retainedState: TextEditorRetainedState by viewModels()

	private fun hasWriteAccess(): Boolean {
		return licenseEnforcer.hasWriteAccess() || textEditorIntent.hubWriteAllowed() == true
	}

	override val textFileContent: String
		get() = textEditorFragment()?.textFileContent ?: ""

	override fun allVaultsLocked(): Boolean = (application as CryptomatorApp).allVaultsLocked()

	override fun setupView() {
		textEditorPresenter.setTextFile(textEditorIntent.textFile())
		textEditorPresenter.setRetainedState(retainedState)
		setupToolbar()
		setupBackPressedCallback()
	}

	override fun createFragment(): Fragment = TextEditorFragment()

	private val backPressedCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			if (!hasWriteAccess() || textEditorPresenter.isReadOnlyText) {
				performBackPressed()
				return
			}
			textEditorPresenter.onBackPressed()
		}
	}

	private fun setupBackPressedCallback() {
		onBackPressedDispatcher.addCallback(this, backPressedCallback)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		super.onCreateOptionsMenu(menu)

		menu.findItem(R.id.action_search)
			.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
				override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
					menu.findItem(R.id.action_search_previous).isVisible = true
					menu.findItem(R.id.action_search_next).isVisible = true
					return true
				}

				override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
					invalidateOptionsMenu()
					return true
				}
			})
		return true
	}

	override fun getCustomMenuResource(): Int = R.menu.menu_text_editor

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		R.id.action_save_changes -> {
			textEditorPresenter.saveChanges()
			true
		}
		R.id.action_search_previous -> {
			textScreen()?.onPreviousQuery()
			true
		}
		R.id.action_search_next -> {
			textScreen()?.onNextQuery()
			true
		}
		else -> {
			super.onMenuItemSelected(itemId)
		}
	}

	override fun onQueryTextSubmit(query: String): Boolean {
		textScreen()?.onQueryText(query)
		return true
	}

	override fun onQueryTextChange(query: String): Boolean {
		if (sharedPreferencesHandler.useLiveSearch()) {
			textScreen()?.onQueryText(query)
		}

		return true
	}

	override fun onPrepareOptionsMenu(menu: Menu): Boolean {
		val searchView = menu.findItem(R.id.action_search).actionView as SearchView
		searchView.setOnQueryTextListener(this)

		menu.findItem(R.id.action_save_changes).isVisible = hasWriteAccess() && !textEditorPresenter.isReadOnlyText

		return super.onPrepareOptionsMenu(menu)
	}

	private fun setupToolbar() {
		binding.mtToolbar.toolbar.title = textEditorIntent.textFile().name
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun performBackPressed() {
		performDefaultBackPressed(backPressedCallback)
	}

	override fun showUnsavedChangesDialog() {
		UnsavedChangesDialog.withContext(this).show()
	}

	override fun displayTextFileContent(textFileContent: CharSequence) {
		val fragment = textEditorFragment() ?: return showEditorFragmentWhichLoadsTheKeptText()
		fragment.displayTextFileContent(textFileContent)
		if (!hasWriteAccess()) {
			fragment.setReadOnly()
		}
	}

	private fun showEditorFragmentWhichLoadsTheKeptText() {
		replaceFragment(TextEditorFragment(), FragmentAnimation.NAVIGATE_IN_TO_FOLDER, addToBackStack = false)
	}

	override fun restoreEditorPosition(position: EditorPosition) {
		textEditorFragment()?.restoreEditorPosition(position)
	}

	override fun showLoadingProgress(progress: ProgressModel) {
		textScreen()?.showLoadingProgress(progress)
	}

	override fun hideLoadingProgress() {
		textScreen()?.hideLoadingProgress()
	}

	override fun showReadOnlyText() {
		invalidateOptionsMenu()
		val viewer = getCurrentFragment(R.id.fragment_container) as? TextViewerFragment
		if (viewer != null) {
			viewer.showPages()
		} else {
			replaceFragment(TextViewerFragment(), FragmentAnimation.NAVIGATE_IN_TO_FOLDER, addToBackStack = false)
		}
	}

	override fun onSaveChangesClicked() {
		textEditorPresenter.saveChanges()
	}

	override fun onDiscardChangesClicked() {
		performBackPressed()
	}

	override fun vaultExpectedToBeUnlocked() {
		finish()
	}

	private fun textEditorFragment(): TextEditorFragment? = getCurrentFragment(R.id.fragment_container) as? TextEditorFragment

	private fun textScreen(): TextScreen? = getCurrentFragment(R.id.fragment_container) as? TextScreen
}
