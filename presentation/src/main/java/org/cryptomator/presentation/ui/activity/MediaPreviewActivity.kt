package org.cryptomator.presentation.ui.activity

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityMediaPreviewBinding
import org.cryptomator.presentation.intent.MediaPreviewIntent
import org.cryptomator.presentation.presenter.MediaPreviewPresenter
import org.cryptomator.presentation.ui.activity.view.MediaPreviewView
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import javax.inject.Inject
import timber.log.Timber

/** In-app video and audio player: a stock Media3 PlayerView over the decrypted copy, nothing handed to other apps. */
@Activity
class MediaPreviewActivity : BaseActivity<ActivityMediaPreviewBinding>(ActivityMediaPreviewBinding::inflate), MediaPreviewView {

	@Inject
	lateinit var presenter: MediaPreviewPresenter

	@InjectIntent
	lateinit var mediaPreviewIntent: MediaPreviewIntent

	private var player: ExoPlayer? = null
	private var resumePosition = 0L
	private var resumePlayWhenReady = true
	private val hideSeekHint = Runnable { binding.seekHint.animate().alpha(0f).setDuration(200).start() }

	override fun setupView() {
		binding.toolbar.title = mediaPreviewIntent.mediaFile().name
		setSupportActionBar(binding.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_clear)
		binding.toolbar.applySystemBarsPadding(left = true, top = true, right = true)
		window.statusBarColor = ContextCompat.getColor(this, R.color.colorBlack)
		// the toolbar comes and goes with the player's own controls
		binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> binding.toolbar.visibility = visibility })
		setupDoubleTapSeek()
	}

	override fun onStart() {
		super.onStart()
		startPlayer()
	}

	override fun onStop() {
		super.onStop()
		releasePlayer()
	}

	override fun onDestroy() {
		if (isFinishing) {
			presenter.deleteDecryptedCopy(mediaPreviewIntent.mediaFile())
		}
		super.onDestroy()
	}

	override fun onMenuItemSelected(itemId: Int): Boolean = when (itemId) {
		android.R.id.home -> {
			finish()
			true
		}
		else -> super.onMenuItemSelected(itemId)
	}

	// the seek increments are still marked unstable in Media3 1.4
	@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
	private fun startPlayer() {
		if (player != null) {
			return
		}
		val exoPlayer = ExoPlayer.Builder(this) //
			.setSeekBackIncrementMs(SEEK_STEP_MS) //
			.setSeekForwardIncrementMs(SEEK_STEP_MS) //
			.build()
		exoPlayer.addListener(object : Player.Listener {
			override fun onPlayerError(error: PlaybackException) {
				Timber.tag("MediaPreview").e(error, "Playback failed")
				showError(getString(R.string.screen_media_preview_error))
				finish()
			}
		})
		exoPlayer.setMediaItem(MediaItem.fromUri(presenter.mediaUri(mediaPreviewIntent.mediaFile())))
		exoPlayer.seekTo(resumePosition)
		exoPlayer.playWhenReady = resumePlayWhenReady
		exoPlayer.prepare()
		binding.playerView.player = exoPlayer
		player = exoPlayer
	}

	private fun releasePlayer() {
		player?.let {
			resumePosition = it.currentPosition
			resumePlayWhenReady = it.playWhenReady
			binding.playerView.player = null
			it.release()
		}
		player = null
	}

	// Double-tap on the left or right half of the picture seeks like the ±10 s buttons do.
	@SuppressLint("ClickableViewAccessibility")
	private fun setupDoubleTapSeek() {
		val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
			override fun onDoubleTap(event: MotionEvent): Boolean {
				val forward = event.x > binding.playerView.width / 2f
				player?.let { if (forward) it.seekForward() else it.seekBack() }
				showSeekHint(if (forward) R.string.screen_media_preview_seek_forward else R.string.screen_media_preview_seek_back, forward)
				return true
			}
		})
		binding.playerView.setOnTouchListener { view, event ->
			detector.onTouchEvent(event)
			if (event.action == MotionEvent.ACTION_UP) {
				view.performClick()
			}
			false
		}
	}

	private fun showSeekHint(textId: Int, forward: Boolean) {
		binding.seekHint.setText(textId)
		// the hint sits over the half that was tapped, clear of the centre controls
		binding.seekHint.translationX = binding.playerView.width / 4f * (if (forward) 1 else -1)
		binding.seekHint.animate().cancel()
		binding.seekHint.alpha = 1f
		binding.seekHint.visibility = View.VISIBLE
		binding.seekHint.removeCallbacks(hideSeekHint)
		binding.seekHint.postDelayed(hideSeekHint, SEEK_HINT_MS)
	}

	companion object {

		private const val SEEK_STEP_MS = 10_000L
		private const val SEEK_HINT_MS = 700L
	}
}
