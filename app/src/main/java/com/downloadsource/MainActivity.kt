package com.downloadsource

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.downloadsource.core.UpdateManager
import com.downloadsource.ui.*
import com.downloadsource.utils.PyRuleEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 Python 规则引擎
        PyRuleEngine.init(this)

        setContent {
            var isLocked by remember { mutableStateOf(ThemeStore.getLockPassword(this) != null) }
            
            LifecycleResumeEffect(Unit) {
                if (ThemeStore.getLockPassword(this@MainActivity) != null) {
                    isLocked = true
                }
                onPauseOrDispose {}
            }

            if (isLocked) {
                SecureLockScreen(onUnlock = { isLocked = false })
                return@setContent
            }

            val palette = rememberPalette()
            GradientBackground(palette) {
                MaterialTheme { MainScaffold() }
            }
        }
    }
}

@Composable
fun SecureLockScreen(onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var inputPwd by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    
    // 包含数字和字母的极简键盘
    val keys = listOf("1","2","3","4","5","6","7","8","9","Q","W","E","R","T","Y","U","I","O","P","A","S","D","F","G","H","J","K","L","Z","X","C","V","B","N","M","0","DEL")

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = palette.primary.first())
        Spacer(Modifier.height(16.dp))
        Text("安全验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = palette.onSurface)
        
        // 密码圆点显示区
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(inputPwd.length.coerceAtMost(8)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(palette.primary.first()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(errorMsg, color = Color.Red, fontSize = 14.sp)

        Spacer(Modifier.height(32.dp))

        // 防截屏、防系统键盘的内置极简键盘
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().height(300.dp)
        ) {
            items(keys) { key ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .clickable {
                            errorMsg = ""
                            if (key == "DEL") {
                                if (inputPwd.isNotEmpty()) inputPwd = inputPwd.dropLast(1)
                            } else {
                                if (inputPwd.length < 8) inputPwd += key
                            }
                            
                            // 自动验证
                            if (inputPwd.length >= 4) {
                                if (ThemeStore.verifyLockPassword(ctx, inputPwd)) {
                                    onUnlock()
                                } else if (inputPwd.length == 8) {
                                    errorMsg = "密码错误"
                                    inputPwd = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (key == "DEL") Icon(Icons.Default.Backspace, null, tint = palette.onSurface)
                    else Text(key, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = palette.onSurface)
                }
            }
        }
    }
}
