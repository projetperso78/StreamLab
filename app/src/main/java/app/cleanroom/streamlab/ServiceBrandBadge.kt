package app.cleanroom.streamlab

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/**
 * Service tiles show the publisher's own app icon only when that app is installed on the device
 * (read from PackageManager at runtime). StreamLab ships no third-party logos.
 */
internal object ServiceBrandCatalog {
    val packageNames = app.cleanroom.streamlab.services.ServiceRegistry.officialPackages
}

@Composable
internal fun ServiceBrandBadge(serviceId: String, tint: Color, label: String = "") {
    val context = LocalContext.current
    val installedIcon = remember(context, serviceId) {
        val packageName = ServiceBrandCatalog.packageNames[serviceId]
        try {
            packageName?.let {
                val drawable = context.packageManager.getApplicationIcon(it)
                Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
                    drawable.setBounds(0, 0, 96, 96)
                    drawable.draw(AndroidCanvas(bitmap))
                }.asImageBitmap()
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    Surface(
        modifier = Modifier.size(50.dp).testTag("service-brand-$serviceId"),
        color = if (installedIcon == null) Color(0xFF10171F) else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = .48f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (installedIcon != null) {
                Image(installedIcon, null, Modifier.size(50.dp), contentScale = ContentScale.Fit)
            } else {
                val initial = label.trim().firstOrNull()?.uppercaseChar()
                if (initial != null) {
                    Text(initial.toString(), color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } else {
                    Canvas(Modifier.size(24.dp)) {
                        val play = Path().apply {
                            moveTo(size.width * .18f, size.height * .08f)
                            lineTo(size.width * .18f, size.height * .92f)
                            lineTo(size.width * .87f, size.height * .50f)
                            close()
                        }
                        drawPath(play, tint)
                    }
                }
            }
        }
    }
}
