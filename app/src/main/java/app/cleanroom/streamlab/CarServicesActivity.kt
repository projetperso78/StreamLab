package app.cleanroom.streamlab

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.cleanroom.streamlab.services.LaunchResult
import app.cleanroom.streamlab.services.ServiceLauncher

/** Native, passenger-oriented display. Android Auto driver-visible video is not supported. */
class CarServicesActivity : ComponentActivity() {
    private var returnServiceId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(StreamLabLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        returnServiceId = intent.getStringExtra(EXTRA_RETURN_SERVICE_ID)
        if (savedInstanceState == null && packageName.endsWith(".car")) resumeVideoAfterDriving()
        val launcher = ServiceLauncher(this)
        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val language = StreamLabLocale.current(this@CarServicesActivity)
            BackHandler(enabled = showSettings) { showSettings = false }
            CompositionLocalProvider(
                LocalLayoutDirection provides if (StreamLabLocale.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                if (showSettings) {
                    CinemaPassengerSettings(
                        language = StreamLabLocale.selection(this@CarServicesActivity),
                        onSelectLanguage = { StreamLabLocale.select(this@CarServicesActivity, it) },
                        onBack = { showSettings = false },
                    )
                } else {
                    CinemaPassengerHome(
                        onOpenSection = { section ->
                            if (section == "Settings") showSettings = true else openSection(section)
                        },
                        onOpenService = { service ->
                            if (returnServiceId == service.id && service.id != "netflix" && !packageName.endsWith(".car")) {
                                finish()
                            } else if (launcher.launch(service) == LaunchResult.UNAVAILABLE) {
                                Toast.makeText(
                                    this@CarServicesActivity,
                                    getString(R.string.pass_service_unavailable, service.name),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun openSection(section: String) {
        val next = if (packageName.endsWith(".car") && section == "Live") {
            Intent().setClassName(packageName, PASSENGER_PLAYER_ACTIVITY)
        } else if (packageName.endsWith(".car") && section == "Radio") {
            Intent().setClassName(packageName, "app.cleanroom.streamlab.PassengerRadioActivity")
        } else {
            Intent().setClassName(MAIN_APP_PACKAGE, MainActivity::class.java.name)
                .putExtra(MainActivity.EXTRA_INITIAL_SECTION, section)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(next)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.pass_main_needed), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Personal car build: Android Auto closes the picture while driving and the channel goes on
     * as sound. Reopened once parked, the app goes straight back to that channel in full screen.
     */
    private fun resumeVideoAfterDriving() {
        val token = androidx.media3.session.SessionToken(this, android.content.ComponentName(MAIN_APP_PACKAGE, "app.cleanroom.streamlab.player.PlaybackService"))
        val future = androidx.media3.session.MediaController.Builder(applicationContext, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            val showsPicture = controller.currentTracks.containsType(androidx.media3.common.C.TRACK_TYPE_VIDEO)
            val playing = controller.isPlaying || controller.playWhenReady
            androidx.media3.session.MediaController.releaseFuture(future)
            if (playing && showsPicture && !isFinishing && !isDestroyed) {
                runCatching {
                    startActivity(Intent().setClassName(packageName, PASSENGER_PLAYER_ACTIVITY)
                        .putExtra("resume_full_screen", true))
                }
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        returnServiceId = intent.getStringExtra(EXTRA_RETURN_SERVICE_ID)
    }

    companion object {
        const val EXTRA_RETURN_SERVICE_ID = "return_service_id"
        const val MAIN_APP_PACKAGE = "app.cleanroom.streamlab"
        const val PASSENGER_PLAYER_ACTIVITY = "app.cleanroom.streamlab.PassengerPlayerActivity"
    }
}
