package com.example.personalbookkeeping.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.personalbookkeeping.data.sync.GitSyncSettings
import com.example.personalbookkeeping.ui.theme.LedgerColors

@Composable
fun GitSettingsScreen(
    existing: GitSyncSettings?,
    saving: Boolean,
    errorMessage: String? = null,
    onBack: () -> Unit,
    onSave: (GitSyncSettings, String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var remoteUrl by rememberSaveable(existing?.remoteUrl) { mutableStateOf(existing?.remoteUrl.orEmpty()) }
    var username by rememberSaveable(existing?.username) { mutableStateOf(existing?.username.orEmpty()) }
    var branch by rememberSaveable(existing?.branch) { mutableStateOf(existing?.branch ?: "main") }
    var token by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = if (existing == null) "配置 Git 同步" else "Git 同步设置",
                modifier = Modifier.padding(start = 4.dp),
                color = LedgerColors.Ink,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Text(
            text = "填写你自己的 HTTPS Git 远端。未配置时，账目只保存在本机；配置后仍由你手动点击同步。",
            color = LedgerColors.MutedInk,
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = remoteUrl,
            onValueChange = { remoteUrl = it.trim(); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS 仓库地址") },
            placeholder = { Text("https://git.example.com/user/ledger.git") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户名") },
            singleLine = true
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (existing == null) "访问令牌" else "访问令牌（重新输入以保存）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("分支") },
            singleLine = true
        )
        Text(
            text = "凭证会加密保存在本机。建议使用私有仓库，不要把远端地址或令牌写进公开项目。",
            color = LedgerColors.MutedInk,
            style = MaterialTheme.typography.bodySmall
        )
        (error ?: errorMessage)?.let { message ->
            Text(message, color = LedgerColors.Expense, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = {
                runCatching {
                    GitSyncSettings(
                        remoteUrl = remoteUrl.trim(),
                        username = username.trim(),
                        branch = branch.trim()
                    )
                }.onSuccess { settings ->
                    if (token.isBlank()) {
                        error = "请输入访问令牌"
                    } else {
                        onSave(settings, token)
                    }
                }.onFailure { failure ->
                    error = failure.message ?: "请检查 Git 配置"
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "正在保存" else "保存配置")
        }
        if (existing != null) {
            OutlinedButton(
                onClick = { showClearConfirmation = true },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭 Git 同步")
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("关闭 Git 同步？") },
            text = { Text("这会清除本机保存的远端地址和凭证，不会删除本机账目。") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirmation = false
                }) { Text("关闭同步", color = LedgerColors.Expense) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text("取消") }
            }
        )
    }
}
