package app.cleanroom.streamlab

import android.app.Application
import app.cleanroom.streamlab.core.Network
import app.cleanroom.streamlab.data.CatalogRepository
import app.cleanroom.streamlab.data.Preferences
import app.cleanroom.streamlab.data.Secrets
import app.cleanroom.streamlab.data.StreamLabDatabase
import app.cleanroom.streamlab.services.RadioBrowserDirectory
import app.cleanroom.streamlab.services.RadioDirectoryGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StreamLabApplication : Application() {
    val database by lazy { StreamLabDatabase.open(this) }
    val secrets by lazy { Secrets() }
    val network by lazy { Network() }
    val preferences by lazy { Preferences(this) }
    val repository by lazy { CatalogRepository(this, database, secrets, network, preferences) }
    private val defaultRadioDirectory by lazy { RadioBrowserDirectory(network) }
    @Volatile internal var radioDirectoryOverride: RadioDirectoryGateway? = null
    val radioDirectory: RadioDirectoryGateway get() = radioDirectoryOverride ?: defaultRadioDirectory
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { repository.removeRetiredBundledProfile() }
            runCatching { repository.loadHomeServers() }
            runCatching { repository.backfillChannelOrigins() } }
    }
}
