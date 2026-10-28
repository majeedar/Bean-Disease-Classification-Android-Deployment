package com.majeedar.beandoc.ui.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.majeedar.beandoc.R
import com.majeedar.beandoc.data.DiseaseInfo
import com.majeedar.beandoc.ml.ConfidenceLevel
import com.majeedar.beandoc.viewmodel.ScanState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    state: ScanState,
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    onRetake: () -> Unit,
    onTips: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                // Idle only happens briefly (e.g. after process death); the nav host sends the user home.
                ScanState.Idle -> Unit
                ScanState.Analyzing -> Analyzing()
                is ScanState.Error -> ErrorContent(state.reason, onRetry, onScanAnother)
                is ScanState.Result -> ResultContent(state, onScanAnother, onRetake, onTips)
            }
        }
    }
}

@Composable
private fun Analyzing() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(Modifier.heightIn(min = 16.dp))
        Text(stringResource(R.string.result_analyzing), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ErrorContent(reason: ScanState.Error.Reason, onRetry: () -> Unit, onScanAnother: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(
                if (reason == ScanState.Error.Reason.IMAGE) R.string.result_error_image else R.string.result_error_model
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (reason == ScanState.Error.Reason.MODEL) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_try_again))
            }
        }
        OutlinedButton(onClick = onScanAnother, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.result_scan_another))
        }
    }
}

@Composable
private fun ResultContent(
    result: ScanState.Result,
    onScanAnother: () -> Unit,
    onRetake: () -> Unit,
    onTips: () -> Unit,
) {
    val context = LocalContext.current
    val prediction = result.prediction
    val info = remember(prediction.label) {
        DiseaseInfo.forLabel(context.resources, context.packageName, prediction.label)
    }
    val notSure = prediction.confidenceLevel == ConfidenceLevel.NOT_SURE
    val percent = (prediction.confidence * 100).roundToInt()
    val image = remember(result.photo) { result.photo.asImageBitmap() }
    // In landscape keep the photo small enough that the diagnosis is visible without scrolling.
    val configuration = LocalConfiguration.current
    val photoSize = minOf(320, configuration.screenWidthDp - 32, (configuration.screenHeightDp * 0.45f).toInt()).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.result_photo_description),
            modifier = Modifier
                .size(photoSize)
                .clip(MaterialTheme.shapes.large),
        )

        if (notSure) {
            DiagnosisCard(
                style = DiagnosisStyle.notSure(),
                title = stringResource(R.string.result_not_sure_title),
                subtitle = stringResource(R.string.result_not_sure_body),
                footnote = stringResource(R.string.result_best_guess, info.title, percent),
            )
            Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_retake))
            }
        } else {
            DiagnosisCard(
                style = DiagnosisStyle.forLabel(prediction.label),
                title = info.title,
                subtitle = stringResource(
                    if (prediction.confidenceLevel == ConfidenceLevel.VERY_LIKELY) R.string.result_very_likely
                    else R.string.result_likely
                ),
                footnote = stringResource(R.string.result_percent, percent),
            )
            AdviceSections(info)
            Button(onClick = onScanAnother, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_scan_another))
            }
        }

        OutlinedButton(onClick = onTips, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_photo_tips))
        }

        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.result_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosisCard(style: DiagnosisStyle, title: String, subtitle: String, footnote: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = style.container, contentColor = style.content),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(style.icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = subtitle, style = MaterialTheme.typography.titleMedium)
                Text(text = footnote, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AdviceSections(info: DiseaseInfo) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!info.hasAdvice) {
            Text(stringResource(R.string.advice_unavailable), style = MaterialTheme.typography.bodyLarge)
        }
        info.sections.forEach { section ->
            Text(
                text = stringResource(section.heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            section.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            section.bullets.forEach { bullet ->
                Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
                    Text(bullet, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
