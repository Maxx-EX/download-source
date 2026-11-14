package com.downloadsource

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.downloadsource.storage.DirStore

class MainActivity : ComponentActivity() {

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.data?.let { DirStore.save(this, it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val ctx = LocalContext.current
                var dir by remember { mutableStateOf(DirStore.get(ctx)?.toString() ?: "Not set") }
                Column {
                    Text("Download source")
                    Text("Dir: $dir")
                    Button(onClick = {
                        picker.launch(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE))
                    }) { Text("Pick folder") }
                }
            }
        }
    }
}
