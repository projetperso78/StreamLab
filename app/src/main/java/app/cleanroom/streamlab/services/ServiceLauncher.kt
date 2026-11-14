package app.cleanroom.streamlab.services

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import app.cleanroom.streamlab.ServerSetupActivity

enum class LaunchResult { NATIVE, EXTERNAL_BROWSER, STORE, SETUP, UNAVAILABLE }

/**
 * Opens third-party services only through their official means:
 * 1. the publisher's installed Android app;
 * 2. for self-hosted servers (Plex, Jellyfin), the user's own server in the system browser;
 * 3. otherwise the publisher's official Google Play listing so the user can install it.
 *
 * StreamLab never embeds, proxies or plays third-party service content itself.
 */
class ServiceLauncher(
    private val context: Context,
    private val nativeIntentResolver: (String) -> Intent? = { context.packageManager.getLaunchIntentForPackage(it) },
    private val launchDisplayIdProvider: () -> Int? = { ActivityLaunchTarget.displayIdFor(context) },
    private val activityStarter: (Intent, Int?) -> Unit = { intent, displayId ->
        val options = ActivityLaunchTarget.optionsForDisplay(displayId)
        val activity = context as? Activity
        if (activity != null && options != null) activity.startActivity(intent, options.toBundle())
        else context.startActivity(intent)
    },
) {
    private val taskFlags get() = if (context is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK

    fun nativeIntentFor(service: ExternalService): Intent? {
        val packageName = ServiceRegistry.officialPackages[known(service).id] ?: return null
        return nativeIntentResolver(packageName)?.addFlags(taskFlags)
    }

    fun storeIntentFor(service: ExternalService): Intent? {
        val packageName = ServiceRegistry.officialPackages[known(service).id] ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).addFlags(taskFlags)
    }

    fun launch(service: ExternalService): LaunchResult {
        val known = known(service)
        nativeIntentFor(known)?.let { intent ->
            val result = startIntent(intent, LaunchResult.NATIVE)
            if (result != LaunchResult.UNAVAILABLE) return result
        }
        if (known.id in ServerEndpoints.CONFIGURABLE) {
            if (ServerEndpoints(context).saved(known.id) != null) {
                val opened = launchServer(known)
                if (opened != LaunchResult.UNAVAILABLE) return opened
            }
            return startIntent(
                Intent(context, ServerSetupActivity::class.java)
                    .putExtra(ServerSetupActivity.EXTRA_SERVICE_ID, known.id)
                    .addFlags(taskFlags),
                LaunchResult.SETUP,
            )
        }
        storeIntentFor(known)?.let { intent ->
            val result = startIntent(intent, LaunchResult.STORE)
            if (result != LaunchResult.UNAVAILABLE) return result
        }
        return openInBrowser(Uri.parse(known.website.toASCIIString()))
    }

    /** Opens the user's own configured media server in the system browser. */
    fun launchServer(service: ExternalService): LaunchResult {
        val known = known(service)
        return openInBrowser(Uri.parse(ServerEndpoints(context).home(known)))
    }

    private fun openInBrowser(uri: Uri): LaunchResult {
        if (context is Activity) {
            try {
                val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
                tab.intent.data = uri
                val options = ActivityLaunchTarget.optionsForDisplay(launchDisplayIdProvider())
                if (options != null) context.startActivity(tab.intent, options.toBundle())
                else tab.launchUrl(context, uri)
                return LaunchResult.EXTERNAL_BROWSER
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return startIntent(Intent(Intent.ACTION_VIEW, uri).addFlags(taskFlags), LaunchResult.EXTERNAL_BROWSER)
    }

    private fun startIntent(intent: Intent, success: LaunchResult): LaunchResult = try {
        activityStarter(intent, launchDisplayIdProvider())
        success
    } catch (_: ActivityNotFoundException) {
        LaunchResult.UNAVAILABLE
    } catch (_: SecurityException) {
        LaunchResult.UNAVAILABLE
    }

    private fun known(service: ExternalService): ExternalService {
        val known = ServiceRegistry.all.firstOrNull { it.id == service.id && it.website == service.website }
            ?: throw IllegalArgumentException("Unknown service")
        ServiceRegistry.validatedWebsite(known.website.toString())
        return known
    }
}
