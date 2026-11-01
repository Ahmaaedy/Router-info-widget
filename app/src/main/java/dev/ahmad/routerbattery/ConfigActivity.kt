package dev.ahmad.routerbattery

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Opens when the widget is added (and when the widget is tapped). Not a launcher activity. */
class ConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val hasPassword = runCatching { Store.password(this).isNotBlank() }.getOrDefault(false)
        val savedIp = runCatching { Store.ip(this) }.getOrDefault("192.168.0.1")

        setContent {
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var ip by remember { mutableStateOf(savedIp) }
                    var pw by remember { mutableStateOf("") }
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Router Battery", style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(ip, { ip = it }, label = { Text("Router IP") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pw, { pw = it },
                            label = { Text(if (hasPassword) "Admin password (blank = keep)" else "Admin password") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                Store.saveCreds(this@ConfigActivity, ip.trim(), pw)
                                Refresher.now(this@ConfigActivity)
                                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                }
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = ip.isNotBlank() && (pw.isNotBlank() || hasPassword),
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}
