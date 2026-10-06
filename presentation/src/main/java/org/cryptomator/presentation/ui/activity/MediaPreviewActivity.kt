package org.cryptomator.presentation.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityMediaPreviewBinding
import org.cryptomator.presentation.intent.MediaPreviewIntent
import org.cryptomator.presentation.presenter.MediaPreviewPresenter
import org.cryptomator.presentation.ui.activity.view.MediaPreviewView
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import org.cryptomator.presentation.util.RandomAccessDataSource
import javax.inject.Inject
import timber.log.Timber

/** In-app video and audio player over a streamed source or the decrypted copy. */
// seek increments and controller visibility are still marked unstable in Media3 1.4
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Activity
class MediaPreviewActivity : BaseActivity<ActivityMediaPreviewBinding>(ActivityMediaPreviewBinding::inflate), MediaPreviewView {

	@Inject
	lateinit var presenter: MediaPreviewPresenter

	@InjectIntent
	lateinit var mediaPreviewIntent: MediaPreviewIntent

	private var player: ExoPlayer? = null
	private var sourceFactory: RandomAccessDataSource.Factory? = null
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
		// one file at a time, so there is nothing for "previous" and "next" to do
		binding.playerView.setShowPreviousButton(false)
		binding.playerView.setShowNextButton(false)
		// setting a listener is what makes Media3 show its fullscreen button
		binding.playerView.setFullscreenButtonClickListener { fullscreen -> setFullscreen(fullscreen) }
		// the toolbar comes and goes with the player's own controls
		binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> binding.toolbar.visibility = visibility })
		setupTouches()
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

	private fun startPlayer() {
		if (player != null) {
			return
		}
		val file = mediaPreviewIntent.mediaFile()
		val streamed = mediaPreviewIntent.streamed() == true
		val builder = ExoPlayer.Builder(this) //
			.setSeekBackIncrementMs(SEEK_STEP_MS) //
			.setSeekForwardIncrementMs(SEEK_STEP_MS)
		if (streamed) {
			// opened lazily on the player's loading thread
			val factory = RandomAccessDataSource.Factory { presenter.openStream(file) }
			sourceFactory = factory
			builder.setMediaSourceFactory(DefaultMediaSourceFactory(factory))
		}
		val exoPlayer = builder.build()
		exoPlayer.addListener(object : Player.Listener {
			override fun onPlayerError(error: PlaybackException) {
				Timber.tag("MediaPreview").e(error, "Playback failed")
				showError(getString(R.string.screen_media_preview_error))
				finish()
			}
		})
		exoPlayer.setMediaItem(MediaItem.fromUri(if (streamed) presenter.streamUri(file) else presenter.mediaUri(file)))
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
		sourceFactory?.release()
		sourceFactory = null
	}

	/** Landscape with the system bars tucked away; the same button brings everything back. */
	private fun setFullscreen(enabled: Boolean) {
		requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		WindowInsetsControllerCompat(window, binding.root).apply {
			systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
			if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
		}
	}

	// A single tap toggles the controls once the double-tap window has passed; a double tap on the
	// left or right half seeks like the ±10 s buttons and leaves the controls as they are. The
	// detector consumes every touch on the picture, so PlayerView does not toggle on its own as well.
	@SuppressLint("ClickableViewAccessibility")
	private fun setupTouches() {
		val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
			override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
				if (binding.playerView.isControllerFullyVisible) {
					binding.playerView.hideController()
				} else {
					binding.playerView.showController()
				}
				return true
			}

			override fun onDoubleTap(event: MotionEvent): Boolean {
				val forward = event.x > binding.playerView.width / 2f
				player?.let { if (forward) it.seekForward() else it.seekBack() }
				showSeekHint(if (forward) R.string.screen_media_preview_seek_forward else R.string.screen_media_preview_seek_back, forward)
				return true
			}
		})
		binding.playerView.setOnTouchListener { _, event ->
			detector.onTouchEvent(event)
			true
		}
	}

	private fun showSeekHint(textId: Int, forward: Boolean) {
		binding.seekHint.setText(textId)
		// over the half that was tapped and above the row of buttons, so it covers none of them
		binding.seekHint.translationX = binding.playerView.width / 4f * (if (forward) 1 else -1)
		binding.seekHint.translationY = -binding.playerView.height / 5f
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
