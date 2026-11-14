package app.cleanroom.streamlab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.cleanroom.streamlab.data.ProfileEntity as CatalogProfile
import app.cleanroom.streamlab.data.ProfileType

@Composable
fun EditProfileDialog(profile: CatalogProfile, onDismiss: () -> Unit, onSave: (String, String, String, String, String) -> Unit) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var source by remember(profile.id) { mutableStateOf("") }
    var username by remember(profile.id) { mutableStateOf("") }
    var password by remember(profile.id) { mutableStateOf("") }
    var epg by remember(profile.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.profile_edit_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_keep_sensitive))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, singleLine = true)
            if (profile.type != ProfileType.LOCAL_M3U.name && profile.type != ProfileType.LOCAL_MEDIA.name) OutlinedTextField(
                source,
                { source = it },
                label = { Text(stringResource(R.string.field_url_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            )
            if (profile.type == ProfileType.XTREAM.name || profile.type == ProfileType.STALKER.name) {
                OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.field_username_optional)) }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.field_password_optional)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
            OutlinedTextField(
                epg,
                { epg = it },
                label = { Text(stringResource(R.string.field_epg_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            )
        }
    }, confirmButton = { Button(onClick = { onSave(name, source, username, password, epg) }) { Text(stringResource(R.string.field_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.field_cancel)) } })
}
