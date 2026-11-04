package com.example.personalbookkeeping.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.example.personalbookkeeping.data.ai.AiPhase
import com.example.personalbookkeeping.ui.screens.AiBookkeepingScreen
import com.example.personalbookkeeping.ui.screens.AiHistoryScreen
import com.example.personalbookkeeping.ui.screens.AiSettingsScreen
import com.example.personalbookkeeping.LedgerApplication
import com.example.personalbookkeeping.data.transfer.ImportPreview
import com.example.personalbookkeeping.data.transfer.LedgerFileFormat
import com.example.personalbookkeeping.data.transfer.LedgerFileTransfer
import com.example.personalbookkeeping.data.sync.GitConflictChoice
import com.example.personalbookkeeping.data.sync.GitSyncManager
import com.example.personalbookkeeping.data.sync.GitSyncResult
import com.example.personalbookkeeping.data.sync.GitSyncSettings
import com.example.personalbookkeeping.data.sync.GitSyncState
import com.example.personalbookkeeping.data.sync.GitSyncStatus
import com.example.personalbookkeeping.data.sync.ObjectStorageSettings
import com.example.personalbookkeeping.data.sync.ObjectStorageSyncManager
import com.example.personalbookkeeping.data.sync.ObjectSyncResult
import com.example.personalbookkeeping.data.sync.ObjectSyncState
import com.example.personalbookkeeping.data.sync.ObjectSyncStatus
import com.example.personalbookkeeping.domain.model.LedgerCategories
import com.example.personalbookkeeping.domain.model.LedgerEntry
import com.example.personalbookkeeping.domain.model.LedgerType
import com.example.personalbookkeeping.ui.components.RecordFeedbackSnackbarHost
import com.example.personalbookkeeping.ui.components.RecordFeedbackVisuals
import com.example.personalbookkeeping.ui.screens.EntryScreen
import com.example.personalbookkeeping.ui.screens.GitConflictScreen
import com.example.personalbookkeeping.ui.screens.GitSettingsScreen
import com.example.personalbookkeeping.ui.screens.HomeScreen
import com.example.personalbookkeeping.ui.screens.ImportPreviewScreen
import com.example.personalbookkeeping.ui.screens.LedgerHistoryScreen
import com.example.personalbookkeeping.ui.screens.SettingsScreen
import com.example.personalbookkeeping.ui.screens.ObjectStorageSettingsScreen
import com.example.personalbookkeeping.ui.screens.ObjectStorageConflictScreen
import com.example.personalbookkeeping.ui.theme.LedgerColors
import com.example.personalbookkeeping.ui.theme.LedgerTheme
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LedgerTab {
    HOME,
    HISTORY,
    SETTINGS
}

