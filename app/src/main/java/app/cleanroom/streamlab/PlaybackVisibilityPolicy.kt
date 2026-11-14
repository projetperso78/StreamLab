package app.cleanroom.streamlab

/** Remembers playback intent across a system visibility interruption. */
internal class PlaybackVisibilityPolicy {
    private var resumeWhenVisible = false

    fun onPause(wasPlaying: Boolean) {
        resumeWhenVisible = wasPlaying
    }

    fun onResume(): Boolean = resumeWhenVisible.also { resumeWhenVisible = false }
}
