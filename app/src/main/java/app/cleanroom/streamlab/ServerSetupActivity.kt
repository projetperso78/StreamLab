package app.cleanroom.streamlab

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.cleanroom.streamlab.services.*

class ServerSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = ServiceRegistry.all.firstOrNull { it.id == intent.getStringExtra(EXTRA_SERVICE_ID) && it.id in ServerEndpoints.CONFIGURABLE }
            ?: run { finish(); return }
        val store = ServerEndpoints(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(24 + bars.left, 24 + bars.top, 24 + bars.right, 24 + bars.bottom); insets
        }
        root.addView(TextView(this).apply { text = getString(R.string.server_config_title, service.name); textSize = 24f })
        root.addView(TextView(this).apply {
            text = getString(R.string.server_config_body)
        })
        root.addView(TextView(this).apply { text = getString(R.string.server_address) })
        val address = EditText(this).apply {
            hint = if (service.id == "jellyfin") "https://mon-serveur.example" else "https://app.plex.tv/desktop/"
            contentDescription = getString(R.string.server_address)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(store.saved(service.id) ?: if (service.id == "plex") service.website.toASCIIString() else "")
        }
        root.addView(address)
        if (service.id == "jellyfin") root.addView(Button(this).apply {
            text = getString(R.string.server_try_demo)
            setOnClickListener { address.setText("https://demo.jellyfin.org/stable/") }
        })
        if (service.id == "jellyfin") root.addView(TextView(this).apply {
            text = getString(R.string.server_demo_note)
        })
        if (service.id == "plex") root.addView(TextView(this).apply {
            text = getString(R.string.server_plex_note)
        })
        root.addView(Button(this).apply {
            text = getString(R.string.server_save_open)
            setOnClickListener {
                runCatching { store.save(service.id, address.text.toString()) }
                    .onFailure { address.error = it.message }
                    .onSuccess {
                        val result = ServiceLauncher(this@ServerSetupActivity).launchServer(service)
                        if (result == LaunchResult.UNAVAILABLE) address.error = getString(R.string.server_player_unavailable)
                        else finish()
                    }
            }
        })
        root.addView(Button(this).apply { text = getString(R.string.server_back); setOnClickListener { finish() } })
        setContentView(root)
    }

    companion object {
        const val EXTRA_SERVICE_ID = "service_id"
    }
}
