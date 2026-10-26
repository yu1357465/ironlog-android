package io.github.yu1357465.ironlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.yu1357465.ironlog.ui.IronLogRoot
import io.github.yu1357465.ironlog.ui.theme.IronLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IronLogTheme {
                IronLogRoot()
            }
        }
    }
}
