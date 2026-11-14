package app.cleanroom.streamlab

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.core.ChannelOrigin
import app.cleanroom.streamlab.services.RegionDetector

/** First-launch flow: language, then straight to the radios of the user's country. */
object Onboarding {
    private const val STORE = "streamlab_onboarding"
    private const val DONE = "done"
    private const val STEP = "step"

    fun isDone(context: Context): Boolean = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getBoolean(DONE, false)
    fun step(context: Context): Int = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt(STEP, 0)
    fun setStep(context: Context, step: Int) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt(STEP, step).commit()
    }
    fun finish(context: Context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean(DONE, true).commit()
    }
}

@Composable
fun OnboardingScreen(onFinish: (section: String) -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(Onboarding.step(context)) }
    val selected = StreamLabLocale.selection(context)
    val uiLocale = LocalConfiguration.current.locales[0]
    fun done(section: String) { Onboarding.finish(context); onFinish(section) }

    MaterialTheme(colorScheme = Cinema.colors) {
        Surface(Modifier.fillMaxSize(), color = Cinema.bg, contentColor = Cinema.white) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp).testTag("onboarding"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("StreamLab", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                if (step == 0) {
                    Text(stringResource(R.string.onboarding_language_title), style = MaterialTheme.typography.headlineSmall)
                    listOf(
                        StreamLabLocale.SYSTEM to stringResource(R.string.language_system),
                        "fr" to "Français", "en" to "English", "ar" to "العربية",
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = selected == code,
                            onClick = {
                                Onboarding.setStep(context, 1)
                                (context as? Activity)?.let { StreamLabLocale.select(it, code) }
                                step = 1
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("onboarding-language-$code"),
                            label = { Text(label, style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                } else {
                    val country = remember(context) { RegionDetector.detect(context) }
                    Text(stringResource(R.string.onboarding_ready_title), style = MaterialTheme.typography.headlineSmall)
                    if (country != null) {
                        Text("${ChannelOrigin.flag(country)} ${ChannelOrigin.countryName(country, uiLocale)}",
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("onboarding-country"))
                    }
                    // Said once, before anything plays: video is for passengers, the driver keeps eyes on the road.
                    PassengerSafetyNotice(Modifier.fillMaxWidth())
                    Button(onClick = { done("RadioNear") }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("onboarding-radio")) {
                        Text(stringResource(R.string.onboarding_listen_radio), style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = { done("Library") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("onboarding-playlist")) {
                        Text(stringResource(R.string.onboarding_add_playlist))
                    }
                    TextButton(onClick = { done("Apps") }, modifier = Modifier.testTag("onboarding-skip")) {
                        Text(stringResource(R.string.onboarding_explore))
                    }
                    Text(stringResource(R.string.onboarding_note), style = MaterialTheme.typography.bodySmall, color = Color(0xFFB9CBC9))
                }
            }
        }
    }
}
