package io.github.acedroidx.frp

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.acedroidx.frp.ui.theme.FrpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class ConfigActivity : ComponentActivity() {
    private val configEditText = MutableStateFlow("")
    private val isAutoStart = MutableStateFlow(false)
    private lateinit var configFile: File
    private lateinit var autoStartPreferencesKey: String
    private lateinit var preferences: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val frpConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.extras?.getParcelable(IntentExtraKey.FrpConfig, FrpConfig::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.extras?.getParcelable(IntentExtraKey.FrpConfig)
        }
        if (frpConfig == null) {
            Log.e("adx", "frp config is null")
            Toast.makeText(this, "frp config is null", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        configFile = frpConfig.getFile(this)
        autoStartPreferencesKey = frpConfig.type.getAutoStartPreferencesKey()
        preferences = getSharedPreferences("data", MODE_PRIVATE)
        readConfig()
        readIsAutoStart()

        enableEdgeToEdge()
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
                    // Screen content
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .scrollable(orientation = Orientation.Vertical,
                                state = rememberScrollableState { delta -> 0f })
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        MainContent()
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainContent() {
        val openDialog = remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 按钮区域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = { saveConfig(); closeActivity() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(R.string.saveConfigButton),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = { closeActivity() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(R.string.dontSaveConfigButton),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = { openDialog.value = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        stringResource(R.string.rename),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            // 自动启动开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    stringResource(R.string.auto_start_switch),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = isAutoStart.collectAsStateWithLifecycle(false).value,
                    onCheckedChange = { setAutoStart(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                )
            }
            
            // 配置文本编辑区域
            OutlinedTextField(
                value = configEditText.collectAsStateWithLifecycle("").value,
                onValueChange = { configEditText.value = it },
                textStyle = MaterialTheme.typography.bodyMedium.merge(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        if (openDialog.value) {
            RenameDialog(configFile.name.removeSuffix(".toml")) { openDialog.value = false }
        }
    }

    @Composable
    fun RenameDialog(
        originName: String,
        onClose: () -> Unit,
    ) {
        var text by remember { mutableStateOf(originName) }
        AlertDialog(
            title = {
                Text(
                    stringResource(R.string.rename),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            icon = {
                Icon(
                    painterResource(id = R.drawable.ic_rename),
                    contentDescription = "Rename Icon",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onDismissRequest = {
                onClose()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameConfig("$text.toml")
                        onClose()
                    }
                ) {
                    Text(
                        stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onClose()
                    }
                ) {
                    Text(
                        stringResource(R.string.dismiss),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    fun readConfig() {
        if (configFile.exists()) {
            val mReader = configFile.bufferedReader()
            val mRespBuff = StringBuffer()
            val buff = CharArray(1024)
            var ch = 0
            while (mReader.read(buff).also { ch = it } != -1) {
                mRespBuff.append(buff, 0, ch)
            }
            mReader.close()
            configEditText.value = mRespBuff.toString()
        } else {
            Log.e("adx", "config file is not exist")
            Toast.makeText(this, "config file is not exist", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveConfig() {
        configFile.writeText(configEditText.value)
    }

    fun renameConfig(newName: String) {
        val originAutoStart = isAutoStart.value
        setAutoStart(false)
        val newFile = File(configFile.parent, newName)
        configFile.renameTo(newFile)
        configFile = newFile
        setAutoStart(originAutoStart)
    }

    fun readIsAutoStart() {
        isAutoStart.value =
            preferences.getStringSet(autoStartPreferencesKey, emptySet())?.contains(configFile.name)
                ?: false
    }

    fun setAutoStart(value: Boolean) {
        val editor = preferences.edit()
        val set = preferences.getStringSet(autoStartPreferencesKey, emptySet())?.toMutableSet()
        if (value) {
            set?.add(configFile.name)
        } else {
            set?.remove(configFile.name)
        }
        editor.putStringSet(autoStartPreferencesKey, set)
        editor.apply()
        isAutoStart.value = value
    }

    fun closeActivity() {
        setResult(RESULT_OK)
        finish()
    }
}