package io.github.acedroidx.frp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val isStartup = MutableStateFlow(false)
    private val logText = MutableStateFlow("")
    private val frpcConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val frpsConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    private val runningConfigList = MutableStateFlow<List<FrpConfig>>(emptyList())
    
    // CHML FRP相关状态
    private val chmlFrpToken = MutableStateFlow("")
    private val tunnelList = MutableStateFlow<List<ChmlFrpConfig>>(emptyList())
    private val showTokenDialog = mutableStateOf(false)
    private val showTunnelDialog = mutableStateOf(false)

    private lateinit var preferences: SharedPreferences

    private lateinit var mService: ShellService
    private var mBound: Boolean = false

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ShellService.LocalBinder
            mService = binder.getService()
            mBound = true

            mService.lifecycleScope.launch {
                mService.processThreads.collect { processThreads ->
                    runningConfigList.value = processThreads.keys.toList()
                }
            }
            mService.lifecycleScope.launch {
                mService.logText.collect {
                    logText.value = it
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

    private val configActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            updateConfigList()
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("data", MODE_PRIVATE)
        isStartup.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        
        // 初始化CHML FRP Token
        chmlFrpToken.value = PreferencesManager.getToken(this)

        checkConfig()
        updateConfigList()
        checkNotificationPermission()
        createBGNotificationChannel()

        val intent = Intent(this, ShellService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        enableEdgeToEdge()
        setContent {
            FrpTheme(
                useIOSStyle = true, // 默认使用iOS风格主题
                dynamicColor = false // 关闭动态颜色，确保使用iOS风格颜色
            ) {
                Scaffold(topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "frp for Android - ${BuildConfig.VERSION_NAME}/${BuildConfig.FrpVersion}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }) { contentPadding ->
                    Column(
                        modifier = Modifier
                            .padding(contentPadding)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        MainContent()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainContent() {
        val isStartupState by isStartup.collectAsStateWithLifecycle()
        val logTextState by logText.collectAsStateWithLifecycle()
        val frpcConfigListState by frpcConfigList.collectAsStateWithLifecycle()
        val frpsConfigListState by frpsConfigList.collectAsStateWithLifecycle()
        val runningConfigListState by runningConfigList.collectAsStateWithLifecycle()
        val chmlFrpTokenState by chmlFrpToken.collectAsStateWithLifecycle()
        val tunnelsState by tunnelList.collectAsStateWithLifecycle()
        val showTokenDialogState by showTokenDialog
        val showTunnelDialogState by showTunnelDialog
        
        var showCreateDialog by remember { mutableStateOf(false) }
        val clipboardManager = LocalClipboardManager.current
        val openDialog = remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 添加CHML FRP设置卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "CHML FRP设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.size(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Token: ${if (chmlFrpTokenState.isNotBlank()) "已设置" else "未设置"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Button(
                            onClick = { showTokenDialog.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("设置Token")
                        }
                    }
                    
                    Spacer(modifier = Modifier.size(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { fetchTunnels() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("获取隧道列表")
                        }
                        
                        Button(
                            onClick = { showTunnelDialog.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("选择隧道")
                        }
                    }
                }
            }
            
            // 自启动开关
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        stringResource(R.string.auto_start_switch),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = isStartupState,
                        onCheckedChange = {
                            val editor = preferences.edit()
                            editor.putBoolean(PreferencesKey.AUTO_START, it)
                            editor.apply()
                            isStartup.value = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // 配置列表
            if (frpcConfigListState.isEmpty() && frpsConfigListState.isEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            stringResource(R.string.no_config),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { openDialog.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.addConfigButton))
                        }
                    }
                }
            } else {
                // 添加配置按钮
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Button(
                            onClick = { openDialog.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.addConfigButton))
                        }
                    }
                }
            }
            
            if (frpcConfigListState.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Text(
                        "frpc", 
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                frpcConfigListState.forEach { config -> FrpConfigItem(config) }
            }
            
            if (frpsConfigListState.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 0.dp
                    )
                ) {
                    Text(
                        "frps", 
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                frpsConfigListState.forEach { config -> FrpConfigItem(config) }
            }

            // 日志部分
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.frp_log),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { mService.clearLog() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { 
                                Text(stringResource(R.string.deleteButton)) 
                            }
                            
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(logTextState))
                                    // Only show a toast for Android 12 and lower.
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) Toast.makeText(
                                        this@MainActivity, getString(R.string.copied), Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) { 
                                Text(stringResource(R.string.copy)) 
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.size(12.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        SelectionContainer {
                            Text(
                                if (logTextState == "") stringResource(R.string.no_log) else logTextState,
                                style = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        
        // 创建配置对话框
        if (openDialog.value) {
            CreateConfigDialog { openDialog.value = false }
        }

        // CHML FRP Token设置对话框
        if (showTokenDialogState) {
            var tokenText by remember { mutableStateOf(chmlFrpTokenState) }
            
            BasicAlertDialog(
                onDismissRequest = { showTokenDialog.value = false }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "设置CHML FRP Token",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.size(20.dp))
                        
                        OutlinedTextField(
                            value = tokenText,
                            onValueChange = { tokenText = it },
                            label = { Text("Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        
                        Spacer(modifier = Modifier.size(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showTokenDialog.value = false },
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Text(
                                    "取消",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            Button(
                                onClick = {
                                    chmlFrpToken.value = tokenText
                                    PreferencesManager.saveToken(this@MainActivity, tokenText)
                                    showTokenDialog.value = false
                                    Toast.makeText(this@MainActivity, "Token已保存", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
        
        // CHML FRP隧道选择对话框
        if (showTunnelDialogState) {
            BasicAlertDialog(
                onDismissRequest = { showTunnelDialog.value = false }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "选择CHML FRP隧道",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.size(20.dp))
                        
                        if (tunnelsState.isEmpty()) {
                            Text(
                                "没有可用的隧道，请先获取隧道列表",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                tunnelsState.forEach { tunnel ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp)
                                        ) {
                                            Text(
                                                text = tunnel.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.size(4.dp))
                                            Text(
                                                text = "ID: ${tunnel.id}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "类型: ${tunnel.type}, 端口: ${tunnel.nport}, 节点: ${tunnel.node}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Button(
                                                onClick = {
                                                    executeChmlFrpCommand(tunnel)
                                                    showTunnelDialog.value = false
                                                },
                                                modifier = Modifier.align(Alignment.End),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("选择")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.size(20.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { showTunnelDialog.value = false }
                            ) {
                                Text(
                                    "取消",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun FrpConfigItem(config: FrpConfig) {
        val runningConfigList by runningConfigList.collectAsStateWithLifecycle(emptyList())
        val isRunning = runningConfigList.contains(config)
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) 
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) 
                else 
                    MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = if (isRunning) "运行中" else "未运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { startConfigActivity(config) },
                        enabled = !isRunning,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (!isRunning) 1f else 0.5f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pencil_24dp),
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    IconButton(
                        onClick = { deleteConfig(config) },
                        enabled = !isRunning,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.error.copy(alpha = if (!isRunning) 1f else 0.5f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_delete_24),
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    Switch(
                        checked = isRunning, 
                        onCheckedChange = {
                            if (it) (startShell(config)) else (stopShell(config))
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    @Preview(showBackground = true)
    fun CreateConfigDialog(onClose: () -> Unit = {}) {
        BasicAlertDialog(onDismissRequest = { onClose() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp), // 更大的圆角半径，符合iOS风格
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp) // 增加间距
                ) {
                    // 标题
                    Text(
                        stringResource(R.string.create_frp_select),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface // 使用onSurface颜色
                    )
                    
                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    
                    // 选项按钮
                    Row(
                        horizontalArrangement = Arrangement.SpaceAround,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // FRPC按钮
                        Button(
                            onClick = { startConfigActivity(FrpType.FRPC);onClose() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp), // 增加圆角
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "frpc",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        
                        // FRPS按钮
                        Button(
                            onClick = { startConfigActivity(FrpType.FRPS);onClose() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary // 使用次要颜色区分
                            ),
                            shape = RoundedCornerShape(12.dp), // 增加圆角
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "frps",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    
                    // CHML FRP隧道选项 - 如果有token才显示
                    // 仅当有token时显示隧道选项
                    val tokenValue = chmlFrpToken.collectAsStateWithLifecycle("").value
                    
                    if (tokenValue.isNotBlank()) {
                        // 如果有隧道数据，显示选择按钮
                        Button(
                            onClick = { 
                                onClose() 
                                // 在主界面显示隧道选择
                                fetchTunnels()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "选择CHML FRP隧道",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    
                    // 分隔线
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                    
                    // 取消按钮
                    TextButton(
                        onClick = { onClose() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            "取消",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            unbindService(connection)
            mBound = false
        }
    }

    fun checkConfig() {
        val frpcDir = FrpType.FRPC.getDir(this)
        if (frpcDir.exists() && !frpcDir.isDirectory) {
            frpcDir.delete()
        }
        if (!frpcDir.exists()) frpcDir.mkdirs()
        val frpsDir = FrpType.FRPS.getDir(this)
        if (frpsDir.exists() && !frpsDir.isDirectory) {
            frpsDir.delete()
        }
        if (!frpsDir.exists()) frpsDir.mkdirs()
        // v1.1旧版本配置迁移
        // 遍历文件夹内的所有文件
        this.filesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".toml")) {
                // 构建目标文件路径
                val destination = File(frpcDir, file.name)
                // 移动文件
                if (file.renameTo(destination)) {
                    Log.d("adx", "Moved: ${file.name} to ${destination.absolutePath}")
                } else {
                    Log.e("adx", "Failed to move: ${file.name}")
                }
            }
        }
    }

    private fun deleteConfig(config: FrpConfig) {
        val file = config.getFile(this)
        if (file.exists()) {
            file.delete()
        }
        updateConfigList()
    }

    private fun startConfigActivity(type: FrpType) {
        val currentDate = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.getDefault())
        val formattedDateTime = formatter.format(currentDate)
        val fileName = "$formattedDateTime.toml"
        val file = File(type.getDir(this), fileName)
        file.writeBytes(resources.assets.open(type.getConfigAssetsName()).readBytes())
        val config = FrpConfig(type, fileName)
        startConfigActivity(config)
    }

    private fun startConfigActivity(config: FrpConfig) {
        val intent = Intent(this, ConfigActivity::class.java)
        intent.putExtra(IntentExtraKey.FrpConfig, config)
        configActivityLauncher.launch(intent)
    }

    private fun startShell(config: FrpConfig) {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.START)
        intent.putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        startService(intent)
    }

    private fun stopShell(config: FrpConfig) {
        val intent = Intent(this, ShellService::class.java)
        intent.setAction(ShellServiceAction.STOP)
        intent.putExtra(IntentExtraKey.FrpConfig, arrayListOf(config))
        startService(intent)
    }

    private fun checkNotificationPermission() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createBGNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel("shell_bg", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateConfigList() {
        frpcConfigList.value = (FrpType.FRPC.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPC, it)
        }
        frpsConfigList.value = (FrpType.FRPS.getDir(this).list()?.toList() ?: listOf()).map {
            FrpConfig(FrpType.FRPS, it)
        }

        // 检查自启动列表中是否含有已经删除的配置
        val frpcAutoStartList =
            preferences.getStringSet(PreferencesKey.AUTO_START_FRPC_LIST, emptySet())?.filter {
                frpcConfigList.value.contains(
                    FrpConfig(FrpType.FRPC, it)
                )
            }
        with(preferences.edit()) {
            putStringSet(PreferencesKey.AUTO_START_FRPC_LIST, frpcAutoStartList?.toSet())
            apply()
        }
        val frpsAutoStartList =
            preferences.getStringSet(PreferencesKey.AUTO_START_FRPS_LIST, emptySet())?.filter {
                frpsConfigList.value.contains(
                    FrpConfig(FrpType.FRPS, it)
                )
            }
        with(preferences.edit()) {
            putStringSet(PreferencesKey.AUTO_START_FRPS_LIST, frpsAutoStartList?.toSet())
            apply()
        }
    }

    // CHML FRP相关方法
    private fun fetchTunnels() {
        this.lifecycleScope.launch {
            try {
                val token = chmlFrpToken.value
                if (token.isBlank()) {
                    this@MainActivity.showErrorDialog("错误", "请先设置Token")
                    return@launch
                }
                
                Toast.makeText(this@MainActivity, "正在获取隧道列表...", Toast.LENGTH_SHORT).show()
                
                val response = ChmlFrpApi.create().getTunnels(token)
                if (response.isSuccessful && response.body() != null) {
                    val apiResponse = response.body()!!
                    if (apiResponse.code == 200) {
                        if (apiResponse.data.isEmpty()) {
                            this@MainActivity.showErrorDialog("提示", "获取隧道列表成功，但列表为空")
                        } else {
                            tunnelList.value = apiResponse.data
                            Toast.makeText(this@MainActivity, "获取隧道列表成功，共${apiResponse.data.size}个隧道", Toast.LENGTH_SHORT).show()
                            // 自动打开隧道选择对话框
                            showTunnelDialog.value = true
                        }
                    } else {
                        val errorMsg = "获取隧道列表失败: ${apiResponse.msg}"
                        this@MainActivity.showErrorDialog("API错误", errorMsg)
                        Log.e("CHML_FRP", "API错误: ${apiResponse.msg}, 状态码: ${apiResponse.code}")
                    }
                } else {
                    val errorMsg = "网络请求失败: ${response.code()} ${response.message()}"
                    var detailMsg = ""
                    if (response.errorBody() != null) {
                        try {
                            detailMsg = "\n\n详细信息:\n" + response.errorBody()!!.string()
                        } catch (e: Exception) {
                            Log.e("CHML_FRP", "无法读取错误详情: ${e.message}")
                        }
                    }
                    this@MainActivity.showErrorDialog("网络错误", errorMsg + detailMsg)
                    Log.e("CHML_FRP", errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "网络请求异常: ${e.message}"
                this@MainActivity.showErrorDialog("网络异常", errorMsg)
                Log.e("CHML_FRP", errorMsg, e)
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    private fun showErrorDialog(title: String, message: String) {
        val showDialog = MutableStateFlow(true)
        
        lifecycleScope.launch {
            setContent {
                val showDialogState by showDialog.collectAsStateWithLifecycle()
                
                if (showDialogState) {
                    BasicAlertDialog(
                        onDismissRequest = { 
                            showDialog.value = false
                            // 恢复原始UI
                            setContent {
                                FrpTheme(
                                    useIOSStyle = true,
                                    dynamicColor = false
                                ) {
                                    Scaffold(topBar = {
                                        TopAppBar(
                                            title = {
                                                Text(
                                                    "frp for Android - ${BuildConfig.VERSION_NAME}/${BuildConfig.FrpVersion}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            colors = TopAppBarDefaults.topAppBarColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }) { contentPadding ->
                                        Column(
                                            modifier = Modifier
                                                .padding(contentPadding)
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                                .background(MaterialTheme.colorScheme.background)
                                        ) {
                                            MainContent()
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (title.contains("错误") || title.contains("异常")) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.size(20.dp))
                                
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.size(24.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = { 
                                            showDialog.value = false
                                            // 恢复原始UI
                                            setContent {
                                                FrpTheme(
                                                    useIOSStyle = true,
                                                    dynamicColor = false
                                                ) {
                                                    Scaffold(topBar = {
                                                        TopAppBar(
                                                            title = {
                                                                Text(
                                                                    "frp for Android - ${BuildConfig.VERSION_NAME}/${BuildConfig.FrpVersion}",
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            },
                                                            colors = TopAppBarDefaults.topAppBarColors(
                                                                containerColor = MaterialTheme.colorScheme.surface,
                                                                titleContentColor = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        )
                                                    }) { contentPadding ->
                                                        Column(
                                                            modifier = Modifier
                                                                .padding(contentPadding)
                                                                .fillMaxWidth()
                                                                .verticalScroll(rememberScrollState())
                                                                .background(MaterialTheme.colorScheme.background)
                                                        ) {
                                                            MainContent()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (title.contains("错误") || title.contains("异常")) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text("确定")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun executeChmlFrpCommand(tunnel: ChmlFrpConfig) {
        if (!this.mBound) {
            Toast.makeText(this, "服务未连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        this.lifecycleScope.launch {
            try {
                val token = chmlFrpToken.value
                if (token.isBlank()) {
                    this@MainActivity.showErrorDialog("错误", "请先设置Token")
                    return@launch
                }
                
                // 获取配置文件内容
                Toast.makeText(this@MainActivity, "正在获取隧道配置...", Toast.LENGTH_SHORT).show()
                val response = ChmlFrpApi.createConfigApi().getConfig(tunnel.id, token)
                
                if (response.isSuccessful && response.body() != null) {
                    val configResponse = response.body()!!
                    if (configResponse.status == 200 && configResponse.success) {
                        // 保存配置文件
                        val configContent = configResponse.cfg
                        
                        // 确保目录存在
                        val configDir = File(this@MainActivity.filesDir, FrpType.FRPC.typeName)
                        if (!configDir.exists()) {
                            configDir.mkdir()
                        }
                        
                        // 保存配置文件到正确的位置
                        val configFile = File(configDir, "chmlfrp.ini")
                        configFile.writeText(configContent)
                        
                        // 创建frpc配置对象
                        val config = FrpConfig(
                            FrpType.FRPC,
                            "chmlfrp.ini",
                        )
                        
                        // 启动frpc服务
                        this@MainActivity.startShell(config)
                        
                        // 更新配置列表
                        this@MainActivity.updateConfigList()
                        
                        Toast.makeText(this@MainActivity, "已启动CHML FRP隧道: ${tunnel.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        this@MainActivity.showErrorDialog("配置获取失败", "API返回错误: ${configResponse.message}")
                    }
                } else {
                    val errorMsg = "获取配置失败: ${response.code()} ${response.message()}"
                    var detailMsg = ""
                    if (response.errorBody() != null) {
                        try {
                            detailMsg = "\n\n详细信息:\n" + response.errorBody()!!.string()
                        } catch (e: Exception) {
                            Log.e("CHML_FRP", "无法读取错误详情: ${e.message}")
                        }
                    }
                    this@MainActivity.showErrorDialog("网络错误", errorMsg + detailMsg)
                }
            } catch (e: Exception) {
                this@MainActivity.showErrorDialog("异常", "获取或启动隧道时出错: ${e.message}")
                Log.e("CHML_FRP", "执行CHML FRP命令异常", e)
            }
        }
    }
}