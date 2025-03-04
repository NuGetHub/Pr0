package com.pr0gramm.app.ui.views.viewer.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.decoder.DecoderCounters
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import com.google.android.exoplayer2.video.VideoRendererEventListener
import com.pr0gramm.app.Logger
import com.pr0gramm.app.R
import com.pr0gramm.app.io.Cache
import com.pr0gramm.app.ui.views.AspectLayout
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector

/**
 * Stripped down version of [android.widget.VideoView].
 */
class ExoVideoPlayer(context: Context, hasAudio: Boolean, parentView: AspectLayout) :
        RxVideoPlayer(), VideoPlayer, Player.EventListener {

    private val logger = Logger("ExoVideoPlayer")

    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val exo: ExoPlayer
    private val exoVideoRenderer: MediaCodecVideoRenderer
    private var exoAudioRenderer: MediaCodecAudioRenderer? = null

    override var muted: Boolean by observeChange(false) { applyVolumeState() }

    private var uri: Uri? = null
    private var initialized: Boolean = false

    private val backendViewCallbacks = object : TextureViewBackend.Callbacks {
        override fun onAvailable(backend: TextureViewBackend) {
            sendSetSurfaceMessage(true, backend.currentSurface)
        }

        override fun onDestroy(backend: TextureViewBackend) {
            sendSetSurfaceMessage(true, null)
        }
    }

    private val surfaceProvider: TextureViewBackend = TextureViewBackend(context, backendViewCallbacks)

    init {
        val videoView = surfaceProvider.view
        parentView.addView(videoView)

        logger.debug { "Create ExoPlayer instance" }

        val videoListener = VideoListener(callbacks, parentView)

        // default values
        val enableDecoderFallback = true
        val playClearSamplesWithoutKey = false
        val drmSessionManager = null
        val allowedJoiningTimeMs = 5000L

        // start with the video renderer
        exoVideoRenderer = MediaCodecVideoRenderer(context, MediaCodecSelector.DEFAULT,
                allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKey,
                enableDecoderFallback, handler, videoListener, -1)

        val renderers = mutableListOf<Renderer>(exoVideoRenderer)

        if (hasAudio) {
            val renderer = MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT)
            exoAudioRenderer = renderer
            renderers += renderer
        }

        exo = ExoPlayerFactory.newInstance(context, renderers.toTypedArray(), DefaultTrackSelector())
        exo.addListener(this)

        videoView.addOnDetachListener {
            detaches.onNext(Unit)

            pause()

            logger.debug { "Detaching view, releasing exo player now." }
            exo.removeListener(this)
            exo.release()
        }
    }

    override fun open(uri: Uri) {
        logger.debug { "Opening exo player for uri $uri" }
        this.uri = uri
    }

    override fun start() {
        val uri = uri
        if (initialized || uri == null)
            return

        initialized = true

        val extractorsFactory = ExtractorsFactory { arrayOf(FragmentedMp4Extractor(), Mp4Extractor()) }

        val mediaSource = ProgressiveMediaSource
                .Factory(DataSourceFactory(context), extractorsFactory)
                .createMediaSource(uri)

        // apply volume before starting the player
        applyVolumeState()

        logger.info { "Preparing exo player for $uri now'" }

        exo.prepare(mediaSource, false, false)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.playWhenReady = true

        applyVolumeState()

        // initialize the renderer with a surface, if we already have one.
        // this might be the case, if we are restarting the video after
        // a call to pause.
        surfaceProvider.currentSurface?.let { surface ->
            sendSetSurfaceMessage(true, surface)
        }
    }

    override val progress: Float
        get() {
            val duration = exo.duration.toFloat()
            return if (duration > 0) exo.currentPosition / duration else -1f
        }

    override val buffered: Float
        get() {
            var buffered = exo.bufferedPercentage / 100f
            if (buffered == 0f) {
                buffered = -1f
            }

            return buffered
        }

    override val currentPosition: Int
        get() {
            return exo.currentPosition.toInt()
        }

    override val duration: Int
        get() {
            return exo.duration.toInt()
        }

    override fun pause() {
        logger.debug { "Stopping exo player now" }
        sendSetSurfaceMessage(false, null)
        exo.stop()
        initialized = false
    }

    internal fun sendSetSurfaceMessage(async: Boolean, surface: Surface?) {
        val message = exo.createMessage(exoVideoRenderer)
                .setType(C.MSG_SET_SURFACE)
                .setPayload(surface)
                .send()

        if (!async) {
            message.blockUntilDelivered()
        }
    }

    override fun rewind() {
        logger.debug { "Rewinding playback to the start." }
        exo.seekTo(0)
    }

    override fun seekTo(position: Int) {
        logger.debug { "Seeking to position $position" }
        exo.seekTo(position.toLong())
    }

    private fun applyVolumeState() {
        exoAudioRenderer?.let { exoAudioRenderer ->
            val volume = if (this.muted) 0f else 1f
            logger.debug { "Setting volume on exo player to $volume" }

            exo.createMessage(exoAudioRenderer)
                    .setType(C.MSG_SET_VOLUME)
                    .setPayload(volume)
                    .send()
        }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        logger.debug { "onLoadingChanged: $isLoading" }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> callbacks.onVideoBufferingStarts()

            Player.STATE_READY -> {
                // better re-apply volume state
                applyVolumeState()

                if (playWhenReady) {
                    callbacks.onVideoRenderingStarts()
                } else {
                    callbacks.onVideoBufferingEnds()
                }
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
        logger.debug { "Timeline has changed" }
    }

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        logger.debug { "Tracks have changed, ${trackGroups.length} tracks available" }
    }

    override fun onRepeatModeChanged(p0: Int) {
        logger.debug { "Repeat mode has changed to $p0" }
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        val rootCause = error.rootCause

        val messageChain = error.getMessageWithCauses()
        when {
            error.type == ExoPlaybackException.TYPE_SOURCE -> {
                val message = context.getString(R.string.media_exo_error_io, rootCause.message)
                callbacks.onVideoError(message, VideoPlayer.ErrorKind.NETWORK)
            }

            messageChain.contains("Top bit not zero:") -> {
                val message = context.getString(R.string.media_exo_error_topbit)
                callbacks.onVideoError(message, VideoPlayer.ErrorKind.NETWORK)
            }

            else -> {
                AndroidUtility.logToCrashlytics(rootCause)
                callbacks.onVideoError(messageChain, VideoPlayer.ErrorKind.UNKNOWN)
            }
        }

        // try to reset the player
        pause()
    }

    override fun onPositionDiscontinuity(reason: Int) {
    }

    override fun onSeekProcessed() {
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
    }

    override fun onPlaybackParametersChanged(params: PlaybackParameters?) {
        logger.debug { "Playback parameters are now: $params" }
    }

    private class VideoListener(callbacks: VideoPlayer.Callbacks, parentView: AspectLayout) : VideoRendererEventListener {
        private val logger = Logger("ExoVideoPlayer.Listener")

        private val callbacks by weakref(callbacks)
        private val parentView by weakref(parentView)

        override fun onVideoEnabled(counters: DecoderCounters) {}
        override fun onVideoDisabled(counters: DecoderCounters) {}

        override fun onVideoDecoderInitialized(decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            logger.debug { "Initialized decoder $decoderName after ${initializationDurationMs}ms" }
        }

        override fun onVideoInputFormatChanged(format: Format) {
            logger.debug { "Video format is now $format" }
        }

        override fun onDroppedFrames(count: Int, elapsed: Long) {
        }

        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            if (width > 0 && height > 0) {
                val scaledWidth = ((width * pixelWidthHeightRatio) + 0.5f).toInt()

                logger.debug { "Got video track with size ${scaledWidth}x$height" }

                this.parentView?.aspect = scaledWidth.toFloat() / height
                this.callbacks?.onVideoSizeChanged(scaledWidth, height)
            }
        }

        override fun onRenderedFirstFrame(surface: Surface?) {
            this.callbacks?.onVideoRenderingStarts()
        }
    }

    private class DataSourceFactory(private val context: Context) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val cache = context.injector.instance<Cache>()
            return InputStreamCacheDataSource(cache)
        }
    }
}