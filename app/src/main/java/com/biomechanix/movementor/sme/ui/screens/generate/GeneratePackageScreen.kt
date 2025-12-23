package com.biomechanix.movementor.sme.ui.screens.generate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen for configuring and generating reference packages.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeneratePackageScreen(
    onNavigateBack: () -> Unit,
    onPackageGenerated: () -> Unit,
    viewModel: GeneratePackageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle generation success
    LaunchedEffect(uiState.generationStatus) {
        when (val status = uiState.generationStatus) {
            is GenerationStatus.Success -> {
                snackbarHostState.showSnackbar("Package generation started!")
                onPackageGenerated()
            }
            is GenerationStatus.Error -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.clearError()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Package") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.session == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Session not found")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Session info card
                SessionInfoCard(
                    exerciseName = uiState.session!!.exerciseName,
                    exerciseType = uiState.session!!.exerciseType,
                    frameCount = uiState.session!!.frameCount,
                    duration = uiState.session!!.durationSeconds
                )

                // Package metadata section
                PackageMetadataSection(
                    packageName = uiState.packageName,
                    onPackageNameChange = viewModel::onPackageNameChange,
                    nameError = uiState.nameError,
                    description = uiState.description,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    version = uiState.version,
                    onVersionChange = viewModel::onVersionChange,
                    difficulty = uiState.difficulty,
                    onDifficultyChange = viewModel::onDifficultyChange
                )

                // Joint selection section
                JointSelectionSection(
                    selectedJoints = uiState.selectedJoints,
                    onJointToggle = viewModel::onJointToggle,
                    onSelectGroup = viewModel::selectJointGroup,
                    onClear = viewModel::clearJoints
                )

                // Tolerance settings section
                ToleranceSettingsSection(
                    toleranceTight = uiState.toleranceTight,
                    onToleramceTightChange = viewModel::onToleranceTightChange,
                    toleranceModerate = uiState.toleranceModerate,
                    onToleranceModerateChange = viewModel::onToleranceModerateChange,
                    toleranceLoose = uiState.toleranceLoose,
                    onToleranceLooseChange = viewModel::onToleranceLooseChange
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Generate button
                Button(
                    onClick = viewModel::generatePackage,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.generationStatus !is GenerationStatus.Generating
                ) {
                    if (uiState.generationStatus is GenerationStatus.Generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating...")
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Reference Package")
                    }
                }

                // Info text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Package generation runs in the background. You'll be notified when complete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SessionInfoCard(
    exerciseName: String,
    exerciseType: String,
    frameCount: Int,
    duration: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = exerciseType,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = "$frameCount frames",
                    style = MaterialTheme.typography.bodySmall
                )
                if (duration != null) {
                    Text(
                        text = " • ${String.format("%.1f", duration)}s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackageMetadataSection(
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    nameError: String?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    version: String,
    onVersionChange: (String) -> Unit,
    difficulty: DifficultyLevel?,
    onDifficultyChange: (DifficultyLevel?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Package Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = packageName,
                onValueChange = onPackageNameChange,
                label = { Text("Package Name *") },
                modifier = Modifier.fillMaxWidth(),
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = version,
                    onValueChange = onVersionChange,
                    label = { Text("Version") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // Difficulty dropdown
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = difficulty?.displayName ?: "Not set",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Difficulty") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Not set") },
                            onClick = {
                                onDifficultyChange(null)
                                expanded = false
                            }
                        )
                        DifficultyLevel.entries.forEach { level ->
                            DropdownMenuItem(
                                text = { Text(level.displayName) },
                                onClick = {
                                    onDifficultyChange(level)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JointSelectionSection(
    selectedJoints: Set<String>,
    onJointToggle: (String) -> Unit,
    onSelectGroup: (List<String>) -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Primary Joints",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (selectedJoints.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear")
                    }
                }
            }

            Text(
                text = "Select the joints most important for this exercise",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Quick select buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { onSelectGroup(JointGroups.UPPER_BODY) }) {
                    Text("Upper Body")
                }
                TextButton(onClick = { onSelectGroup(JointGroups.LOWER_BODY) }) {
                    Text("Lower Body")
                }
                TextButton(onClick = { onSelectGroup(JointGroups.CORE) }) {
                    Text("Core")
                }
            }

            // Joint chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JointGroups.ALL_JOINTS.forEach { joint ->
                    FilterChip(
                        selected = joint in selectedJoints,
                        onClick = { onJointToggle(joint) },
                        label = { Text(formatJointName(joint)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToleranceSettingsSection(
    toleranceTight: Double,
    onToleramceTightChange: (Double) -> Unit,
    toleranceModerate: Double,
    onToleranceModerateChange: (Double) -> Unit,
    toleranceLoose: Double,
    onToleranceLooseChange: (Double) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tolerance Settings",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Hide" else "Show")
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Adjust how strictly movements must match the reference",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ToleranceSlider(
                        label = "Tight (Strict)",
                        value = toleranceTight,
                        onValueChange = onToleramceTightChange,
                        valueRange = 0.5f..1.0f
                    )

                    ToleranceSlider(
                        label = "Moderate",
                        value = toleranceModerate,
                        onValueChange = onToleranceModerateChange,
                        valueRange = 0.4f..0.9f
                    )

                    ToleranceSlider(
                        label = "Loose (Lenient)",
                        value = toleranceLoose,
                        onValueChange = onToleranceLooseChange,
                        valueRange = 0.3f..0.8f
                    )
                }
            }
        }
    }
}

@Composable
private fun ToleranceSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = valueRange
        )
    }
}

private fun formatJointName(joint: String): String {
    return joint.replace("_", " ")
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
}
