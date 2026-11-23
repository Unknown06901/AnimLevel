package com.shaml.animlevel

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WriteMethod { ROOT, SHIZUKU, NONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimLevelApp(
    onRequestShizukuPermission: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // --- State ---
    var currentLevel by remember { mutableStateOf(-1) }
    var selectedLevel by remember { mutableStateOf(-1) }
    var isApplying by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var appliedLevelForReboot by remember { mutableStateOf(-1) }

    // Capabilities
    var isRooted by remember { mutableStateOf(false) }
    var shizukuAvailable by remember { mutableStateOf(false) }
    var shizukuPermitted by remember { mutableStateOf(false) }

    val activeMethod: WriteMethod = when {
        isRooted -> WriteMethod.ROOT
        shizukuAvailable && shizukuPermitted -> WriteMethod.SHIZUKU
        else -> WriteMethod.NONE
    }

    // Auto refresh status
    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                val level = AnimLevelManager.readPropViaShell()
                val rooted = AnimLevelManager.isRooted()
                val shizAvail = ShizukuHelper.isAvailable
                val shizPerm = ShizukuHelper.isPermissionGranted
                withContext(Dispatchers.Main) {
                    if (level != null) {
                        currentLevel = level
                        if (selectedLevel == -1) {
                            selectedLevel = level
                        }
                    }
                    isRooted = rooted
                    shizukuAvailable = shizAvail
                    shizukuPermitted = shizPerm
                }
            }
            delay(1500)
        }
    }

    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        darkColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Oplus Animation Level", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text(
                                "persist.sys.oplus.anim_level",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // --- Current Value Card ---
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "CURRENT ACTIVE VALUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                if (currentLevel >= 0) "$currentLevel" else "…",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val matchedItem = AnimLevelManager.LEVEL_INFO.find { it.value == currentLevel }
                            Column {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        matchedItem?.subLabel ?: "Custom / Unknown",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    "Saved in system persist storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // --- Level Options (1, 2, 3 only) ---
                Text(
                    "Select Animation Tier",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                AnimLevelManager.LEVEL_INFO.forEach { item ->
                    LevelOptionCard(
                        item = item,
                        isSelected = selectedLevel == item.value,
                        isCurrent = currentLevel == item.value,
                        onClick = { selectedLevel = item.value }
                    )
                }

                // --- Apply Button ---
                val canApply = activeMethod != WriteMethod.NONE &&
                        selectedLevel in 1..3 &&
                        !isApplying

                Button(
                    onClick = {
                        scope.launch {
                            isApplying = true
                            val target = selectedLevel
                            val ok = withContext(Dispatchers.IO) {
                                when (activeMethod) {
                                    WriteMethod.ROOT -> AnimLevelManager.setViaRoot(target)
                                    WriteMethod.SHIZUKU -> ShizukuHelper.setAnimLevel(target)
                                    WriteMethod.NONE -> false
                                }
                            }
                            if (ok) {
                                delay(300)
                                val newVal = withContext(Dispatchers.IO) {
                                    AnimLevelManager.readPropViaShell()
                                }
                                if (newVal != null) currentLevel = newVal
                                appliedLevelForReboot = target
                                showRebootDialog = true
                            } else {
                                onShowToast("Failed to set persist property")
                            }
                            isApplying = false
                        }
                    },
                    enabled = canApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Applying Property…")
                    } else {
                        Text(
                            when {
                                selectedLevel !in 1..3 -> "Select a level"
                                activeMethod == WriteMethod.NONE -> "Permission required (see below)"
                                else -> "Apply Level $selectedLevel & Reboot"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider()

                // --- Write Method Status Card ---
                MethodStatusCard(
                    isRooted = isRooted,
                    shizukuAvailable = shizukuAvailable,
                    shizukuPermitted = shizukuPermitted,
                    activeMethod = activeMethod,
                    onRequestShizuku = onRequestShizukuPermission
                )

                Spacer(Modifier.height(4.dp))

                // --- Developer Credits ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Developed by @UnkNwn019",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // --- Reboot Confirmation Dialog ---
        if (showRebootDialog) {
            AlertDialog(
                onDismissRequest = { showRebootDialog = false },
                icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Reboot Required", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Property has been successfully set to: Level $appliedLevelForReboot\n\n" +
                            "Because 'persist.sys.oplus.anim_level' is evaluated during system boot by the display and window manager, a reboot is required for full visual effect."
                        )
                        Text(
                            "Would you like to reboot the device now?",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRebootDialog = false
                            scope.launch(Dispatchers.IO) {
                                AnimLevelManager.rebootDevice(isRooted)
                            }
                        }
                    ) {
                        Text("Reboot Now")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                showRebootDialog = false
                                scope.launch(Dispatchers.IO) {
                                    AnimLevelManager.restartUiProcesses(isRooted)
                                }
                                onShowToast("UI processes soft-restarted")
                            }
                        ) {
                            Text("Soft Restart UI")
                        }
                        TextButton(onClick = { showRebootDialog = false }) {
                            Text("Later")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun LevelOptionCard(
    item: AnimLevelManager.LevelItem,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "option_bg"
    )

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${item.value}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.subLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrent) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(
                                text = "CURRENT",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun MethodStatusCard(
    isRooted: Boolean,
    shizukuAvailable: Boolean,
    shizukuPermitted: Boolean,
    activeMethod: WriteMethod,
    onRequestShizuku: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Authorization Method", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            if (activeMethod != WriteMethod.NONE) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Active: ${activeMethod.name}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            MethodRow("Root (KernelSU / Magisk)", isRooted, hint = null)
            MethodRow(
                "Shizuku (Works Unrooted)",
                shizukuAvailable && shizukuPermitted,
                hint = when {
                    !shizukuAvailable -> "Install Shizuku & start service"
                    !shizukuPermitted -> "Tap to grant permission"
                    else -> "Connected with shell access"
                },
                onClick = if (shizukuAvailable && !shizukuPermitted) onRequestShizuku else null
            )
        }
    }
}

@Composable
private fun MethodRow(
    name: String,
    available: Boolean,
    hint: String?,
    onClick: (() -> Unit)? = null,
) {
    val mod = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick)
    else Modifier.fillMaxWidth()
    Row(modifier = mod, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (available) "✓" else "✗",
            color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
