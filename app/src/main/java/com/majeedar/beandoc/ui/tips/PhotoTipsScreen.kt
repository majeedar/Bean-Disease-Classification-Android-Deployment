package com.majeedar.beandoc.ui.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.majeedar.beandoc.R

private data class Tip(val icon: ImageVector, val title: Int, val body: Int)

private val TIPS = listOf(
    Tip(Icons.Filled.Eco, R.string.tips_one_leaf_title, R.string.tips_one_leaf_body),
    Tip(Icons.Filled.WbSunny, R.string.tips_light_title, R.string.tips_light_body),
    Tip(Icons.Filled.CenterFocusStrong, R.string.tips_steady_title, R.string.tips_steady_body),
    Tip(Icons.Filled.Visibility, R.string.tips_top_side_title, R.string.tips_top_side_body),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoTipsScreen(onBack: () -> Unit, onScan: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tips_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TIPS.forEach { tip ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            tip.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(stringResource(tip.title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(tip.body), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tips_start_scan))
            }
        }
    }
}
