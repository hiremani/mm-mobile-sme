package com.biomechanix.movementor.sme.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity

/**
 * Data class for phase editor form state.
 */
data class PhaseFormState(
    val phaseName: String = "",
    val startFrame: Int = 0,
    val endFrame: Int = 0,
    val description: String = "",
    val keyPoses: List<Int> = emptyList(),
    val complianceThreshold: Double = 0.7,
    val holdDurationMs: Int = 0,
    val entryCue: String = "",
    val activeCues: List<String> = emptyList(),
    val exitCue: String = "",
    val correctionCues: Map<String, String> = emptyMap()
)

/**
 * Common correction issue types.
 */
object CorrectionIssueTypes {
    val COMMON_ISSUES = listOf(
        "KNEE_VALGUS" to "Knee Valgus",
        "BACK_ROUNDING" to "Back Rounding",
        "FORWARD_LEAN" to "Forward Lean",
        "ELBOW_FLARE" to "Elbow Flare",
        "SHOULDER_SHRUG" to "Shoulder Shrug",
        "HIP_SHIFT" to "Hip Shift",
        "HEAD_FORWARD" to "Head Forward",
        "ANKLE_COLLAPSE" to "Ankle Collapse"
    )
}

/**
 * Dialog for creating or editing a phase annotation.
 */
