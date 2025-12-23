package com.biomechanix.movementor.sme.ui.screens.recording

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Exercise type with display info.
 */
data class ExerciseType(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val examples: List<String>
)

/**
 * Predefined exercise types.
 */
val exerciseTypes = listOf(
    ExerciseType(
        id = "STRENGTH",
        name = "Strength Training",
        description = "Weight lifting, resistance exercises",
        icon = Icons.Default.FitnessCenter,
        examples = listOf("Squat", "Deadlift", "Bench Press", "Shoulder Press", "Bicep Curl")
    ),
    ExerciseType(
        id = "YOGA",
        name = "Yoga",
        description = "Poses, flows, and stretches",
        icon = Icons.Default.SelfImprovement,
        examples = listOf("Downward Dog", "Warrior I", "Tree Pose", "Child's Pose", "Cobra")
    ),
    ExerciseType(
        id = "REHABILITATION",
        name = "Rehabilitation",
        description = "Physical therapy exercises",
        icon = Icons.Default.SportsGymnastics,
        examples = listOf("Knee Extension", "Hip Flexor Stretch", "Shoulder Rotation", "Calf Raise")
    ),
    ExerciseType(
        id = "FUNCTIONAL",
        name = "Functional Movement",
        description = "Sport-specific and functional exercises",
        icon = Icons.Default.SportsMartialArts,
        examples = listOf("Lunge", "Plank", "Burpee", "Box Jump", "Medicine Ball Throw")
    )
)

/**
 * Screen for selecting exercise type and entering exercise name before recording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordingScreen(
    onNavigateBack: () -> Unit,
    onStartRecording: (exerciseType: String, exerciseName: String) -> Unit
) {
    var selectedType by remember { mutableStateOf<ExerciseType?>(null) }
    var exerciseName by remember { mutableStateOf("") }
    var showExerciseInput by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (showExerciseInput) "Exercise Name" else "New Recording"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showExerciseInput) {
                            showExerciseInput = false
                            exerciseName = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (showExerciseInput && selectedType != null) {
            ExerciseNameInput(
                exerciseType = selectedType!!,
                exerciseName = exerciseName,
                onExerciseNameChange = { exerciseName = it },
                onStartRecording = {
                    onStartRecording(selectedType!!.id, exerciseName)
                },
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            ExerciseTypeSelection(
                exerciseTypes = exerciseTypes,
                onTypeSelected = { type ->
                    selectedType = type
                    showExerciseInput = true
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun ExerciseTypeSelection(
    exerciseTypes: List<ExerciseType>,
    onTypeSelected: (ExerciseType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select Exercise Type",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(exerciseTypes) { type ->
                ExerciseTypeCard(
                    exerciseType = type,
                    onClick = { onTypeSelected(type) }
                )
            }
        }
    }
}

@Composable
private fun ExerciseTypeCard(
    exerciseType: ExerciseType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = exerciseType.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exerciseType.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = exerciseType.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Select",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseNameInput(
    exerciseType: ExerciseType,
    exerciseName: String,
    onExerciseNameChange: (String) -> Unit,
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Selected type info
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = exerciseType.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = exerciseType.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Exercise name input
        Text(
            text = "Enter Exercise Name",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = exerciseName,
            onValueChange = onExerciseNameChange,
            label = { Text("Exercise Name") },
            placeholder = { Text("e.g., ${exerciseType.examples.firstOrNull() ?: "Squat"}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Suggestions
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(exerciseType.examples.filter {
                exerciseName.isEmpty() || it.contains(exerciseName, ignoreCase = true)
            }) { suggestion ->
                SuggestionChip(
                    text = suggestion,
                    onClick = { onExerciseNameChange(suggestion) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start recording button
        Button(
            onClick = onStartRecording,
            enabled = exerciseName.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Start Recording")
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
