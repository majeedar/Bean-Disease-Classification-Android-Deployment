package com.majeedar.beandoc.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majeedar.beandoc.R
import com.majeedar.beandoc.ui.theme.BeanDocTheme

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onGallery: () -> Unit,
    onTips: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.heightIn(min = 24.dp))
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(112.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.heightIn(min = 16.dp))

            Button(
                onClick = onScan,
                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_scan), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(
                onClick = onGallery,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_gallery), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = onTips,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_tips))
            }

            Spacer(Modifier.heightIn(min = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_offline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun HomeScreenPreview() {
    BeanDocTheme { HomeScreen(onScan = {}, onGallery = {}, onTips = {}) }
}