@Composable
fun PersonalLedgerApp(
    viewModel: LedgerViewModel,
    fileTransfer: LedgerFileTransfer
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val application = remember(context) { context.applicationContext as LedgerApplication }
    val gitSyncManager: GitSyncManager = remember(application) { application.gitSyncManager }
    val objectManager: ObjectStorageSyncManager = remember(application) { application.objectStorageSyncManager }
    val aiViewModel: AiBookkeepingViewModel = composeViewModel(
        factory = remember(application) {
            AiBookkeepingViewModelFactory(application.aiSettingsStore, application.ledgerRepository)
        }
    )
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    var showAi by rememberSaveable { mutableStateOf(false) }
    var showAiHistory by rememberSaveable { mutableStateOf(false) }
    var showAiSettings by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(LedgerTab.HOME) }
    var showEntry by rememberSaveable { mutableStateOf(false) }
    var entryType by rememberSaveable { mutableStateOf(LedgerType.EXPENSE) }
    var editingEntry by remember { mutableStateOf<LedgerEntry?>(null) }
    var pendingImport by remember { mutableStateOf<ImportPreview?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var gitStatus by remember {
        mutableStateOf(
            GitSyncStatus(
                state = GitSyncState.UNCONFIGURED,
                isConfigured = false,
                localRepositoryExists = false,
                lastSuccessfulSyncAt = null
            )
        )
    }
    var gitSettings by remember { mutableStateOf<GitSyncSettings?>(null) }
    var showGitSettings by rememberSaveable { mutableStateOf(false) }
    var showGitConflicts by rememberSaveable { mutableStateOf(false) }
    var gitWorking by rememberSaveable { mutableStateOf(false) }
    var gitSettingsError by rememberSaveable { mutableStateOf<String?>(null) }
    var processingRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var objectStatus by remember { mutableStateOf(ObjectSyncStatus(ObjectSyncState.UNCONFIGURED, null)) }
    var objectSettings by remember { mutableStateOf<Pair<ObjectStorageSettings, String>?>(null) }
    var showObjectSettings by rememberSaveable { mutableStateOf(false) }
    var showObjectConflicts by remember { mutableStateOf(false) }
    var objectWorking by remember { mutableStateOf(false) }
    var objectError by rememberSaveable { mutableStateOf<String?>(null) }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun showRecordFeedback(message: String) {
        scope.launch { snackbarHostState.showSnackbar(RecordFeedbackVisuals(message)) }
    }

    fun refreshGitState() {
        scope.launch {
            val status = withContext(Dispatchers.IO) { gitSyncManager.status() }
            val settings = runCatching {
                withContext(Dispatchers.IO) { gitSyncManager.configuredSettings() }
            }.getOrNull()
            gitStatus = status
            gitSettings = settings
            if (status.state == GitSyncState.CONFLICTS || status.state == GitSyncState.RESOLUTION_READY) {
                showGitConflicts = true
            }
        }
    }

    fun runManualSync() {
        if (gitWorking) return
        scope.launch {
            gitWorking = true
            val result = withContext(Dispatchers.IO) { gitSyncManager.syncNow() }
            val status = withContext(Dispatchers.IO) { gitSyncManager.status() }
            gitStatus = status
            gitWorking = false
            when (result) {
                is GitSyncResult.Synced -> {
                    showGitConflicts = false
                    showMessage("已手动同步 ${result.records} 笔记录")
                }
                is GitSyncResult.Conflicts -> {
                    showGitConflicts = true
                }
                is GitSyncResult.Failed -> showMessage(result.failure.message)
            }
        }
    }

    fun resolveGitConflict(recordId: String, choice: GitConflictChoice) {
        if (gitWorking) return
        scope.launch {
            gitWorking = true
            processingRecordId = recordId
            val result = withContext(Dispatchers.IO) {
                gitSyncManager.resolveConflict(recordId, choice)
            }
            val status = withContext(Dispatchers.IO) { gitSyncManager.status() }
            gitStatus = status
            processingRecordId = null
            gitWorking = false
            when (result) {
                is GitSyncResult.Synced -> {
                    showGitConflicts = false
                    showMessage("冲突已解决并完成同步")
                }
                is GitSyncResult.Conflicts -> showGitConflicts = true
                is GitSyncResult.Failed -> showMessage(result.failure.message)
            }
        }
    }

    fun saveGitSettings(settings: GitSyncSettings, token: String) {
        if (gitWorking) return
        scope.launch {
            gitWorking = true
            gitSettingsError = null
            runCatching {
                withContext(Dispatchers.IO) { gitSyncManager.configure(settings, token) }
                gitSettings = withContext(Dispatchers.IO) { gitSyncManager.configuredSettings() }
                gitStatus = withContext(Dispatchers.IO) { gitSyncManager.status() }
            }.onSuccess {
                showGitSettings = false
                showMessage("Git 远端配置已保存")
            }.onFailure {
                gitSettingsError = "保存失败，请检查配置后重试"
            }
            gitWorking = false
        }
    }

    fun clearGitSettings() {
        if (gitWorking) return
        scope.launch {
            gitWorking = true
            runCatching {
                withContext(Dispatchers.IO) { gitSyncManager.clearConfiguration() }
                gitSettings = null
                gitStatus = withContext(Dispatchers.IO) { gitSyncManager.status() }
            }.onSuccess {
                showGitSettings = false
                showGitConflicts = false
                showMessage("Git 同步已关闭，本机账目保留")
            }.onFailure {
                gitSettingsError = "无法清除 Git 配置，请重试"
            }
            gitWorking = false
        }
    }

    LaunchedEffect(gitSyncManager) {
        refreshGitState()
    }

    LaunchedEffect(objectManager) {
        objectStatus = withContext(Dispatchers.IO) { objectManager.status() }
        objectSettings = runCatching { objectManager.configuredSettings() }.getOrNull()
    }

    fun handleObjectResult(result: ObjectSyncResult) {
        when (result) {
            is ObjectSyncResult.Synced -> showMessage("对象存储操作成功，共 ${result.records} 条记录")
            is ObjectSyncResult.Conflicts -> { showObjectConflicts = true; showMessage("请逐条选择冲突版本") }
            is ObjectSyncResult.Failed -> showMessage(result.failure.message)
        }
    }

    fun runObjectAction(action: suspend () -> ObjectSyncResult) {
        if (objectWorking) return
        scope.launch {
            objectWorking = true
            try {
                val result = action()
                objectStatus = objectManager.status()
                if (result is ObjectSyncResult.Synced) showObjectConflicts = false
                handleObjectResult(result)
            } finally { objectWorking = false }
        }
    }

    fun openImport(uri: Uri?, format: LedgerFileFormat) {
        if (uri == null) return
        scope.launch {
            isImporting = true
            runCatching {
                val content = readTextFile(context, uri)
                withContext(Dispatchers.Default) {
                    if (format == LedgerFileFormat.CSV) fileTransfer.previewCsv(content)
                    else fileTransfer.previewJson(content)
                }
            }.onSuccess { preview ->
                pendingImport = preview
            }.onFailure {
                showMessage("无法读取文件，请重新选择")
            }
            isImporting = false
        }
    }

    fun exportFile(uri: Uri?, format: LedgerFileFormat) {
        if (uri == null) return
        scope.launch {
            runCatching {
                val content = withContext(Dispatchers.IO) {
                    if (format == LedgerFileFormat.CSV) fileTransfer.exportCsv()
                    else fileTransfer.exportJson()
                }
                withContext(Dispatchers.IO) { writeTextFile(context, uri, content) }
            }.onSuccess {
                showMessage(if (format == LedgerFileFormat.CSV) "CSV 已导出" else "JSON 已导出")
            }.onFailure {
                showMessage("导出失败，请检查保存位置")
            }
        }
    }

    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        openImport(uri, LedgerFileFormat.CSV)
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        openImport(uri, LedgerFileFormat.JSON)
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        exportFile(uri, LedgerFileFormat.CSV)
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        exportFile(uri, LedgerFileFormat.JSON)
    }

    BackHandler(enabled = showAi || showAiHistory || showAiSettings || showEntry || pendingImport != null || showGitSettings || showObjectSettings || showObjectConflicts) {
        when {
            showAiSettings -> if (!aiState.settingsBusy) showAiSettings = false
            showAiHistory -> showAiHistory = false
            showAi -> if (aiState.phase != AiPhase.SAVING) {
                aiViewModel.cancelRecognition()
                showAi = false
            }
            pendingImport != null -> pendingImport = null
            showObjectConflicts -> showObjectConflicts = false
            showObjectSettings -> { showObjectSettings = false; objectError = null }
            showGitSettings -> {
                showGitSettings = false
                gitSettingsError = null
            }
            else -> {
                showEntry = false
                editingEntry = null
            }
        }
    }

    LedgerTheme {
        if (showAiSettings) {
            AiSettingsScreen(
                state = aiState,
                onSave = { settings, key ->
                    aiViewModel.saveSettings(settings, key) { showAiSettings = false }
                },
                onClear = { aiViewModel.clearSettings { showAiSettings = false } },
                onBack = { if (!aiState.settingsBusy) showAiSettings = false },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showAiHistory) {
            AiHistoryScreen(
                history = aiState.history,
                onBack = { showAiHistory = false },
                onSeeLedger = {
                    showAiHistory = false
                    showAi = false
                    selectedTab = LedgerTab.HISTORY
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showAi) {
            AiBookkeepingScreen(
                state = aiState,
                onDraftChange = aiViewModel::updateDraft,
                onRecognize = aiViewModel::recognize,
                onRetrySave = aiViewModel::retrySave,
                onConfigure = { showAiSettings = true },
                onCancel = aiViewModel::cancelRecognition,
                onNewDraft = aiViewModel::newDraft,
                onSeeLedger = { showAi = false; selectedTab = LedgerTab.HISTORY },
                onHistory = { if (!aiState.busy) showAiHistory = true },
                onBack = {
                    if (aiState.phase != AiPhase.SAVING) {
                        aiViewModel.cancelRecognition()
                        showAi = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (pendingImport != null) {
            ImportPreviewScreen(
                preview = pendingImport!!,
                committing = isImporting,
                onBack = { pendingImport = null },
                onCommit = {
                    pendingImport?.let { preview ->
                        scope.launch {
                            isImporting = true
                            try {
                                val attempt = withContext(Dispatchers.IO) { fileTransfer.commit(preview) }
                                if (attempt.commitResult != null && attempt.preview.issues.isEmpty()) {
                                    val result = attempt.commitResult
                                    pendingImport = null
                                    selectedTab = LedgerTab.HOME
                                    showMessage("导入 ${result.importedCount} 笔，跳过重复 ${result.duplicateCount} 笔")
                                } else {
                                    pendingImport = attempt.preview
                                }
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                showMessage("导入失败，账目未更改，请重试")
                            } finally {
                                isImporting = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showObjectConflicts) {
            ObjectStorageConflictScreen(
                conflicts = objectStatus.conflicts,
                working = objectWorking,
                onChoose = { recordId, revisionId ->
                    runObjectAction { objectManager.resolveConflict(recordId, revisionId) }
                },
                onRefresh = { runObjectAction { objectManager.syncNow() } },
                onBack = { showObjectConflicts = false },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showObjectSettings) {
            ObjectStorageSettingsScreen(
                existing = objectSettings,
                saving = objectWorking,
                errorMessage = objectError,
                onBack = { showObjectSettings = false; objectError = null },
                onSave = { settings, accessKey, secret, session ->
                    if (!objectWorking) scope.launch {
                        objectWorking = true
                        try {
                            withContext(Dispatchers.IO) { objectManager.configure(settings, accessKey, secret, session) }
                            objectSettings = objectManager.configuredSettings()
                            objectStatus = objectManager.status()
                            showObjectSettings = false
                            showMessage("对象存储配置已保存")
                        } catch (exception: Exception) { objectError = "保存失败，请检查配置和密钥" }
                        finally { objectWorking = false }
                    }
                },
                onClear = {
                    if (!objectWorking) scope.launch {
                        objectWorking = true
                        try {
                            withContext(Dispatchers.IO) { objectManager.clearConfiguration() }
                            objectSettings = null
                            objectStatus = objectManager.status()
                            showObjectSettings = false
                            showObjectConflicts = false
                            showMessage("对象存储配置已清除")
                        } catch (exception: Exception) { objectError = "无法清除配置" }
                        finally { objectWorking = false }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (showGitConflicts) {
            GitConflictScreen(
                conflicts = gitStatus.conflicts,
                processingRecordId = processingRecordId,
                resolutionReady = gitStatus.state == GitSyncState.RESOLUTION_READY,
                onChoose = ::resolveGitConflict,
                onContinueSync = ::runManualSync,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showGitSettings) {
            GitSettingsScreen(
                existing = gitSettings,
                saving = gitWorking,
                errorMessage = gitSettingsError,
                onBack = {
                    showGitSettings = false
                    gitSettingsError = null
                },
                onSave = ::saveGitSettings,
                onClear = ::clearGitSettings,
                modifier = Modifier.fillMaxSize()
            )
        } else if (showEntry) {
            EntryScreen(
                initialType = entryType,
                existing = editingEntry,
                categoryCatalog = LedgerCategories.available(state.entries + listOfNotNull(editingEntry)),
                onBack = {
                    showEntry = false
                    editingEntry = null
                },
                onSave = { type, amountFen, date, category, note, existing ->
                    viewModel.saveEntry(type, amountFen, date, category, note, existing)
                    showEntry = false
                    editingEntry = null
                    showRecordFeedback("记录已保存")
                },
                onDelete = { entry ->
                    viewModel.deleteEntry(entry.id)
                    showRecordFeedback("记录已删除")
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Scaffold(
                containerColor = LedgerColors.Canvas,
                snackbarHost = { RecordFeedbackSnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (selectedTab == LedgerTab.HOME) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                entryType = LedgerType.EXPENSE
                                editingEntry = null
                                showEntry = true
                            },
                            containerColor = LedgerColors.Expense,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text("记支出", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = LedgerColors.Surface) {
                        NavigationBarItem(
                            selected = selectedTab == LedgerTab.HOME,
                            onClick = { selectedTab = LedgerTab.HOME },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.Home, contentDescription = null) },
                            label = { Text("总览") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == LedgerTab.HISTORY,
                            onClick = { selectedTab = LedgerTab.HISTORY },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.List, contentDescription = null) },
                            label = { Text("流水") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == LedgerTab.SETTINGS,
                            onClick = { selectedTab = LedgerTab.SETTINGS },
                            icon = { androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null) },
                            label = { Text("设置") }
                        )
                    }
                }
            ) { padding ->
                when (selectedTab) {
                    LedgerTab.HOME -> HomeScreen(
                        state = state,
                        onPreviousMonth = { viewModel.shiftMonth(-1) },
                        onNextMonth = { viewModel.shiftMonth(1) },
                        onAdd = { type ->
                            entryType = type
                            editingEntry = null
                            showEntry = true
                        },
                        onSeeLedger = { selectedTab = LedgerTab.HISTORY },
                        onAiBookkeeping = {
                            aiViewModel.enterFromHome()
                            showAiHistory = false
                            showAi = true
                        },
                        onEdit = { entry ->
                            editingEntry = entry
                            entryType = entry.type
                            showEntry = true
                        },
                        modifier = Modifier.padding(padding)
                    )
                    LedgerTab.HISTORY -> LedgerHistoryScreen(
                        state = state,
                        onPreviousMonth = { viewModel.shiftMonth(-1) },
                        onNextMonth = { viewModel.shiftMonth(1) },
                        onEdit = { entry ->
                            editingEntry = entry
                            entryType = entry.type
                            showEntry = true
                        },
                        modifier = Modifier.padding(padding)
                    )
                    LedgerTab.SETTINGS -> SettingsScreen(
                        onConfigureAi = { showAiSettings = true },
                        aiConfigured = aiState.settings != null,
                        onImportCsv = { csvImportLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                        onImportJson = { jsonImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                        onExportCsv = { csvExportLauncher.launch("personal-ledger.csv") },
                        onExportJson = { jsonExportLauncher.launch("personal-ledger.json") },
                        onConfigureGit = {
                            gitSettingsError = null
                            showGitSettings = true
                        },
                        syncConfigured = gitStatus.isConfigured,
                        syncStatus = syncStatusText(gitStatus),
                        onSyncNow = {
                            if (gitStatus.state == GitSyncState.CONFLICTS || gitStatus.state == GitSyncState.RESOLUTION_READY) {
                                showGitConflicts = true
                            } else {
                                runManualSync()
                            }
                        },
                        objectConfigured = objectSettings != null,
                        objectStatus = when (objectStatus.state) {
                            ObjectSyncState.UNCONFIGURED -> "未配置，账目仅保存在本机"
                            ObjectSyncState.READY -> objectStatus.lastSuccessfulSyncAt?.let { "上次成功同步：$it" } ?: "已配置，等待手动同步"
                            ObjectSyncState.SYNCING -> "正在同步"
                            ObjectSyncState.CONFLICTS -> "${objectStatus.conflicts.size} 条记录需要选择版本"
                            ObjectSyncState.ERROR -> objectStatus.failure?.message ?: "对象存储暂时不可用"
                        },
                        onConfigureObject = { objectError = null; showObjectSettings = true },
                        onTestObject = { runObjectAction { objectManager.testConnection() } },
                        onSyncObject = {
                            runObjectAction { objectManager.syncNow() }
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
        if (gitWorking || objectWorking) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("正在处理") },
                text = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 14.dp),
                            strokeWidth = 2.dp
                        )
                        Text("请稍候，完成前暂时无法编辑账目。")
                    }
                },
                confirmButton = {}
            )
        }
    }
}

private suspend fun readTextFile(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
        ?: error("The selected file could not be opened")
}

private suspend fun writeTextFile(context: Context, uri: Uri, content: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.bufferedWriter(StandardCharsets.UTF_8)?.use { writer ->
        writer.write(content)
        writer.flush()
    } ?: error("The selected destination could not be opened")
}

private fun syncStatusText(status: GitSyncStatus): String = when (status.state) {
    GitSyncState.UNCONFIGURED -> "未配置远端；当前仅保存在本机"
    GitSyncState.READY -> status.lastSuccessfulSyncAt?.let { instant ->
        val time = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
            .withZone(ZoneId.systemDefault())
            .format(instant)
        "上次成功同步：$time"
    } ?: "已配置远端；等待你手动同步"
    GitSyncState.SYNCING -> "正在同步，请稍候"
    GitSyncState.CONFLICTS -> "${status.conflicts.size} 笔账目需要选择保留版本"
    GitSyncState.RESOLUTION_READY -> "冲突已处理，继续同步以完成合并"
    GitSyncState.ERROR -> status.failure?.message ?: "同步暂时不可用，请检查配置"
}
