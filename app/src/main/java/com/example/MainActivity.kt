package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*
import com.example.ui.VoiceSpeechHelper
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Manchester triage colors
val RedManchester = Color(0xFFEF4444)
val OrangeManchester = Color(0xFFF97316)
val YellowManchester = Color(0xFFEAB308)
val GreenManchester = Color(0xFF22C55E)
val BlueManchester = Color(0xFF3B82F6)

data class PredefinedLab(
    val id: String,
    val name: String,
    val ref: String,
    val unit: String,
    val source: String
)

val PRESET_LABS = listOf(
    PredefinedLab("hb", "Hemoglobina (Hb)", "Mulher: 12.0 - 15.5 | Homem: 13.5 - 17.5 g/dL", "g/dL", "SBPC/ML"),
    PredefinedLab("ht", "Hematócrito (Ht)", "Mulher: 36 - 48 % | Homem: 40 - 53 %", "%", "SBPC/ML"),
    PredefinedLab("leu", "Leucócitos", "4.000 - 11.000 /mm³", "/mm³", "SBPC/ML"),
    PredefinedLab("pla", "Plaquetas", "150.000 - 450.000 /mm³", "/mm³", "SBPC/ML"),
    PredefinedLab("ureia", "Ureia", "15 - 45 mg/dL", "mg/dL", "SBN"),
    PredefinedLab("crea", "Creatinina", "Mulher: 0.5 - 1.1 | Homem: 0.6 - 1.2 mg/dL", "mg/dL", "SBN"),
    PredefinedLab("crp", "Proteína C-Reativa (PCR)", "< 5.0 mg/L", "mg/L", "SBC"),
    PredefinedLab("na", "Sódio (Na+)", "135 - 145 mEq/L", "mEq/L", "SBPC/ML"),
    PredefinedLab("k", "Potássio (K+)", "3.5 - 5.1 mEq/L", "mEq/L", "SBPC/ML"),
    PredefinedLab("lac", "Lactato Arterial", "4.5 - 19.8 mg/dL", "mg/dL", "AMIB"),
    PredefinedLab("troponina", "Troponina I Alta Sens.", "< 0.04 ng/mL", "ng/mL", "SBC")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val viewModel: PatientViewModel = viewModel()
    val allPatients by viewModel.allPatients.collectAsStateWithLifecycle()
    val selectedPatient by viewModel.selectedPatient.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = selectedPatient,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
        ) { patient ->
            if (patient == null) {
                PatientDashboardScreen(
                    patients = allPatients,
                    onSelectPatient = { viewModel.selectPatient(it) },
                    onCreatePatient = { name, bed -> viewModel.createPatient(name, bed) },
                    onDeletePatient = { viewModel.deletePatient(it) }
                )
            } else {
                ClinicalSuiteWorkflowScreen(
                    patient = patient,
                    viewModel = viewModel,
                    onExit = { viewModel.selectPatient(null) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatientDashboardScreen(
    patients: List<Patient>,
    onSelectPatient: (Patient) -> Unit,
    onCreatePatient: (String, String) -> Unit,
    onDeletePatient: (Patient) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0C1324),
                        Color(0xFF030712)
                    )
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Radiant Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141D30)),
                border = BorderStroke(1.dp, Color(0xFF233656))
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF00D4AA), Color(0xFF3B82F6)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Medical",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Ultra-Pro Clínica 4.0",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )
                        Text(
                            text = "FARMÁCIA CLÍNICA & SEGURANÇA HOSPITALAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00D4AA),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Text(
                text = "Pacientes Internados / Leitos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (patients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF131A29))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = Color(0xFF00D4AA).copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum paciente ativo no momento",
                            color = Color.LightGray,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Toque no botão '+' abaixo para admitir",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(patients) { patient ->
                        PatientItemRow(
                            patient = patient,
                            onSelect = { onSelectPatient(patient) },
                            onDelete = { onDeletePatient(patient) }
                        )
                    }
                }
            }
        }

        // Add Patient FAB
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF00D4AA),
            contentColor = Color(0xFF0F172A),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Admitir Paciente", modifier = Modifier.size(28.dp))
        }

        if (showCreateDialog) {
            CreatePatientDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name, bed ->
                    onCreatePatient(name, bed)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
fun PatientItemRow(
    patient: Patient,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
        border = BorderStroke(1.dp, Color(0xFF22324C))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Triage classification bullet
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when (patient.triageClass.lowercase()) {
                            "vermelho (emergência)", "vermelho" -> RedManchester
                            "laranja (muito urgente)", "laranja" -> OrangeManchester
                            "amarelo (urgente)", "amarelo" -> YellowManchester
                            "verde (pouco urgente)", "verde" -> GreenManchester
                            "azul (não urgente)", "azul" -> BlueManchester
                            else -> Color.Gray
                        }
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (patient.name.isEmpty()) "Sem Nome" else patient.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Text(
                    text = "Leito: ${patient.bed.ifEmpty { "Pendente" }}",
                    fontSize = 13.sp,
                    color = Color(0xFF00D4AA),
                    fontWeight = FontWeight.SemiBold
                )

                if (patient.chiefComplaint.isNotEmpty()) {
                    Text(
                        text = "QP: ${patient.chiefComplaint}",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(onClick = { showConfirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = Color(0xFFEF4444).copy(alpha = 0.8f))
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Apagar Prontuário?") },
            text = { Text("Tem certeza que deseja apagar o registro deste paciente? Esta conduta é irreversível.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showConfirmDelete = false
                    }
                ) {
                    Text("Apagar", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun CreatePatientDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bed by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Admissão Hospitalar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Paciente") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00D4AA),
                        focusedBorderColor = Color(0xFF00D4AA)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = bed,
                    onValueChange = { bed = it },
                    label = { Text("Leito (ex: 204B ou Box 03)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00D4AA),
                        focusedBorderColor = Color(0xFF00D4AA)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Voltar", color = Color.LightGray)
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name, bed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Proceder", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Stages Labels in Portuguese
val WORKFLOW_STAGES = listOf(
    "Triagem",
    "Admissão",
    "Vitais",
    "Exame",
    "Labs",
    "Evolução",
    "Medicamentos & Segurança", // Reconciliation + Check duplications
    "Laudo IA & Alta" // Conduct suggestions + Discharge criteria
)

@Composable
fun ClinicalSuiteWorkflowScreen(
    patient: Patient,
    viewModel: PatientViewModel,
    onExit: () -> Unit
) {
    val stageIndex by viewModel.currentStageIndex.collectAsStateWithLifecycle()
    val homeMedications by viewModel.homeMedications.collectAsStateWithLifecycle()
    val hospitalPrescriptions by viewModel.hospitalPrescriptions.collectAsStateWithLifecycle()
    val doseAdministrations by viewModel.doseAdministrations.collectAsStateWithLifecycle()
    val safetyAlerts by viewModel.safetyAlerts.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGeneratingGuidance.collectAsStateWithLifecycle()
    val errorString by viewModel.generationError.collectAsStateWithLifecycle()

    var showReportModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080D1A))
    ) {
        // Clinical Suite Prominent Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10192C))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = patient.name.ifEmpty { "Paciente sem Nome" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Leito: ${patient.bed.ifEmpty { "Não classificado" }}",
                    color = Color(0xFF00D4AA),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }

            // Fast Report summary action button
            Button(
                onClick = { showReportModal = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = "Gerar Report", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sumário", color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Stepper Progress Row
        ScrollableTabRow(
            selectedTabIndex = stageIndex,
            containerColor = Color(0xFF0B1220),
            contentColor = Color(0xFF00D4AA),
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            WORKFLOW_STAGES.forEachIndexed { index, title ->
                Tab(
                    selected = stageIndex == index,
                    onClick = { viewModel.setStageIndex(index) },
                    text = {
                        Text(
                            text = "${index + 1}. $title",
                            fontSize = 12.sp,
                            fontWeight = if (stageIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (stageIndex == index) Color(0xFF00D4AA) else Color.LightGray
                        )
                    }
                )
            }
        }

        // Core Stage View Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when (stageIndex) {
                0 -> TriageView(patient = patient, onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) })
                1 -> AdmissionView(patient = patient, onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) })
                2 -> VitalsView(patient = patient, onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) })
                3 -> PhysicalExamView(patient = patient, onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) })
                4 -> LabsView(patient = patient, onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) })
                5 -> EvolutionView(
                    patient = patient,
                    viewModel = viewModel,
                    onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) }
                )
                6 -> MedicationReconciliationView(
                    viewModel = viewModel,
                    homeMeds = homeMedications,
                    hospitalPrescriptions = hospitalPrescriptions,
                    doseAdministrations = doseAdministrations,
                    alerts = safetyAlerts
                )
                7 -> GuidanceView(
                    patient = patient,
                    viewModel = viewModel,
                    isGenerating = isGenerating,
                    error = errorString,
                    onValueUpdate = { k, v -> viewModel.updatePatientField(k, v) }
                )
            }
        }

        // Navigation Stepper Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C1324))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { if (stageIndex > 0) viewModel.setStageIndex(stageIndex - 1) },
                enabled = stageIndex > 0
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Anterior")
            }

            TextButton(
                onClick = { if (stageIndex < WORKFLOW_STAGES.size - 1) viewModel.setStageIndex(stageIndex + 1) },
                enabled = stageIndex < WORKFLOW_STAGES.size - 1
            ) {
                Text("Próximo")
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = "Próximo")
            }
        }
    }

    if (showReportModal) {
        FullReportModal(
            patient = patient,
            homeMeds = homeMedications,
            hospitalPres = hospitalPrescriptions,
            adminLog = doseAdministrations,
            onDismiss = { showReportModal = false }
        )
    }
}