@Composable
fun PhaseEditorDialog(
    isEditing: Boolean,
    initialState: PhaseFormState,
    totalFrames: Int,
    frameRate: Int,
    onDismiss: () -> Unit,
    onSave: (PhaseFormState) -> Unit
) {
    var phaseName by remember { mutableStateOf(initialState.phaseName) }
    var startFrame by remember { mutableIntStateOf(initialState.startFrame) }
    var endFrame by remember { mutableIntStateOf(initialState.endFrame) }
    var description by remember { mutableStateOf(initialState.description) }
    val keyPoses = remember { mutableStateListOf<Int>().apply { addAll(initialState.keyPoses) } }
    var complianceThreshold by remember { mutableDoubleStateOf(initialState.complianceThreshold) }
    var holdDurationMs by remember { mutableIntStateOf(initialState.holdDurationMs) }
    var entryCue by remember { mutableStateOf(initialState.entryCue) }
    val activeCues = remember { mutableStateListOf<String>().apply { addAll(initialState.activeCues) } }
    var exitCue by remember { mutableStateOf(initialState.exitCue) }
    val correctionCues = remember { mutableStateOf(initialState.correctionCues.toMutableMap()) }

    var phaseNameError by remember { mutableStateOf<String?>(null) }
    var frameRangeError by remember { mutableStateOf<String?>(null) }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val durationFrames = endFrame - startFrame
    val durationSeconds = durationFrames.toFloat() / frameRate

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditing) "Edit Phase" else "Create Phase",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Phase Name
                OutlinedTextField(
                    value = phaseName,
                    onValueChange = {
                        phaseName = it
                        phaseNameError = if (it.isBlank()) "Phase name is required" else null
                    },
                    label = { Text("Phase Name *") },
                    placeholder = { Text("e.g., Eccentric, Hold, Concentric") },
                    isError = phaseNameError != null,
                    supportingText = phaseNameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Brief description of this phase") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Range
                Text(
                    text = "Frame Range",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = startFrame.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { newStart ->
                                startFrame = newStart.coerceIn(0, totalFrames)
                                validateFrameRange(startFrame, endFrame)?.let {
                                    frameRangeError = it
                                } ?: run { frameRangeError = null }
                            }
                        },
                        label = { Text("Start") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = endFrame.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { newEnd ->
                                endFrame = newEnd.coerceIn(0, totalFrames)
                                validateFrameRange(startFrame, endFrame)?.let {
                                    frameRangeError = it
                                } ?: run { frameRangeError = null }
                            }
                        },
                        label = { Text("End") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                frameRangeError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Duration display
                Text(
                    text = "Duration: $durationFrames frames (${String.format("%.2f", durationSeconds)}s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hold Duration (for static phases)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (holdDurationMs > 0) holdDurationMs.toString() else "",
                        onValueChange = { value ->
                            holdDurationMs = value.toIntOrNull()?.coerceIn(0, 10000) ?: 0
                        },
                        label = { Text("Hold Duration (ms)") },
                        placeholder = { Text("0 for dynamic") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (holdDurationMs > 0) "${holdDurationMs / 1000.0}s" else "Dynamic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Compliance Threshold
                Text(
                    text = "Compliance Threshold: ${(complianceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = complianceThreshold.toFloat(),
                    onValueChange = { complianceThreshold = it.toDouble() },
                    valueRange = 0.6f..1.0f,
                    steps = 7,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Minimum similarity score for compliance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Key Poses Section
                Text(
                    text = "Key Poses",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Frame indices representing key positions in this phase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (keyPoses.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        keyPoses.forEachIndexed { index, frame ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "Frame $frame",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    IconButton(
                                        onClick = { keyPoses.removeAt(index) },
                                        modifier = Modifier.padding(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (keyPoses.size < 5) {
                    var newKeyPoseFrame by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newKeyPoseFrame,
                            onValueChange = { newKeyPoseFrame = it },
                            label = { Text("Frame #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                newKeyPoseFrame.toIntOrNull()?.let { frame ->
                                    if (frame in startFrame..endFrame && frame !in keyPoses) {
                                        keyPoses.add(frame)
                                        newKeyPoseFrame = ""
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Coaching Cues Section
                Text(
                    text = "Coaching Cues",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Optional guidance for users during this phase",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Entry Cue
                OutlinedTextField(
                    value = entryCue,
                    onValueChange = { entryCue = it },
                    label = { Text("Entry Cue") },
                    placeholder = { Text("Shown when entering this phase") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Active Cues
                Text(
                    text = "Active Cues (during phase)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                activeCues.forEachIndexed { index, cue ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cue,
                            onValueChange = { activeCues[index] = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { activeCues.removeAt(index) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove cue"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (activeCues.size < 5) {
                    TextButton(
                        onClick = { activeCues.add("") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Add Active Cue")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Exit Cue
                OutlinedTextField(
                    value = exitCue,
                    onValueChange = { exitCue = it },
                    label = { Text("Exit Cue") },
                    placeholder = { Text("Shown when exiting this phase") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Correction Cues Section (Expandable)
                TextButton(
                    onClick = { showAdvancedSettings = !showAdvancedSettings }
                ) {
                    Text(if (showAdvancedSettings) "Hide Correction Cues" else "Show Correction Cues")
                }

                if (showAdvancedSettings) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Correction Cues",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Corrections shown when specific form issues are detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CorrectionIssueTypes.COMMON_ISSUES.forEach { (issueKey, issueLabel) ->
                        val correction = correctionCues.value[issueKey] ?: ""
                        OutlinedTextField(
                            value = correction,
                            onValueChange = { newValue ->
                                correctionCues.value = correctionCues.value.toMutableMap().apply {
                                    if (newValue.isBlank()) {
                                        remove(issueKey)
                                    } else {
                                        put(issueKey, newValue)
                                    }
                                }
                            },
                            label = { Text(issueLabel) },
                            placeholder = { Text("Correction for $issueLabel") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            // Validate and save
                            if (phaseName.isBlank()) {
                                phaseNameError = "Phase name is required"
                                return@Button
                            }
                            if (startFrame >= endFrame) {
                                frameRangeError = "End frame must be after start frame"
                                return@Button
                            }

                            onSave(
                                PhaseFormState(
                                    phaseName = phaseName.trim(),
                                    startFrame = startFrame,
                                    endFrame = endFrame,
                                    description = description.trim(),
                                    keyPoses = keyPoses.sorted(),
                                    complianceThreshold = complianceThreshold,
                                    holdDurationMs = holdDurationMs,
                                    entryCue = entryCue.trim(),
                                    activeCues = activeCues.filter { it.isNotBlank() },
                                    exitCue = exitCue.trim(),
                                    correctionCues = correctionCues.value.filterValues { it.isNotBlank() }
                                )
                            )
                        },
                        enabled = phaseName.isNotBlank() && startFrame < endFrame
                    ) {
                        Text(if (isEditing) "Save Changes" else "Create Phase")
                    }
                }
            }
        }
    }
}

/**
 * Convert PhaseAnnotationEntity to PhaseFormState for editing.
 */
fun PhaseAnnotationEntity.toFormState(): PhaseFormState {
    val activeCuesList = activeCuesJson?.parseJsonStringList() ?: emptyList()
    val keyPosesList = keyPosesJson?.parseJsonIntList() ?: emptyList()
    val correctionCuesMap = correctionCuesJson?.parseJsonStringMap() ?: emptyMap()

    return PhaseFormState(
        phaseName = phaseName,
        startFrame = startFrame,
        endFrame = endFrame,
        description = description ?: "",
        keyPoses = keyPosesList,
        complianceThreshold = complianceThreshold ?: 0.7,
        holdDurationMs = holdDurationMs ?: 0,
        entryCue = entryCue ?: "",
        activeCues = activeCuesList,
        exitCue = exitCue ?: "",
        correctionCues = correctionCuesMap
    )
}

/**
 * Convert list of active cues to JSON string.
 */
fun List<String>.toActiveCuesJson(): String? {
    if (isEmpty()) return null
    return "[" + joinToString(",") { "\"$it\"" } + "]"
}

/**
 * Convert list of key pose frames to JSON string.
 */
fun List<Int>.toKeyPosesJson(): String? {
    if (isEmpty()) return null
    return "[" + joinToString(",") + "]"
}

/**
 * Convert correction cues map to JSON string.
 */
fun Map<String, String>.toCorrectionCuesJson(): String? {
    if (isEmpty()) return null
    return "{" + entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" } + "}"
}

/**
 * Parse JSON string list.
 */
private fun String.parseJsonStringList(): List<String> {
    return try {
        trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Parse JSON int list.
 */
private fun String.parseJsonIntList(): List<Int> {
    return try {
        trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Parse JSON string map.
 */
private fun String.parseJsonStringMap(): Map<String, String> {
    return try {
        val content = trim().removePrefix("{").removeSuffix("}")
        if (content.isBlank()) return emptyMap()

        content.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            .associate { pair ->
                val parts = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex(), 2)
                val key = parts[0].trim().removeSurrounding("\"")
                val value = parts.getOrNull(1)?.trim()?.removeSurrounding("\"") ?: ""
                key to value
            }
            .filterKeys { it.isNotBlank() }
    } catch (e: Exception) {
        emptyMap()
    }
}

private fun validateFrameRange(start: Int, end: Int): String? {
    return when {
        start >= end -> "End frame must be after start frame"
        end - start < 5 -> "Phase must be at least 5 frames"
        else -> null
    }
}
