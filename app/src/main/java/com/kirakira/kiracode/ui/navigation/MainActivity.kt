package com.kirakira.kiracode.ui.navigation

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.ui.theme.KiraCodeThemeWithPreferences
import com.kirakira.kiracode.ui.theme.SystemBarsColorSync
import com.kirakira.kiracode.ui.theme.isDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * KiraCode 主 Activity
 * 入口点，设置主题与导航宿主，启用 edge-to-edge 显示
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "=== onCreate 开始 ===")
        try {
            // 窗口底色强制深色：防止未铺满的页面透出系统亮色窗口背景
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF121212.toInt()))
            applyImmersiveMode()

            setContent {
                Log.d(TAG, "=== setContent 开始 ===")
                KiraCodeThemeWithPreferences(preferencesRepository) {
                    Log.d(TAG, "=== 主题加载完成 ===")
                    val darkTheme = isDarkTheme(preferencesRepository)
                    SystemBarsColorSync(
                        darkTheme = darkTheme,
                        context = this
                    )
                    AppNavHost()
                    Log.d(TAG, "=== AppNavHost 加载完成 ===")
                }
            }
            Log.d(TAG, "=== onCreate 完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "启动失败", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