@Composable
fun TriageView(patient: Patient, onValueUpdate: (String, String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Triagem & Entrada Admissional", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Estruture o acolhimento do paciente e a classificação de risco clínico.", fontSize = 12.sp, color = Color.Gray)
        }

        item {
            OutlinedTextField(
                value = patient.chiefComplaint,
                onValueChange = { onValueUpdate("chiefComplaint", it) },
                label = { Text("Queixa Principal (QP)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Dynamic presets row for Quick entry
            Text("Sintomas Comuns rápidos:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val presets = listOf("Dor torácica", "Dispneia", "Cefaleia", "Dor abdominal", "Febre")
                for (text in presets) {
                    AssistChip(
                        onClick = {
                            val current = patient.chiefComplaint.trim()
                            val nextStr = if (current.isEmpty()) text else "$current, $text"
                            onValueUpdate("chiefComplaint", nextStr)
                        },
                        label = { Text(text, color = Color.LightGray) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF131D31))
                    )
                }
            }
        }

        item {
            Text("Classificação de Risco (Manchester):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            val triageClasses = listOf(
                "Vermelho (Emergência)" to RedManchester,
                "Laranja (Muito Urgente)" to OrangeManchester,
                "Amarelo (Urgente)" to YellowManchester,
                "Verde (Pouco Urgente)" to GreenManchester,
                "Azul (Não Urgente)" to BlueManchester
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((label, color) in triageClasses) {
                    val isSelected = patient.triageClass == label
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color.copy(alpha = 0.2f) else Color(0xFF131D31))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) color else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onValueUpdate("triageClass", label) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

@Composable
fun AdmissionView(patient: Patient, onValueUpdate: (String, String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Histórico Clínico e Alergias", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Histórico médico pregressivo (HMP) e rastreamento de alergias medicamentosas.", fontSize = 12.sp, color = Color.Gray)
        }

        item {
            OutlinedTextField(
                value = patient.allergies,
                onValueChange = { onValueUpdate("allergies", it) },
                label = { Text("Alergias Conocidas (CRÍTICO)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex: Penicilina, Dipirona, Nega alergias...") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Text("Modelos Rápidos:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (text in listOf("Nega alergias", "Alergia a Penicilina", "Alergia a AINEs")) {
                    AssistChip(
                        onClick = { onValueUpdate("allergies", text) },
                        label = { Text(text, color = Color.LightGray) }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = patient.history,
                onValueChange = { onValueUpdate("history", it) },
                label = { Text("Doenças de Base / Comorbidades") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Text("Comorbidades Rápidas:", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (text in listOf("HAS", "Diabetes Mellitus 2", "Tabagista", "Etilista")) {
                    AssistChip(
                        onClick = {
                            val current = patient.history.trim()
                            val nextStr = if (current.isEmpty()) text else "$current, $text"
                            onValueUpdate("history", nextStr)
                        },
                        label = { Text(text, color = Color.LightGray) }
                    )
                }
            }
        }
    }
}

@Composable
fun VitalsView(patient: Patient, onValueUpdate: (String, String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Aferição de Sinais Vitais", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = patient.pa,
                    onValueChange = { onValueUpdate("pa", it) },
                    label = { Text("PA (mmHg)") },
                    placeholder = { Text("120/80") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = patient.fc,
                    onValueChange = { onValueUpdate("fc", it) },
                    label = { Text("FC (bpm)") },
                    placeholder = { Text("80") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = patient.temp,
                    onValueChange = { onValueUpdate("temp", it) },
                    label = { Text("Temp (°C)") },
                    placeholder = { Text("36.5") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = patient.spo2,
                    onValueChange = { onValueUpdate("spo2", it) },
                    label = { Text("SpO₂ (%)") },
                    placeholder = { Text("98") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        }

        // Glasgow Coma Scale representation
        item {
            Text("Escala de Coma de Glasgow:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))

            val glasgows = listOf(
                "15 - Lúcido e Orientado",
                "14 - Leve Confusão Mental",
                "12 - Sem Resposta Verbal Óbvia",
                "8 - Coma Grave (Intubação imediata)",
                "3 - Arreflexia Total"
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in glasgows) {
                    val isSelected = patient.glasgow == option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF00D4AA).copy(alpha = 0.15f) else Color(0xFF131D31))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) Color(0xFF00D4AA) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onValueUpdate("glasgow", option) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { onValueUpdate("glasgow", option) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00D4AA)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = option, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicalExamView(patient: Patient, onValueUpdate: (String, String) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Exame Físico por Sistemas", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        val systems = listOf(
            Triple("examGeral", "Estado Geral", "REG, BEG, MEG, Corado, Descorado, Hidratado..."),
            Triple("examResp", "Aparelho Respiratório", "MV universal, sem ruídos adventícios, estertores..."),
            Triple("examCardio", "Aparelho Cardiovascular", "RCR em 2T, bulhas normofonéticas, sem sopros..."),
            Triple("examAbd", "Abdômen", "Plano, flácido, indolor à palpação, RHA presentes..."),
            Triple("examNeuro", "Neurológico", "Pupilas isocóricas e fotorreagentes, sem déficits focais...")
        )

        items(systems) { (key, label, hint) ->
            val value = when (key) {
                "examGeral" -> patient.examGeral
                "examResp" -> patient.examResp
                "examCardio" -> patient.examCardio
                "examAbd" -> patient.examAbd
                "examNeuro" -> patient.examNeuro
                else -> ""
            }

            OutlinedTextField(
                value = value,
                onValueChange = { onValueUpdate(key, it) },
                label = { Text(label) },
                placeholder = { Text(hint) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }
    }
}

@Composable
fun LabsView(patient: Patient, onValueUpdate: (String, String) -> Unit) {
    // We store selected lab list inside patient.labsJson as a serialized JSONArray.
    val labsArray = remember(patient.labsJson) {
        try {
            JSONArray(patient.labsJson)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    var selectedLabId by remember { mutableStateOf("") }
    var inputVal by remember { mutableStateOf("") }
    val localContext = LocalContext.current

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Rastreamento de Laboratório", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Classificado de acordo com os padrões consensuais brasileiros (SBC, SBN, SBPC/ML).", fontSize = 12.sp, color = Color.Gray)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
                border = BorderStroke(1.dp, Color(0xFF1E334D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adicionar Resultado de Exame:", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Selection dropdown simulator
                    var expandedMenu by remember { mutableStateOf(false) }
                    val activePreset = PRESET_LABS.find { it.id == selectedLabId }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF18253B))
                            .clickable { expandedMenu = true }
                            .padding(14.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = activePreset?.let { "${it.name} [Ref: ${it.ref}]" } ?: "-- Escolha o tipo de Exame Rápido --",
                                color = if (activePreset == null) Color.Gray else Color.White,
                                fontSize = 13.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "", tint = Color.LightGray)
                        }

                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.background(Color(0xFF18253B))
                        ) {
                            PRESET_LABS.forEach { lab ->
                                DropdownMenuItem(
                                    text = { Text("${lab.name} [${lab.source}]", color = Color.White) },
                                    onClick = {
                                        selectedLabId = lab.id
                                        expandedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (activePreset != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inputVal,
                                onValueChange = { inputVal = it },
                                label = { Text("Valor Encontrado") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.weight(1f)
                            )

                            Text(activePreset.unit, color = Color(0xFF00D4AA), fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            Button(
                                onClick = {
                                    if (inputVal.isBlank()) {
                                        Toast.makeText(localContext, "Insira um valor", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    // Add to labs JSON
                                    val newObject = JSONObject().apply {
                                        put("id", activePreset.id)
                                        put("name", activePreset.name)
                                        put("value", inputVal)
                                        put("unit", activePreset.unit)
                                        put("ref", activePreset.ref)
                                        put("source", activePreset.source)
                                    }
                                    labsArray.put(newObject)
                                    onValueUpdate("labsJson", labsArray.toString())
                                    inputVal = ""
                                    selectedLabId = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA))
                            ) {
                                Text("Add", color = Color(0xFF0F172A))
                            }
                        }
                    }
                }
            }
        }

        // List registered results
        item {
            Text("Resultados Anexados no Prontuário:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
        }

        if (labsArray.length() == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF131D31))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum exame cadastrado para este paciente.", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            items(labsArray.length()) { index ->
                val obj = labsArray.getJSONObject(index)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val value = obj.getString("value")
                val unit = obj.getString("unit")
                val ref = obj.getString("ref")
                val source = obj.getString("source")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Valor de Ref: $ref", fontSize = 12.sp, color = Color.Gray)
                            Text("Fonte consensual: $source", fontSize = 11.sp, color = Color(0xFF00D4AA))
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("$value $unit", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF00D4AA))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Remover",
                                modifier = Modifier.clickable {
                                    val newArray = JSONArray()
                                    for (i in 0 until labsArray.length()) {
                                        if (i != index) newArray.put(labsArray.get(i))
                                    }
                                    onValueUpdate("labsJson", newArray.toString())
                                },
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceSoundbar(rmsLevel: Float, modifier: Modifier = Modifier) {
    val normalized = ((rmsLevel.coerceIn(-2f, 12f) + 2f) / 14f).coerceIn(0f, 1f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(32.dp)
    ) {
        val bars = listOf(0.4f, 0.8f, 1.0f, 0.7f, 0.3f, 0.6f, 0.9f, 0.5f)
        for (baseHeight in bars) {
            val barHeight = 4.dp + (24.dp * baseHeight * normalized)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00D4AA),
                                Color(0xFF3B82F6)
                            )
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvolutionView(
    patient: Patient,
    viewModel: PatientViewModel,
    onValueUpdate: (String, String) -> Unit
) {
    val context = LocalContext.current
    var showHelpDialog by remember { mutableStateOf(false) }
    var showSimulationPanel by remember { mutableStateOf(false) }

    val evolutions by viewModel.patientEvolutions.collectAsStateWithLifecycle()

    // Handlers for record permission
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Permissão do Microfone é fundamental para comandos de voz", Toast.LENGTH_LONG).show()
        }
    }

    // State log and feedback
    var partialSpokenText by remember { mutableStateOf("") }
    var recognizedCommandLog by remember { mutableStateOf<String?>(null) }

    // Unified voice trigger parser
    val processDetectedSentence = { fullSentence: String ->
        val lower = fullSentence.trim().lowercase()
        when {
            lower.contains("salvar evolução") || lower.contains("salvar evolução no histórico") || lower.contains("registrar evolução") || lower.contains("gravar evolução") -> {
                if (patient.evolutionText.isNotBlank()) {
                    viewModel.savePatientEvolution(patient.evolutionText)
                    onValueUpdate("evolutionText", "")
                    recognizedCommandLog = "💾 Salvei e registrei a evolução no histórico"
                } else {
                    recognizedCommandLog = "⚠️ Erro: Nota de evolução está vazia"
                }
            }
            lower.contains("apagar tudo") || lower.contains("limpar tudo") || lower.contains("limpar texto") -> {
                onValueUpdate("evolutionText", "")
                recognizedCommandLog = "♻️ Limpou toda a nota de evolução"
            }
            lower.contains("paciente melhorou") || lower.contains("melhora clínica") || lower.contains("modelo melhora") || lower.contains("melhorou") -> {
                val current = patient.evolutionText.trim()
                val template = "Paciente evolui em melhora clínica progressiva nas últimas horas. Encontra-se cooperativo, orientado, hemodinamicamente estável, afebril, eupnéico em ar ambiente. Refere cessação completa de queixas de dor."
                onValueUpdate("evolutionText", if (current.isEmpty()) template else "$current\n$template")
                recognizedCommandLog = "✨ Aplicou modelo: Melhora Progressiva"
            }
            lower.contains("paciente estável") || lower.contains("quadro estável") || lower.contains("modelo estável") || lower.contains("estável") -> {
                val current = patient.evolutionText.trim()
                val template = "Mantém quadro clínico estável sob monitoramento multiparamétrico contínuo de sinais vitais. Consciente, orientado, fotorreagente, perfusão periférica adequada (< 2s). Aceitando dieta habitual."
                onValueUpdate("evolutionText", if (current.isEmpty()) template else "$current\n$template")
                recognizedCommandLog = "✨ Aplicou modelo: Quadro Estável"
            }
            lower.contains("paciente piorou") || lower.contains("piora progressiva") || lower.contains("piora respiratória") || lower.contains("modelo piora") || lower.contains("piora") -> {
                val current = patient.evolutionText.trim()
                val template = "Paciente evolui com piora progressiva do padrão respiratório, apresentando taquipneia associada a tiragem intercostal, batimento de asa de nariz e sutil dessaturação (SpO2 89%). Aventada conduta de suporte de oxigênio sob máscara com reservatório e gasometria."
                onValueUpdate("evolutionText", if (current.isEmpty()) template else "$current\n$template")
                recognizedCommandLog = "⚠️ Aplicou modelo: Piora Ventilatória"
            }
            lower.contains("adicionar dipirona") -> {
                viewModel.addHospitalPrescription("Dipirona", "500mg IV", "De 6 em 6 horas", "IV/EV")
                recognizedCommandLog = "💊 Prescreveu: Dipirona 500mg IV (6/6h)"
            }
            lower.contains("adicionar losartana") -> {
                viewModel.addHospitalPrescription("Losartana", "50mg VO", "De 12 em 12 horas", "VO")
                recognizedCommandLog = "💊 Prescreveu: Losartana 50mg VO (12/12h)"
            }
            lower.contains("chamar ia") || lower.contains("gerar parecer") || lower.contains("gerar ia") || lower.contains("analisar com ia") -> {
                recognizedCommandLog = "🧠 Solicitou parecer estruturado com IA..."
                viewModel.generateAIClinicalGuidance()
                viewModel.setStageIndex(7) // Jump to GuidanceView
            }
            lower.contains("avançar de tela") || lower.contains("avançar tela") || lower.contains("próximo") || lower.contains("avançar") -> {
                recognizedCommandLog = "➡️ Avançou para a próxima etapa"
                val currentIdx = viewModel.currentStageIndex.value
                if (currentIdx < WORKFLOW_STAGES.size - 1) viewModel.setStageIndex(currentIdx + 1)
            }
            lower.contains("voltar tela") || lower.contains("voltar de tela") || lower.contains("anterior") || lower.contains("voltar") -> {
                recognizedCommandLog = "⬅️ Voltou para a etapa anterior"
                val currentIdx = viewModel.currentStageIndex.value
                if (currentIdx > 0) viewModel.setStageIndex(currentIdx - 1)
            }
            lower.contains("ajuda") || lower.contains("comandos") -> {
                showHelpDialog = true
                recognizedCommandLog = "❓ Exibindo menu de ajuda"
            }
            else -> {
                // Regular dictation mode (append spoken text capitalized)
                val current = patient.evolutionText.trim()
                val capitalized = fullSentence.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                val nextText = if (current.isEmpty()) capitalized else "$current $capitalized"
                onValueUpdate("evolutionText", nextText)
                recognizedCommandLog = "🗣️ Ditou: \"$fullSentence\""
            }
        }
    }

    val voiceHelper = remember {
        VoiceSpeechHelper(context) { text, isFinal ->
            if (isFinal) {
                partialSpokenText = ""
                processDetectedSentence(text)
            } else {
                partialSpokenText = text
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.destroy()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Evolução Médica & Copiloto por Voz", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Dite notas de evolução ou execute comandos clínicos usando comandos sonoros rápidos.", fontSize = 12.sp, color = Color.Gray)
                }

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(Icons.Default.Help, contentDescription = "Menu de Ajuda de Voz", tint = Color(0xFF00D4AA))
                }
            }
        }

        // Notes Editor text field
        item {
            OutlinedTextField(
                value = patient.evolutionText,
                onValueChange = { onValueUpdate("evolutionText", it) },
                label = { Text("Nota de Evolução Clínica") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00D4AA),
                    focusedLabelColor = Color(0xFF00D4AA)
                ),
                trailingIcon = {
                    if (patient.evolutionText.isNotEmpty()) {
                        IconButton(onClick = { onValueUpdate("evolutionText", "") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar Campo", tint = Color.Gray)
                        }
                    }
                }
            )
        }

        item {
            Button(
                onClick = {
                    if (patient.evolutionText.isNotBlank()) {
                        viewModel.savePatientEvolution(patient.evolutionText)
                        onValueUpdate("evolutionText", "")
                        Toast.makeText(context, "Evolução salva no histórico!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = patient.evolutionText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4AA),
                    disabledContainerColor = Color(0xFF00D4AA).copy(alpha = 0.3f),
                    contentColor = Color(0xFF0F172A),
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Salvar Evolução",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gravar Evolução no Histórico do Paciente", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Voice Assistant & Command Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11192E)),
                border = BorderStroke(1.dp, Color(0xFF233554))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ASSISTENTE VIRTUAL POR VOZ ULTRA-PRO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4AA),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsating Circular Mic Trigger
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(
                                    if (voiceHelper.isListening) {
                                        Color(0xFFEF4444).copy(alpha = 0.2f)
                                    } else {
                                        Color(0xFF00D4AA).copy(alpha = 0.15f)
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        width = if (voiceHelper.isListening) 3.dp else 1.dp,
                                        color = if (voiceHelper.isListening) Color(0xFFEF4444) else Color(0xFF00D4AA)
                                    ),
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (!hasMicPermission) {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        if (voiceHelper.isListening) {
                                            voiceHelper.stopListening()
                                        } else {
                                            voiceHelper.startListening()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (voiceHelper.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = if (voiceHelper.isListening) "Ouvindo" else "Ditar",
                                tint = if (voiceHelper.isListening) Color(0xFFEF4444) else Color(0xFF00D4AA),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        if (voiceHelper.isListening) {
                            Spacer(modifier = Modifier.width(16.dp))
                            VoiceSoundbar(rmsLevel = voiceHelper.rmsLevel)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Label
                    Text(
                        text = when {
                            !hasMicPermission -> "⚠️ Permissão de microfone não concedida. Toque no botão acima para permitir o áudio."
                            voiceHelper.isListening -> "🟢 Ouvindo... fale agora a evolução ou comando"
                            voiceHelper.errorText != null -> "🔴 ${voiceHelper.errorText}"
                            else -> "🎙️ Toque para ativar ditado por microfone"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (!hasMicPermission) Color(0xFFF87171) else if (voiceHelper.isListening) Color(0xFFEF4444) else Color.LightGray,
                        textAlign = TextAlign.Center
                    )

                    // Show partial spoken text live
                    if (partialSpokenText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B1220))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Ouvindo: \"$partialSpokenText\"",
                                fontSize = 12.sp,
                                color = Color(0xFF00D4AA),
                                style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )
                        }
                    }

                    // Feed back of last successfully executed command
                    recognizedCommandLog?.let { log ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = log,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Simulated Command Panel Toggle
                    Button(
                        onClick = { showSimulationPanel = !showSimulationPanel },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showSimulationPanel) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (showSimulationPanel) "Ocultar Atalhos de Toque" else "Ativar Atos rápidos por toque (Simulação de voz) 🗣️",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    AnimatedVisibility(visible = showSimulationPanel) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "Clique abaixo para ditar ou injetar ações instantâneas no prontuário (ideal para teste e homologação ágil no navegador):",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val simulations = listOf(
                                    "🗣️ Melhora Progressiva" to "paciente melhorou",
                                    "🗣️ Quadro Estável" to "paciente estável",
                                    "🗣️ Piora Ventilatória" to "paciente piorou",
                                    "💊 Losartana 50mg" to "adicionar losartana",
                                    "💊 Dipirona 500mg" to "adicionar dipirona",
                                    "🧠 Chamar Parecer IA" to "chamar ia",
                                    "💾 Gravar Evolução" to "registrar evolução",
                                    "♻️ Limpar Nota" to "apagar tudo",
                                    "➡️ Avançar" to "avançar",
                                    "⬅️ Voltar" to "voltar",
                                    "❓ Menu de Atalhos" to "ajuda"
                                )

                                for ((label, triggerText) in simulations) {
                                    AssistChip(
                                        onClick = {
                                            processDetectedSentence(triggerText)
                                            Toast.makeText(context, "Simulado: \"$triggerText\"", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White) },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E293B)),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Standard Quick selection clinical model tags
        item {
            Text("Modelos Médicos de Texto (Uso tradicional):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        val quickModels = listOf(
            "Paciente evolui com melhora clínica nas últimas horas. Sem queixas álgicas.",
            "Mantém quadro estável em monitoramento contínuo. Regulando exames de controle.",
            "Evolui com piora progressiva do padrão respiratório e necessidade de suporte ventilatório."
        )

        items(quickModels) { model ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onValueUpdate("evolutionText", model) },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                border = BorderStroke(1.dp, Color(0xFF22324C))
            ) {
                Text(text = model, fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(12.dp))
            }
        }

        // --- HISTÓRICO DE EVOLUÇÕES DOS PACIENTES ---
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Histórico",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Histórico de Evoluções (${evolutions.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (evolutions.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.clearPatientEvolutions()
                            Toast.makeText(context, "Histórico limpo com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Limpar Tudo",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar Tudo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (evolutions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31).copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, Color(0xFF22324C).copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "",
                                tint = Color.Gray,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Nenhuma nota no histórico clínico ainda.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Escreva ou dite e clique em 'Gravar' para documentar.",
                                fontSize = 11.sp,
                                color = Color.Gray.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(evolutions) { ev ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                    border = BorderStroke(1.dp, Color(0xFF22324C))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "",
                                    tint = Color(0xFF00D4AA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date(ev.timestamp))
                                Text(
                                    text = timeStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00D4AA)
                                )
                            }

                            Row {
                                // Restore/Copy button (allows re-editing or re-filling the editor if needed)
                                IconButton(
                                    onClick = {
                                        onValueUpdate("evolutionText", ev.text)
                                        Toast.makeText(context, "Copiado para o editor!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Copiar para Editor",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        viewModel.deletePatientEvolution(ev)
                                        Toast.makeText(context, "Evolução excluída!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Evolução",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = ev.text,
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsVoice, contentDescription = "", tint = Color(0xFF00D4AA))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guia de Voz & Atalhos Clínicos")
                }
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    item {
                        Text(
                            text = "Dite livremente a nota clínica ou fale um dos comandos pré-definidos para comandar o prontuário por voz:",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    val helpCommands = listOf(
                        "\"apagar tudo\"" to "Zera toda a nota digitada no campo de texto.",
                        "\"modelo melhora\" ou \"melhorou\"" to "Aplica uma evolução clínica detalhada de melhora clínica do paciente.",
                        "\"modelo estável\" ou \"estável\"" to "Insere uma nota médica padrão descrevendo quadro de estabilidade geral.",
                        "\"modelo piora\" ou \"piorou\"" to "Adiciona nota clínica expressando piora do padrão respiratório/ventilatório.",
                        "\"adicionar dipirona\"" to "Prescreve Dipirona na base hospitalar ativa.",
                        "\"adicionar losartana\"" to "Prescreve Losartana na base hospitalar ativa.",
                        "\"gerar parecer\" ou \"chamar ia\"" to "Executa a IA (Gemini) para avaliar o prontuário e pula para a tela de Parecer IA.",
                        "\"avançar\"" to "Muda para a aba seguinte do prontuário.",
                        "\"voltar\"" to "Retorna para a aba anterior do prontuário."
                    )

                    items(helpCommands) { (phrase, explanation) ->
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Text(phrase, fontWeight = FontWeight.Bold, color = Color(0xFF00D4AA), fontSize = 13.sp)
                            Text(explanation, color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA))
                ) {
                    Text("Entendido", color = Color(0xFF0F172A))
                }
            }
        )
    }
}

@Composable
fun MedicationReconciliationView(
    viewModel: PatientViewModel,
    homeMeds: List<HomeMedication>,
    hospitalPrescriptions: List<HospitalPrescription>,
    doseAdministrations: List<DoseAdministration>,
    alerts: List<SafetyAlert>
) {
    val context = LocalContext.current
    var showAddHomeMedDialog by remember { mutableStateOf(false) }
    var showAddHospitalPresDialog by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // RADIAN RECONCILIATION CLINICAL TITLE Banner
        item {
            Text("Reconciliação & Evitação de Duplicidades", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Cruze os medicamentos domiciliares com a prescrição ativa do hospital para travar duplicidades e omissões.", fontSize = 12.sp, color = Color.Gray)
        }

        // SAFETY INTERRUPTER ALERTS DISPLAY (Prevenção de Duplicidades)
        item {
            if (alerts.isNotEmpty()) {
                Text(
                    text = "ALERTAS DE SEGURANÇA DETECTADOS (${alerts.size}):",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF97316),
                    modifier = Modifier.padding(bottom = 6.dp),
                    fontSize = 12.sp
                )

                for (alert in alerts) {
                    val colorContainer = when (alert.severity) {
                        AlertSeverity.HIGH -> Color(0xFF450A0A)
                        AlertSeverity.MEDIUM -> Color(0xFF451A03)
                        AlertSeverity.LOW -> Color(0xFF111827)
                    }
                    val colorBorder = when (alert.severity) {
                        AlertSeverity.HIGH -> Color(0xFFEF4444)
                        AlertSeverity.MEDIUM -> Color(0xFFF97316)
                        AlertSeverity.LOW -> Color(0xFF4b5563)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colorContainer),
                        border = BorderStroke(1.5.dp, colorBorder)
                    ) {
                        Row(modifier = Modifier.padding(14.dp)) {
                            Icon(
                                imageVector = if (alert.severity == AlertSeverity.HIGH) Icons.Default.Warning else Icons.Default.Info,
                                contentDescription = "",
                                tint = colorBorder,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(alert.title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(alert.description, fontSize = 12.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                    border = BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = "", tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Reconciliação segura: Nenhum conflito ou duplicidade identificados.", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            }
        }

        // PART A: HOME MEDICATIONS RECONCILIATION
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("1. Medicamentos de Uso Contínuo (Domiciliar):", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Button(
                    onClick = { showAddHomeMedDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(16.dp))
                    Text("De Uso Contínuo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (homeMeds.isEmpty()) {
            item {
                Text("Nenhum medicamento de uso contínuo inserido.", color = Color.Gray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        } else {
            items(homeMeds) { med ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                    border = BorderStroke(1.dp, Color(0xFF233656))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(med.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Dose: ${med.dosage} (${med.frequency})", fontSize = 12.sp, color = Color.LightGray)
                            }

                            // Dynamic state badge
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (med.action) {
                                        "Prescrito no Hospital" -> Color(0xFF064E3B)
                                        "Pausado/Suspenso" -> Color(0xFF7C2D12)
                                        "Substituído" -> Color(0xFF1E3A8A)
                                        "Omitido" -> Color(0xFF374151)
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                            ) {
                                Text(
                                    text = med.action,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(onClick = { viewModel.deleteHomeMedication(med) }) {
                                Icon(Icons.Default.Delete, contentDescription = "", tint = Color(0xFFEF4444).copy(alpha = 0.8f))
                            }
                        }

                        if (med.justification.isNotEmpty()) {
                            Text("Justificativa: ${med.justification}", fontSize = 11.sp, color = Color.Yellow, modifier = Modifier.padding(top = 4.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decisão na Admissão:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Interactive Reconciliation Switches
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.reconcileHomeMedication(med, "Prescrito no Hospital") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Prescrever", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    // Suspend/Pause trigger with standard justification
                                    viewModel.reconcileHomeMedication(med, "Pausado/Suspenso", "Pausado temporariamente devido ao protocolo cirúrgico hospitalar")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Pausar", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Button(
                                onClick = { viewModel.reconcileHomeMedication(med, "Substituído", "Substituído por análogo de controle rápido hospitalar") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Substituir", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Button(
                                onClick = { viewModel.reconcileHomeMedication(med, "Omitido", "Omitido deliberadamente por critérios clínicos") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Omitir", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // PART B: HOSPITAL ACTIVE PRESCRIPTIONS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("2. Prescrição de Medicamentos Hospitalares:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Button(
                    onClick = { showAddHospitalPresDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(16.dp))
                    Text("Adicionar Prescrição", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (hospitalPrescriptions.isEmpty()) {
            item {
                Text("Nenhuma receita hospitalar ativa.", color = Color.Gray, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        } else {
            items(hospitalPrescriptions) { prescription ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10192C)),
                    border = BorderStroke(1.dp, Color(0xFF223656))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(prescription.name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Ingrediente ativo: ${prescription.activeIngredient}", fontSize = 11.sp, color = Color(0xFF00D4AA))
                            Text("Dose: ${prescription.dosage} | Fluxo: ${prescription.frequency} via ${prescription.route}", fontSize = 12.sp, color = Color.LightGray)
                        }

                        // Simulate Quick Administration block
                        Button(
                            onClick = {
                                viewModel.administerDose(
                                    prescriptionName = prescription.name,
                                    dosage = prescription.dosage,
                                    activeIngredient = prescription.activeIngredient
                                )
                                Toast.makeText(context, "Dose administrada e salva no registro!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF00D4AA)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Checar", color = Color(0xFF00D4AA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = { viewModel.deleteHospitalPrescription(prescription) }) {
                            Icon(Icons.Default.Delete, contentDescription = "", tint = Color(0xFFEF4444).copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // PART C: ADMINISTRATION TIME CHECKS Log
        item {
            Text("3. Últimas Doses Administradas no Leito:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
        }

        if (doseAdministrations.isEmpty()) {
            item {
                Text("Nenhuma administração efetuada neste turno.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            items(doseAdministrations) { admin ->
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(admin.administeredAt))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111E2E)),
                    border = BorderStroke(1.dp, Color(0xFF22324C))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text("${admin.prescriptionName} (${admin.dosage})", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text("Afeição: ${admin.nurseSignature}", fontSize = 11.sp, color = Color.Gray)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(timeStr, color = Color(0xFF00D4AA), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Estornar",
                                color = Color.Red,
                                fontSize = 10.sp,
                                modifier = Modifier.clickable { viewModel.deleteDoseAdministration(admin) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddHomeMedDialog) {
        AddHomeMedDialog(
            onDismiss = { showAddHomeMedDialog = false },
            onConfirm = { name, dosage, frequency ->
                viewModel.addHomeMedication(name, dosage, frequency)
                showAddHomeMedDialog = false
            }
        )
    }

    if (showAddHospitalPresDialog) {
        AddHospitalPrescriptionDialog(
            onDismiss = { showAddHospitalPresDialog = false },
            onConfirm = { name, dosage, frequency, route ->
                viewModel.addHospitalPrescription(name, dosage, frequency, route)
                showAddHospitalPresDialog = false
            }
        )
    }
}

@Composable
fun AddHomeMedDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2D))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Medicamento Domiciliar", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Medicamento (Ex: Losartana / Glifage)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosagem (Ex: 50mg / 850mg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = freq,
                    onValueChange = { freq = it },
                    label = { Text("Frequência de uso (Ex: 12/12h, 1x ao dia)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Voltar") }
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name, dosage, freq) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Inserir", color = Color(0xFF0F172A))
                    }
                }
            }
        }
    }
}

@Composable
fun AddHospitalPrescriptionDialog(onDismiss: () -> Unit, onConfirm: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("VO") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2D))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Prescrição Hospitalar Activa", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome/Princípio Ativo (Ex: Aradois / Dipirona)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("Dosagem (Ex: 1g IV, 50mg VO)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = freq,
                    onValueChange = { freq = it },
                    label = { Text("Sob demanda ou Horários (Ex: de 6/6h, 1x ao dia)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Via: ", color = Color.White, modifier = Modifier.padding(end = 12.dp))
                    for (item in listOf("VO", "IV", "IM", "SC")) {
                        val isSelected = route == item
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF00D4AA) else Color(0xFF1E293B))
                                .clickable { route = item }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .padding(end = 4.dp)
                        ) {
                            Text(item, color = if (isSelected) Color(0xFF0F172A) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Voltar") }
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name, dosage, freq, route) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Adicionar", color = Color(0xFF0F172A))
                    }
                }
            }
        }
    }
}

@Composable
fun GuidanceView(
    patient: Patient,
    viewModel: PatientViewModel,
    isGenerating: Boolean,
    error: String?,
    onValueUpdate: (String, String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            // IA Disclaimer banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2D3D)),
                border = BorderStroke(1.dp, Color(0xFF00A383))
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Info, contentDescription = "", tint = Color(0xFF00D4AA))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AVISO DE SUPORTE CLÍNICO (INTELIGÊNCIA ARTIFICIAL)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "As recomendações automáticas funcionam como ferramenta auxiliar. Não substituem o discernimento e a avaliação presencial do médico responsável.",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.generateAIClinicalGuidance() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color(0xFF0F172A), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerando laudo clínico seguro...", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = "", tint = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar Parecer de Segurança Medicamentosa (IA)", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (error != null) {
            item {
                Text(error, color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        item {
            OutlinedTextField(
                value = patient.clinicalGuidanceText,
                onValueChange = { onValueUpdate("clinicalGuidanceText", it) },
                label = { Text("Parecer de Reconciliação Consolidado") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                placeholder = { Text("O parecer gerado por IA com instruções de alta ou internação aparecerá aqui...") },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        item {
            Text("Modelos de Desfechos Rápidos:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(6.dp))

            val outcomes = listOf(
                "DESFECHO CLÍNICO: Alta hospitalar segura com receituário de sintomáticos ambulatoriais e agendada revisão em 7 dias.",
                "DESFECHO CLÍNICO: Indicada internação hospitalar em enfermaria médica para otimização terapêutica e exames complementares.",
                "DESFECHO CLÍNICO: Transferência programada à Unidade de Terapia Intensiva (UTI) por instabilidade clinica de Sinais Vitais."
            )

            for (text in outcomes) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val current = patient.clinicalGuidanceText.trim()
                            val joined = if (current.isEmpty()) text else "$current\n\n$text"
                            onValueUpdate("clinicalGuidanceText", joined)
                        }
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31))
                ) {
                    Text(text, fontSize = 12.sp, color = Color.LightGray, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
fun FullReportModal(
    patient: Patient,
    homeMeds: List<HomeMedication>,
    hospitalPres: List<HospitalPrescription>,
    adminLog: List<DoseAdministration>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val reportText = remember {
        buildString {
            append("=================================================\n")
            append("           FOLHA DE EVOLUÇÃO E RECONCILIAÇÃO      \n")
            append("           SISTEMA ULTRA-PRO CLÍNICA 4.0          \n")
            append("=================================================\n")
            append("Data/Hora: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")

            append("[ INFOS DO PACIENTE ]\n")
            append("Nome: ${patient.name.ifEmpty { "Pendente" }}\n")
            append("Leito: ${patient.bed.ifEmpty { "Não classificado" }}\n")
            if (patient.history.isNotEmpty()) append("Comorbidades / HMP: ${patient.history}\n")
            if (patient.triageClass.isNotEmpty()) append("Manchester: ${patient.triageClass}\n")
            if (patient.chiefComplaint.isNotEmpty()) append("Queixa Principal: ${patient.chiefComplaint}\n")
            if (patient.allergies.isNotEmpty()) append("Alergias Medicamentosas: ${patient.allergies}\n\n")

            append("[ SINAIS VITAIS ]\n")
            append("PA: ${patient.pa.ifEmpty { "90/60" }} mmHg | FC: ${patient.fc.ifEmpty { "80" }} bpm | Temp: ${patient.temp.ifEmpty { "36.5" }} °C | SpO2: ${patient.spo2.ifEmpty { "98" }}% | Glasgow: ${patient.glasgow.ifEmpty { "15" }}\n\n")

            append("[ EXAME FÍSICO INTEGRADO ]\n")
            if (patient.examGeral.isNotEmpty()) append("Geral: ${patient.examGeral}\n")
            if (patient.examResp.isNotEmpty()) append("Resp: ${patient.examResp}\n")
            if (patient.examCardio.isNotEmpty()) append("Cardio: ${patient.examCardio}\n")
            if (patient.examAbd.isNotEmpty()) append("Abd: ${patient.examAbd}\n")
            if (patient.examNeuro.isNotEmpty()) append("Neuro: ${patient.examNeuro}\n\n")

            if (patient.labsJson.isNotEmpty() && patient.labsJson != "[]") {
                append("[ EXAMES LABORATORIAIS ]\n")
                try {
                    val arr = JSONArray(patient.labsJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        append("  - ${obj.getString("name")}: ${obj.getString("value")} ${obj.getString("unit")} (Ref: ${obj.getString("ref")})\n")
                    }
                } catch (e: Exception) {
                    append("Erro ao ler exames\n")
                }
                append("\n")
            }

            if (homeMeds.isNotEmpty()) {
                append("[ RECONCILIAÇÃO DE MEDICAMENTOS DOMICILIARES ]\n")
                homeMeds.forEach {
                    append("  * Domiciliar: ${it.name} ${it.dosage} (${it.frequency}) -> Ação Admissional: ${it.action}\n")
                    if (it.justification.isNotEmpty()) append("    Justificativa clínica: ${it.justification}\n")
                }
                append("\n")
            }

            if (hospitalPres.isNotEmpty()) {
                append("[ PRESCRIÇÃO HOSPITALAR EDITADA ]\n")
                hospitalPres.forEach {
                    append("  * ${it.name} ${it.dosage} (${it.frequency}) via ${it.route}\n")
                }
                append("\n")
            }

            if (adminLog.isNotEmpty()) {
                append("[ REGISTRO DE DOSES CHECKADAS (ENFERMAGEM) ]\n")
                adminLog.forEach {
                    val dt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.administeredAt))
                    append("  * [$dt] ${it.prescriptionName} (${it.dosage}) - ${it.nurseSignature}\n")
                }
                append("\n")
            }

            if (patient.evolutionText.isNotEmpty()) {
                append("[ NOTA DE EVOLUÇÃO CLÍNICA ]\n")
                append(patient.evolutionText)
                append("\n\n")
            }

            if (patient.clinicalGuidanceText.isNotEmpty()) {
                append("[ DIRETRIZES DE SAÚDE, RECONCILIAÇÃO & ALTA ]\n")
                append(patient.clinicalGuidanceText)
                append("\n\n")
            }

            append("=================================================\n")
            append("Médico Responsável: _____________________________\n")
            append("CRM/Assinatura:")
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sumário do Prontuário", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable text report
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF070B14))
                        .padding(12.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = reportText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Retornar à Edição", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText))
                            Toast.makeText(context, "Sumário copiado para área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4AA)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "", tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Tudo", color = Color(0xFF0F172A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
