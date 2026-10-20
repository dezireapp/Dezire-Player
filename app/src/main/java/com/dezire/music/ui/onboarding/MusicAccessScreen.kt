package com.dezire.music.ui.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dezire.music.data.MusicRepository

/*
 * Onboarding step 2: explains why Dezire needs the local audio permission
 * and drives the actual request + scan.
 *
 * The permission result, the recoverable denial state and the return from
 * Android Settings are all handled here; the scan itself runs through the
 * shared [com.dezire.music.data.MusicRepository] via [OnboardingViewModel],
 * so the existing library state is reused rather than duplicated.
 */
@Composable
fun MusicAccessScreen(
    viewModel: OnboardingViewModel,
    onBack: () -> Unit,
    onPermissionGranted: () -> Unit,
    onNotNow: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permission = remember { MusicRepository.requiredAudioPermission() }
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var scanRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            permanentlyDenied = false
            onPermissionGranted()
            viewModel.scanMusic()
        } else {
            permissionDenied = true
            permanentlyDenied = !shouldShowRationale(context, permission)
        }
    }

    fun beginScan() {
        scanRequested = true
        if (MusicRepository.hasAudioPermission(context)) {
            permissionDenied = false
            permanentlyDenied = false
            onPermissionGranted()
            viewModel.scanMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    /*
     * On returning from Android Settings (or any resume) re-check the
     * permission: if the user enabled it we continue the requested scan
     * automatically instead of leaving them stuck.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                scanRequested &&
                scanState is OnboardingViewModel.ScanState.Idle &&
                MusicRepository.hasAudioPermission(context)
            ) {
                permissionDenied = false
                permanentlyDenied = false
                onPermissionGranted()
                viewModel.scanMusic()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent)
    }

    OnboardingBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnboardingWhite
                    )
                }
            }

            val current = scanState
            when {
                current is OnboardingViewModel.ScanState.Scanning -> ScanProgress()
                current is OnboardingViewModel.ScanState.Complete ->
                    ScanComplete(songCount = current.songCount, onFinish = onFinish)
                permissionDenied -> PermissionDenied(
                    permanentlyDenied = permanentlyDenied,
                    onRetry = { beginScan() },
                    onOpenSettings = { openAppSettings() },
                    onNotNow = onNotNow
                )
                else -> MusicAccessContent(
                    onScan = { beginScan() },
                    onNotNow = onNotNow
                )
            }
        }
    }
}

@Composable
private fun MusicAccessContent(
    onScan: () -> Unit,
    onNotNow: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.5f))

        WaveformArtwork(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )

        Spacer(Modifier.height(28.dp))

        OnboardingEyebrow(text = "LET'S SET UP YOUR MUSIC")
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Bring Your Music to Life",
            color = OnboardingWhite,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Allow Dezire to access audio files on your device so it " +
                "can discover and organize your songs.",
            color = OnboardingMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(26.dp))

        BenefitRow(
            icon = Icons.Default.Search,
            text = "Discover songs stored on your device."
        )
        BenefitRow(
            icon = Icons.Default.LibraryMusic,
            text = "Organize your music library automatically."
        )
        BenefitRow(
            icon = Icons.Default.Album,
            text = "Display album artwork, artists, and track information when available."
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(text = "SCAN MY MUSIC", onClick = onScan)
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Not now",
                color = OnboardingMuted,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ScanProgress() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = OnboardingElectricBlue,
            strokeWidth = 3.dp,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Finding your music...",
            color = OnboardingWhite,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Scanning this device for audio files.",
            color = OnboardingMuted,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ScanComplete(
    songCount: Int,
    onFinish: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = OnboardingElectricBlue,
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Your library is ready",
            color = OnboardingWhite,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (songCount == 1) {
                "Found 1 song on this device."
            } else {
                "Found $songCount songs on this device."
            },
            color = OnboardingMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "OPEN DEZIRE", onClick = onFinish)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionDenied(
    permanentlyDenied: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(OnboardingElectricBlue.copy(alpha = 0.14f))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = OnboardingElectricBlue,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Music access needed",
            color = OnboardingWhite,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (permanentlyDenied) {
                "Audio access is turned off. Enable it in Android Settings " +
                    "to discover the songs stored on this device."
            } else {
                "Dezire needs audio access to scan the songs stored on " +
                    "this device. You can try again, or continue without " +
                    "scanning for now."
            },
            color = OnboardingMuted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        if (permanentlyDenied) {
            PrimaryButton(text = "OPEN SETTINGS", onClick = onOpenSettings)
        } else {
            PrimaryButton(text = "TRY AGAIN", onClick = onRetry)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Not now",
                color = OnboardingMuted,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(OnboardingElectricBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnboardingElectricBlue,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            color = OnboardingWhite.copy(alpha = 0.9f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OnboardingElectricBlue,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
    }
}

/*
 * Whether Android will still show the system permission dialog. A false
 * result after a denial means the user selected "don't allow" / "don't ask
 * again", so we surface the Open Settings escape hatch instead of trapping
 * them in a request loop.
 */
private fun shouldShowRationale(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return true
    return activity.shouldShowRequestPermissionRationale(permission)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
