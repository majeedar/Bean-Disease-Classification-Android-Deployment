package com.majeedar.beandoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.majeedar.beandoc.ui.AppNavigation
import com.majeedar.beandoc.ui.theme.BeanDocTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BeanDocTheme {
                AppNavigation()
            }
        }
    }
}
