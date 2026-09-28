package com.claude.codex.ai.monitoring.presentation.pair.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusTone

/**
 * Camera scanning step. Owns the CAMERA permission request (a platform concern that cannot live
 * in the ViewModel). When access is denied it explains why and offers Settings and code entry.
 */
@Composable
fun QrScannerPanel(
    onCodeScanned: (String) -> Unit,
    onEnterCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denials by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = isGranted
        if (!isGranted) denials++
    }
    LaunchedEffect(Unit) {
        if (!granted && denials == 0) launcher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = modifier.padding(horizontal = Dimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLg),
    ) {
        if (granted) {
            val description = stringResource(R.string.cd_scanner)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(AppTheme.colors.surfaceElevated)
                    .semantics { contentDescription = description },
            ) {
                QrCameraPreview(onCodeScanned = onCodeScanned, modifier = Modifier.fillMaxSize())
                ScannerOverlay(modifier = Modifier.fillMaxSize().padding(Dimens.SpaceXxl))
            }
            Text(
                text = stringResource(R.string.pair_scan_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        } else {
            // After two refusals Android stops showing the dialog, so send the user to Settings.
            val permanentlyDenied = denials >= 2
            StateMessage(
                icon = R.drawable.ic_scan,
                title = stringResource(R.string.pair_camera_title),
                message = stringResource(R.string.pair_camera_message),
                tone = StatusTone.WAITING,
                actionLabel = stringResource(if (permanentlyDenied) R.string.pair_camera_settings else R.string.pair_camera_allow),
                onAction = {
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    } else {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }
        TextButton(onClick = onEnterCodeClick) {
            Text(text = stringResource(R.string.pair_enter_code), color = AppTheme.colors.brandEnd)
        }
    }
}

@Preview
@Composable
private fun QrScannerPanelPreview() {
    AppTheme(darkTheme = true) {
        QrScannerPanel(onCodeScanned = {}, onEnterCodeClick = {})
    }
}
