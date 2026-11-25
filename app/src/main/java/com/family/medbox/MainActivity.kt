package com.family.medbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.family.medbox.ui.MediBoxRoot
import com.family.medbox.ui.theme.ContrastPalette
import com.family.medbox.ui.theme.MediBoxTheme

class MainActivity : ComponentActivity() {

    private var openRemindersRequest by mutableIntStateOf(0)
    private var openElderRequest by mutableIntStateOf(0)
    /** 通知里指定的那一次服药（planId@time），用于打开后高亮 */
    private var focusDoseKey by mutableStateOf<String?>(null)
    private var focusDoseRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MediBoxApp).container
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) == true) {
            openRemindersRequest++
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ELDER_DOSES, false) == true) {
            openElderRequest++
        }
        intent?.getStringExtra(EXTRA_FOCUS_DOSE)?.let {
            focusDoseKey = it
            focusDoseRequest++
        }

        setContent {
            MediBoxTheme {
                // 冷启动先单独画一帧背景：窗口尽快完成首帧、拿到焦点，
                // 再组合整个首页。否则 debug 包首帧组合接近 5 秒，
                // FocusEvent 会超时，被判成输入分发 ANR。
                var contentReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    contentReady = true
                }
                if (!contentReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    val elderMode by container.uiSettings.elderMode.collectAsState()
                    val elderFontScale by container.uiSettings.elderFontScale.collectAsState()
                    val highContrast by container.uiSettings.highContrast.collectAsState()
                    // 对比度增强：加深次要文字与分隔线，重新组合时自动生效。
                    SideEffect {
                        ContrastPalette.enabled = elderMode && highContrast
                    }
                    val baseDensity = LocalDensity.current
                    // 长辈模式只放大字号（sp），不改 dp 尺寸，图标和布局结构保持不变。
                    val density = remember(baseDensity, elderMode, elderFontScale) {
                        if (elderMode) Density(baseDensity.density, baseDensity.fontScale * elderFontScale)
                        else baseDensity
                    }
                    CompositionLocalProvider(LocalDensity provides density) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            MediBoxRoot(
                                container = container,
                                openRemindersRequest = openRemindersRequest,
                                openElderRequest = openElderRequest,
                                focusDoseKey = focusDoseKey,
                                focusDoseRequest = focusDoseRequest
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)) {
            openRemindersRequest++
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_ELDER_DOSES, false)) {
            openElderRequest++
        }
        intent.getStringExtra(EXTRA_FOCUS_DOSE)?.let {
            focusDoseKey = it
            focusDoseRequest++
        }
    }

    companion object {
        const val EXTRA_OPEN_REMINDERS = "open_reminders"
        const val EXTRA_OPEN_ELDER_DOSES = "open_elder_doses"
        const val EXTRA_FOCUS_DOSE = "focus_dose"
    }
}
