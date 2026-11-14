package app.cleanroom.streamlab

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.cleanroom.streamlab.core.Redaction
import app.cleanroom.streamlab.core.ShareCode
import app.cleanroom.streamlab.data.CatalogRepository
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object QrImage {
    fun render(text: String, size: Int = 720): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}

/** Share favourite radios and public playlists with a friend by QR code, and import a friend's code. */
@Composable
fun SharePanel(repo: CatalogRepository, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    val nothingText = stringResource(R.string.share_nothing)
    val importedText = stringResource(R.string.share_imported)
    val invalidText = stringResource(R.string.share_invalid)
    val qrLabel = stringResource(R.string.share_qr_label)

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ShareCode.encode(repo.shareBundle()) } }
            .onSuccess { code = it }
            .onFailure { error = nothingText }
    }

    fun import(raw: String) {
        scope.launch {
            runCatching { repo.importShare(ShareCode.decode(raw)) }
                .onSuccess { count -> onStatus(String.format(importedText, count)); pasted = "" }
                .onFailure { onStatus(invalidText + " " + Redaction.diagnostic(it.message)) }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("share-panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.share_intro), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        val current = code
        if (current != null) {
            val bitmap = remember(current) { QrImage.render(current).asImageBitmap() }
            Image(
                bitmap, null,
                Modifier.size(280.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)
                    .semantics { contentDescription = qrLabel }.testTag("share-qr"),
            )
            TextButton(onClick = {
                context.startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, current), null))
            }) { Text(stringResource(R.string.share_as_text)) }
        } else if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.testTag("share-empty"))
        }
        Button(
            onClick = {
                val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                GmsBarcodeScanning.getClient(context, options).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let(::import) }
                    .addOnFailureListener { onStatus(invalidText) }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("share-scan"),
        ) { Text(stringResource(R.string.share_scan)) }
        OutlinedTextField(
            pasted, { pasted = it },
            modifier = Modifier.fillMaxWidth().testTag("share-paste"),
            label = { Text(stringResource(R.string.share_paste)) },
            singleLine = true,
        )
        OutlinedButton(onClick = { import(pasted) }, enabled = pasted.isNotBlank(), modifier = Modifier.testTag("share-import")) {
            Text(stringResource(R.string.share_import))
        }
        Text(stringResource(R.string.share_privacy), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
