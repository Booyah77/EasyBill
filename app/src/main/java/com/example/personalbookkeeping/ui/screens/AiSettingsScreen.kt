package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.personalbookkeeping.data.ai.AiException
import com.example.personalbookkeeping.data.ai.AiSettings
import com.example.personalbookkeeping.data.ai.DEFAULT_AI_MODEL
import com.example.personalbookkeeping.data.ai.DEFAULT_AI_URL
import com.example.personalbookkeeping.ui.AiUiState
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun AiSettingsScreen(
    state: AiUiState,
    onSave: (AiSettings, String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var url by rememberSaveable(state.settings?.serviceUrl) { mutableStateOf(state.settings?.serviceUrl ?: DEFAULT_AI_URL) }
    var model by rememberSaveable(state.settings?.model) { mutableStateOf(state.settings?.model ?: DEFAULT_AI_MODEL) }
    // Never put plaintext credentials into the saved instance state.
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val working = state.settingsBusy || state.loadingSettings

    Column(
        modifier = modifier.fillMaxSize().background(LedgerColors.Canvas).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !working) { Icon(Icons.Filled.ArrowBack, contentDescription = "返回") }
            Text("大模型设置", color = LedgerColors.Ink, style = MaterialTheme.typography.headlineSmall)
        }
        Text("默认使用 DeepSeek，也支持 OpenAI Chat Completions 兼容的 HTTPS 服务。服务需支持 JSON 输出。",
            color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = url, onValueChange = { url = it; error = null }, enabled = !working,
            modifier = Modifier.fillMaxWidth(), label = { Text("HTTPS 服务地址") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = { Text("可填基础地址、带 /v1 的地址或完整 /chat/completions 地址") }
        )
        OutlinedTextField(value = model, onValueChange = { model = it; error = null }, enabled = !working,
            modifier = Modifier.fillMaxWidth(), label = { Text("模型名称") }, singleLine = true)
        OutlinedTextField(
            value = key, onValueChange = { key = it; error = null }, enabled = !working,
            modifier = Modifier.fillMaxWidth(), label = { Text("API Key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text(if (state.settings == null) "请填写你在服务商处创建的 API Key" else "留空保留已存密钥；更换服务地址时需重新输入") }
        )
        Text("密钥加密保存在本机，不进入账目导出或同步文件。识别只发送本段文字、分类名称和当前日期。",
            color = LedgerColors.MutedInk, style = MaterialTheme.typography.bodySmall)
        (error ?: state.settingsError?.message)?.let { Text(it, color = LedgerColors.Expense) }
        Button(onClick = {
            try {
                val settings = AiSettings(url.trim(), model.trim())
                if (key.isBlank() && (state.settings == null || settings.completionUrl != state.settings.completionUrl)) {
                    error = "请填写 API Key；更换服务地址时需要重新输入。"
                } else {
                    error = null
                    onSave(settings, key)
                }
            } catch (exception: AiException) {
                error = exception.failure.message
            }
        }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (working) "正在处理…" else "保存配置") }
        if (state.settings != null || state.settingsError != null) {
            OutlinedButton(onClick = { confirmClear = true }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                Text("清除大模型配置")
            }
        }
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清除大模型配置？") },
            text = { Text("将移除本机保存的地址、模型和 API Key。") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
    }
}
