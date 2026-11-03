package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.sync.ObjectStorageSettings
import com.example.personalbookkeeping.data.sync.S3AddressingStyle

@Composable
fun ObjectStorageSettingsScreen(
    existing: Pair<ObjectStorageSettings, String>?,
    saving: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: (ObjectStorageSettings, String, String, String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = existing?.first
    var endpoint by rememberSaveable(settings?.endpointUrl) { mutableStateOf(settings?.endpointUrl.orEmpty()) }
    var region by rememberSaveable(settings?.region) { mutableStateOf(settings?.region.orEmpty()) }
    var bucket by rememberSaveable(settings?.bucket) { mutableStateOf(settings?.bucket.orEmpty()) }
    var prefix by rememberSaveable(settings?.prefix) { mutableStateOf(settings?.prefix ?: "personal-ledger") }
    var accessKey by rememberSaveable(existing?.second) { mutableStateOf(existing?.second.orEmpty()) }
    var pathStyle by rememberSaveable(settings?.addressingStyle) { mutableStateOf(settings?.addressingStyle != S3AddressingStyle.VIRTUAL_HOST) }
    var secret by rememberSaveable { mutableStateOf("") }
    var session by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("对象存储同步", style = MaterialTheme.typography.headlineSmall)
        Text("仅在你点击测试连接或立即同步时访问远端。请使用私有存储桶和限定前缀权限的凭证。")
        OutlinedTextField(endpoint, { endpoint = it.trim() }, label = { Text("HTTPS Endpoint") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(region, { region = it.trim() }, label = { Text("Region") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(bucket, { bucket = it.trim() }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(prefix, { prefix = it.trim() }, label = { Text("Prefix（可留空）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("寻址方式")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pathStyle = true }) { Text(if (pathStyle) "✓ Path style" else "Path style") }
            OutlinedButton(onClick = { pathStyle = false }) { Text(if (!pathStyle) "✓ Virtual host" else "Virtual host") }
        }
        OutlinedTextField(accessKey, { accessKey = it }, label = { Text("Access key ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(secret, { secret = it }, label = { Text(if (existing == null) "Secret access key" else "Secret access key（留空则保留）") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(session, { session = it }, label = { Text("Session token（可选，留空则保留）") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        (localError ?: errorMessage)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            runCatching {
                ObjectStorageSettings(endpoint.trim(), region.trim(), bucket.trim(),
                    ObjectStorageSettings.normalizePrefix(prefix),
                    if (pathStyle) S3AddressingStyle.PATH else S3AddressingStyle.VIRTUAL_HOST)
            }.onSuccess { onSave(it, accessKey.trim(), secret, session) }
                .onFailure { localError = it.message ?: "请检查配置" }
        }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        if (existing != null) OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("清除对象存储配置") }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清除对象存储配置？") },
        text = { Text("只清除本机连接信息；本机账目和远端修订对象都会保留。") },
        confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
