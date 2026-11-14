package app.cleanroom.streamlab

import androidx.compose.runtime.remember
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.cleanroom.streamlab.player.PlaybackService
import com.google.android.gms.cast.framework.CastContext

// FragmentActivity: the Cast button opens its device picker as a dialog fragment (crashed on ComponentActivity).
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var pipMode by mutableStateOf(false)
    private var sectionRequest by mutableStateOf("Apps" to 0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StreamLabLocale.wrap(newBase))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { CastContext.getSharedInstance(this) }
            .onFailure { android.util.Log.w("StreamLabCast", "Cast unavailable: ${it.javaClass.simpleName}") }
        // Application context: the activity may be destroyed before release, which unbound twice and threw.
        val future = MediaController.Builder(applicationContext, SessionToken(applicationContext, ComponentName(applicationContext, PlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({ runCatching { controller = future.get() }.onFailure { android.util.Log.w("StreamLabPlayback", "Controller unavailable: ${it.javaClass.simpleName}") } }, androidx.core.content.ContextCompat.getMainExecutor(this))
        val app = application as StreamLabApplication
        KidsMode.load(this)
        sectionRequest = PassengerSectionRequest.initial(intent.getStringExtra(EXTRA_INITIAL_SECTION))
        setContent {
            val direction = if (StreamLabLocale.isRtl(StreamLabLocale.current(this@MainActivity))) LayoutDirection.Rtl else LayoutDirection.Ltr
            var onboarded by remember { mutableStateOf(Onboarding.isDone(this@MainActivity)) }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                if (!onboarded) {
                    OnboardingScreen(onFinish = { target ->
                        sectionRequest = PassengerSectionRequest.next(sectionRequest, target)
                        onboarded = true
                    })
                    return@CompositionLocalProvider
                }
                if (KidsMode.active.value) {
                    KidsModeScreen(app, controller)
                    return@CompositionLocalProvider
                }
                // Android 12+: the system shrinks the video into a small window by itself on Home/app switch.
                val videoOnScreen = PipPolicy.active
                androidx.compose.runtime.LaunchedEffect(videoOnScreen) { updatePipParams(videoOnScreen) }
                StreamLabScreen(app, controller, pipMode, initialSection = sectionRequest.first, sectionRequestId = sectionRequest.second, onPip = { enterPip() })
            }
        }
    }
    fun updateAppLanguage(language: String) {
        intent.putExtra(EXTRA_INITIAL_SECTION, "Settings")
        StreamLabLocale.select(this, language)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sectionRequest = PassengerSectionRequest.next(sectionRequest, intent.getStringExtra(EXTRA_INITIAL_SECTION))
    }

    private fun pipSupported() = packageManager.hasSystemFeature("android.software.picture_in_picture")

    private fun pipParams(auto: Boolean): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(android.util.Rational(16, 9))
        .apply { if (android.os.Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(auto).setSeamlessResizeEnabled(true) }
        .build()

    private fun updatePipParams(videoOnScreen: Boolean) {
        if (pipSupported() && android.os.Build.VERSION.SDK_INT >= 31) runCatching { setPictureInPictureParams(pipParams(videoOnScreen)) }
    }

    private fun enterPip() {
        if (pipSupported()) runCatching { enterPictureInPictureMode(pipParams(PipPolicy.active)) }
    }

    // Android 8 to 11 have no auto-enter: leaving with video on screen asks for the small window here.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT < 31 && PipPolicy.active && controller?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        PipPolicy.inPip = isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    companion object {
        const val EXTRA_INITIAL_SECTION = "initial_section"
    }

    override fun onDestroy() {
        // Closing before the player connection is ready: cancel the pending connection instead of
        // releasing a half-built controller ("Service not registered").
        controllerFuture?.let { MediaController.releaseFuture(it) } ?: controller?.release()
        controllerFuture = null
        controller = null
        super.onDestroy()
    }
}
