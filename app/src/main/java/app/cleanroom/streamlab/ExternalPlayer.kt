package app.cleanroom.streamlab

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Hands the current stream to another player the user installed (VLC, MX Player, mpv, Just Player…). */
object ExternalPlayer {
    fun open(context: Context, url: String, title: String) {
        val uri = Uri.parse(url)
        val type = if (url.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/*"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
            .putExtra("title", title)
            .apply { if (url.startsWith("content://")) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val chooser = Intent.createChooser(view, context.getString(R.string.external_player_choose)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.external_player_none, Toast.LENGTH_LONG).show()
        }
    }
}
