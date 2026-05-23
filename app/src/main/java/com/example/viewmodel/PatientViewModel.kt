package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DrugInfo(
    val activeIngredient: String,
    val therapeuticClass: String
)

data class SafetyAlert(
    val type: AlertType,
    val title: String,
    val description: String,
    val severity: AlertSeverity
)

enum class AlertType {
    OMISSION, // Home med not reconciled/prescribed
    DUPLICATE_INGREDIENT, // Same ingredient active in multiple prescriptions
    CLASS_OVERLAP, // Same therapeutic class active
    INTERVAL_WARNING // Administration given too early
}

enum class AlertSeverity {
    HIGH, // Red alert: active ingredient duplicate
    MEDIUM, // Orange alert: same class overlap or interval risk
    LOW // Yellow info
}

class PatientViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PatientDatabase.getDatabase(application)
    private val repository = PatientRepository(db.patientDao())

    // List of all patients
    val allPatients: StateFlow<List<Patient>> = repository.allPatients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Patient
    private val _selectedPatient = MutableStateFlow<Patient?>(null)
    val selectedPatient: StateFlow<Patient?> = _selectedPatient.asStateFlow()

    // Current navigation stage index
    private val _currentStageIndex = MutableStateFlow(0)
    val currentStageIndex: StateFlow<Int> = _currentStageIndex.asStateFlow()

    // Stage variables for current selection
    private val _homeMedications = MutableStateFlow<List<HomeMedication>>(emptyList())
    val homeMedications: StateFlow<List<HomeMedication>> = _homeMedications.asStateFlow()

    private val _hospitalPrescriptions = MutableStateFlow<List<HospitalPrescription>>(emptyList())
    val hospitalPrescriptions: StateFlow<List<HospitalPrescription>> = _hospitalPrescriptions.asStateFlow()

    private val _doseAdministrations = MutableStateFlow<List<DoseAdministration>>(emptyList())
    val doseAdministrations: StateFlow<List<DoseAdministration>> = _doseAdministrations.asStateFlow()

    private val _patientEvolutions = MutableStateFlow<List<PatientEvolution>>(emptyList())
    val patientEvolutions: StateFlow<List<PatientEvolution>> = _patientEvolutions.asStateFlow()

    // Safety Alertscomputed dynamically
    private val _safetyAlerts = MutableStateFlow<List<SafetyAlert>>(emptyList())
    val safetyAlerts: StateFlow<List<SafetyAlert>> = _safetyAlerts.asStateFlow()

    // AI generating clinical guidance progress
    private val _isGeneratingGuidance = MutableStateFlow(false)
    val isGeneratingGuidance: StateFlow<Boolean> = _isGeneratingGuidance.asStateFlow()

    private val _generationError = MutableStateFlow<String?>(null)
    val generationError: StateFlow<String?> = _generationError.asStateFlow()

    // Built-in pharmacological dictionary
    private val drugDictionary = mapOf(
        "losartana" to DrugInfo("Losartana", "Anti-hipertensivo (BRA)"),
        "aradois" to DrugInfo("Losartana", "Anti-hipertensivo (BRA)"),
        "cozar" to DrugInfo("Losartana", "Anti-hipertensivo (BRA)"),
        "metformina" to DrugInfo("Metformina", "Hipoglicemiante oral"),
        "glifage" to DrugInfo("Metformina", "Hipoglicemiante oral"),
        "dipirona" to DrugInfo("Dipirona", "Analgésico / Antitérmico"),
        "novalgina" to DrugInfo("Dipirona", "Analgésico / Antitérmico"),
        "paracetamol" to DrugInfo("Paracetamol", "Analgésico / Antitérmico"),
        "tylenol" to DrugInfo("Paracetamol", "Analgésico / Antitérmico"),
        "ibuprofeno" to DrugInfo("Ibuprofeno", "Anti-inflamatório (AINE)"),
        "advil" to DrugInfo("Ibuprofeno", "Anti-inflamatório (AINE)"),
        "cetoprofeno" to DrugInfo("Cetoprofeno", "Anti-inflamatório (AINE)"),
        "profenid" to DrugInfo("Cetoprofeno", "Anti-inflamatório (AINE)"),
        "enalapril" to DrugInfo("Enalapril", "Anti-hipertensivo (IECA)"),
        "captopril" to DrugInfo("Captopril", "Anti-hipertensivo (IECA)"),
        "anlodipino" to DrugInfo("Anlodipino", "Anti-hipertensivo (Ancor)"),
        "atenolol" to DrugInfo("Atenolol", "Beta-bloqueador"),
        "atenuol" to DrugInfo("Atenolol", "Beta-bloqueador"),
        "insulina" to DrugInfo("Insulina", "Hipoglicemiante"),
        "lantus" to DrugInfo("Insulina", "Hipoglicemiante"),
        "novorapid" to DrugInfo("Insulina", "Hipoglicemiante"),
        "omeprazol" to DrugInfo("Omeprazol", "Protetor Gástrico (IBP)"),
        "pantoprazol" to DrugInfo("Pantoprazol", "Protetor Gástrico (IBP)"),
        "ranitidina" to DrugInfo("Ranitidina", "Protetor Gástrico (Anti-H2)"),
        "amoxicilina" to DrugInfo("Amoxicilina", "Antibiótico (Penicilina)"),
        "clavulin" to DrugInfo("Amoxicilina", "Antibiótico (Penicilina)"),
        "ciprofloxacino" to DrugInfo("Ciprofloxacino", "Antibiótico (Quinolona)"),
        "cefalezin" to DrugInfo("Cefalexina", "Antibiótico (Cefalosporina)"),
        "clopidogrel" to DrugInfo("Clopidogrel", "Antiagregante plaquetário"),
        "plavix" to DrugInfo("Clopidogrel", "Antiagregante plaquetário"),
        "aas" to DrugInfo("Ácido Acetilsalicílico", "Antiagregante / Analgésico"),
        "aspirina" to DrugInfo("Ácido Acetilsalicílico", "Antiagregante / Analgésico")
    )

    init {
        // Collect safety alerts automatically when medications/prescriptions change
        viewModelScope.launch {
            combine(
                _homeMedications,
                _hospitalPrescriptions,
                _doseAdministrations
            ) { home, hospital, admin ->
                computeSafetyAlerts(home, hospital, admin)
            }.collect { alerts ->
                _safetyAlerts.value = alerts
            }
        }
    }

    fun selectPatient(patient: Patient?) {
        _selectedPatient.value = patient
        if (patient != null) {
            // Subscribe to patient's medication sub-states
            viewModelScope.launch {
                repository.getHomeMedications(patient.id).collect {
                    _homeMedications.value = it
                }
            }
            viewModelScope.launch {
                repository.getHospitalPrescriptions(patient.id).collect {
                    _hospitalPrescriptions.value = it
                }
            }
            viewModelScope.launch {
                repository.getDoseAdministrations(patient.id).collect {
                    _doseAdministrations.value = it
                }
            }
            viewModelScope.launch {
                repository.getPatientEvolutions(patient.id).collect {
                    _patientEvolutions.value = it
                }
            }
        } else {
            _homeMedications.value = emptyList()
            _hospitalPrescriptions.value = emptyList()
            _doseAdministrations.value = emptyList()
            _patientEvolutions.value = emptyList()
            _currentStageIndex.value = 0
        }
    }

    fun setStageIndex(index: Int) {
        _currentStageIndex.value = index
    }

    fun createPatient(name: String, bed: String) {
        viewModelScope.launch {
            val newPatient = Patient(name = name, bed = bed)
            val id = repository.insertPatient(newPatient)
            val created = repository.getPatientById(id)
            selectPatient(created)
        }
    }

    fun updatePatientField(key: String, value: String) {
        val current = _selectedPatient.value ?: return
        viewModelScope.launch {
            val updated = when (key) {
                "name" -> current.copy(name = value)
                "bed" -> current.copy(bed = value)
                "birthDate" -> current.copy(birthDate = value)
                "allergies" -> current.copy(allergies = value)
                "history" -> current.copy(history = value)
                "triageClass" -> current.copy(triageClass = value)
                "chiefComplaint" -> current.copy(chiefComplaint = value)
                "pa" -> current.copy(pa = value)
                "fc" -> current.copy(fc = value)
                "temp" -> current.copy(temp = value)
                "spo2" -> current.copy(spo2 = value)
                "pain" -> current.copy(pain = value)
                "glasgow" -> current.copy(glasgow = value)
                "examGeral" -> current.copy(examGeral = value)
                "examResp" -> current.copy(examResp = value)
                "examCardio" -> current.copy(examCardio = value)
                "examAbd" -> current.copy(examAbd = value)
                "examNeuro" -> current.copy(examNeuro = value)
                "evolutionText" -> current.copy(evolutionText = value)
                "clinicalGuidanceText" -> current.copy(clinicalGuidanceText = value)
                "labsJson" -> current.copy(labsJson = value)
                "customExamsJson" -> current.copy(customExamsJson = value)
                else -> current
            }
            repository.updatePatient(updated)
            _selectedPatient.value = updated
        }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch {
            if (_selectedPatient.value?.id == patient.id) {
                selectPatient(null)
            }
            repository.deletePatient(patient)
        }
    }

    // --- Home Medications Manager ---
    fun addHomeMedication(name: String, dosage: String, frequency: String) {
        val p = _selectedPatient.value ?: return
        viewModelScope.launch {
            val med = HomeMedication(
                patientId = p.id,
                name = name,
                dosage = dosage,
                frequency = frequency,
                action = "Pendente"
            )
            repository.insertHomeMedication(med)
        }
    }

    fun reconcileHomeMedication(med: HomeMedication, action: String, justification: String = "") {
        viewModelScope.launch {
            val updated = med.copy(action = action, justification = justification)
            repository.insertHomeMedication(updated)
            
            // Auto Prescribe option: if action is "Prescrito no Hospital", add to hospital prescriptions automatically!
            if (action == "Prescrito no Hospital") {
                val resolved = resolveDrugBrand(med.name)
                val exists = _hospitalPrescriptions.value.any {
                    it.activeIngredient.lowercase() == resolved.activeIngredient.lowercase()
                }
                if (!exists) {
                    addHospitalPrescriptionInternal(med.name, med.dosage, med.frequency, "VO")
                }
            }
        }
    }

    fun deleteHomeMedication(med: HomeMedication) {
        viewModelScope.launch {
            repository.deleteHomeMedication(med)
        }
    }

    // --- Hospital Prescriptions Manager ---
    fun addHospitalPrescription(name: String, dosage: String, frequency: String, route: String) {
        viewModelScope.launch {
            addHospitalPrescriptionInternal(name, dosage, frequency, route)
        }
    }

    private suspend fun addHospitalPrescriptionInternal(name: String, dosage: String, frequency: String, route: String) {
        val p = _selectedPatient.value ?: return
        val resolved = resolveDrugBrand(name)
        val prescription = HospitalPrescription(
            patientId = p.id,
            name = name,
            activeIngredient = resolved.activeIngredient,
            dosage = dosage,
            frequency = frequency,
            route = route
        )
        repository.insertHospitalPrescription(prescription)
    }

    fun deleteHospitalPrescription(prescription: HospitalPrescription) {
        viewModelScope.launch {
            repository.deleteHospitalPrescription(prescription)
        }
    }

    // --- Dose Administrations Manager ---
    fun administerDose(prescriptionName: String, dosage: String, activeIngredient: String) {
        val p = _selectedPatient.value ?: return
        viewModelScope.launch {
            val dose = DoseAdministration(
                patientId = p.id,
                prescriptionName = prescriptionName,
                activeIngredient = activeIngredient,
                dosage = dosage,
                administeredAt = System.currentTimeMillis()
            )
            repository.insertDoseAdministration(dose)
        }
    }

    fun deleteDoseAdministration(admin: DoseAdministration) {
        viewModelScope.launch {
            repository.deleteDoseAdministration(admin)
        }
    }

    // Pharmacological Brand resolution
    fun resolveDrugBrand(medName: String): DrugInfo {
        val cleanName = medName.trim().lowercase()
        return drugDictionary[cleanName] ?: DrugInfo(
            activeIngredient = medName.trim(), // fallback to direct name
            therapeuticClass = "Não Categorizado"
        )
    }

    // Dynamic Clinical Safety and DoubleDose checking Engine
    private fun computeSafetyAlerts(
        home: List<HomeMedication>,
        hospital: List<HospitalPrescription>,
        admin: List<DoseAdministration>
    ): List<SafetyAlert> {
        val alerts = mutableListOf<SafetyAlert>()

        // 1. RECONCILIATION OMISSION ALERTS
        home.forEach { homeMed ->
            val resolvedHome = resolveDrugBrand(homeMed.name)
            
            // Check if home medication has been reconciled/referenced
            if (homeMed.action == "Pendente") {
                alerts.add(
                    SafetyAlert(
                        type = AlertType.OMISSION,
                        title = "Medicamento Domiciliar Sem Reconciliação",
                        description = "O medicamento de uso contínuo \"${homeMed.name} ${homeMed.dosage}\" ainda está pendente de reconciliação admissional.",
                        severity = AlertSeverity.LOW
                    )
                )
            } else if (homeMed.action == "Omitido" && homeMed.justification.isEmpty()) {
                alerts.add(
                    SafetyAlert(
                        type = AlertType.OMISSION,
                        title = "Omissão de Medicamento sem Justificativa",
                        description = "O medicamento domiciliar \"${homeMed.name}\" foi marcado como omitido, porém nenhuma justificativa médica foi descrita.",
                        severity = AlertSeverity.MEDIUM
                    )
                )
            } else if (homeMed.action == "Prescrito no Hospital") {
                // Cross check if it really exists in the hospital prescriptions
                val existsInHospital = hospital.any { hospPres ->
                    hospPres.activeIngredient.lowercase() == resolvedHome.activeIngredient.lowercase()
                }
                if (!existsInHospital) {
                    alerts.add(
                        SafetyAlert(
                            type = AlertType.OMISSION,
                            title = "Inconsistência na Reconciliação",
                            description = "Você indicou que \"${homeMed.name}\" seria prescrito no hospital, mas ele não consta na prescrição hospitalar ativa.",
                            severity = AlertSeverity.MEDIUM
                        )
                    )
                }
            }
        }

        // 2. ACTIVE INGREDIENT DUPLICATION ALERTS (Evitar Duplicidade de Dose no Hospital)
        val activePrescriptions = hospital.filter { it.isActive }
        val ingredientGroups = activePrescriptions.groupBy { it.activeIngredient.lowercase() }
        ingredientGroups.forEach { (ingredient, items) ->
            if (items.size > 1) {
                val drugNamesStr = items.joinToString(" e ") { "\"${it.name} ${it.dosage}\"" }
                alerts.add(
                    SafetyAlert(
                        type = AlertType.DUPLICATE_INGREDIENT,
                        title = "DUPLICIDADE DE PRINCÍPIO ATIVO: Risco Alto",
                        description = "As prescrições $drugNamesStr possuem o mesmo princípio ativo ($ingredient). Risco iminente de superdosagem hospitalar!",
                        severity = AlertSeverity.HIGH
                    )
                )
            }
        }

        // 3. THERAPEUTIC CLASS OVERLAP ALERTS
        val classGroups = activePrescriptions.groupBy {
            val resolved = resolveDrugBrand(it.name)
            resolved.therapeuticClass
        }
        classGroups.forEach { (tClass, items) ->
            if (items.size > 1 && tClass != "Não Categorizado") {
                // Distinct ingredients of the same class (so they aren't duplicates check level 2)
                val distinctIngredients = items.map { it.activeIngredient.lowercase() }.distinct()
                if (distinctIngredients.size > 1) {
                    val listStr = items.joinToString(" + ") { "${it.name} (${it.activeIngredient})" }
                    alerts.add(
                        SafetyAlert(
                            type = AlertType.CLASS_OVERLAP,
                            title = "SOBREPOSIÇÃO TERAPÊUTICA: Classe Equivalente",
                            description = "Prescrição concomitante de múltiplos medicamentos da classe \"$tClass\": $listStr. Avalie duplicidade de classe.",
                            severity = AlertSeverity.MEDIUM
                        )
                    )
                }
            }
        }

        // 4. ADMINISTRATION TIME/INTERVAL SAFETY ALERTS
        val currentTime = System.currentTimeMillis()
        val recentAdministrations = admin.groupBy { it.activeIngredient.lowercase() }
        recentAdministrations.forEach { (ingredient, list) ->
            val lastAdmin = list.maxByOrNull { it.administeredAt } ?: return@forEach
            val timeElapsedMinutes = (currentTime - lastAdmin.administeredAt) / (1000 * 60)
            
            // If administered less than 240 minutes (4 hours) ago
            if (timeElapsedMinutes < 240) {
                alerts.add(
                    SafetyAlert(
                        type = AlertType.INTERVAL_WARNING,
                        title = "Alerta de Administração Recente",
                        description = "Dose de \"${lastAdmin.prescriptionName} (${lastAdmin.dosage})\" contendo \"$ingredient\" foi administrada há apenas $timeElapsedMinutes min. Risco de repetição precoce!",
                        severity = AlertSeverity.MEDIUM
                    )
                )
            }
        }

        return alerts
    }

    // --- Gemini AI Clinical Assistant integration ---
    fun generateAIClinicalGuidance() {
        val patient = _selectedPatient.value ?: return
        _isGeneratingGuidance.value = true
        _generationError.value = null

        viewModelScope.launch {
            try {
                // Build robust prompt
                val prompt = buildString {
                    append("PRESCRIÇÃO, RECONCILIAÇÃO E DIRETRIZES HOSPITALARES:\n\n")
                    append("- Nome do Paciente: ${patient.name}\n")
                    append("- Setor/Leito: ${patient.bed}\n")
                    if (patient.birthDate.isNotEmpty()) append("- Data de Nascimento: ${patient.birthDate}\n")
                    if (patient.triageClass.isNotEmpty()) append("- Classificação de Manchester: ${patient.triageClass}\n")
                    if (patient.chiefComplaint.isNotEmpty()) append("- Queixa Principal: ${patient.chiefComplaint}\n")
                    if (patient.allergies.isNotEmpty()) append("- Alergias Conhecidas: ${patient.allergies}\n")
                    
                    // Sinais vitais
                    append("- Sinais Vitais:\n")
                    append("  PA: ${patient.pa} mmHg | FC: ${patient.fc} bpm | Temp: ${patient.temp} °C | SpO2: ${patient.spo2}% | Escala de Dor: ${patient.pain} | Glasgow: ${patient.glasgow}\n")
                    
                    // Exame físico
                    append("- Exame Físico:\n")
                    if (patient.examGeral.isNotEmpty()) append("  Geral: ${patient.examGeral}\n")
                    if (patient.examResp.isNotEmpty()) append("  Respiratório: ${patient.examResp}\n")
                    if (patient.examCardio.isNotEmpty()) append("  Cardíaco: ${patient.examCardio}\n")
                    if (patient.examAbd.isNotEmpty()) append("  Abdominal: ${patient.examAbd}\n")
                    if (patient.examNeuro.isNotEmpty()) append("  Neurológico: ${patient.examNeuro}\n")

                    // Labs
                    append("- Exames Laboratoriais Registrados:\n ${patient.labsJson}\n")

                    // Evolution
                    if (patient.evolutionText.isNotEmpty()) append("- Evolução do Plantão:\n${patient.evolutionText}\n")

                    // Reconciliation & safety state
                    append("- MEDICAMENTOS DE USO DOMICILIAR CONTÍNUO (RECONCILIAÇÃO):\n")
                    _homeMedications.value.forEach {
                        append("  * ${it.name} ${it.dosage} - Conduta na Admissão: ${it.action} (Justificativa: ${it.justification})\n")
                    }

                    append("- PRESCRIÇÃO HOSPITALAR EDITADA PELO MÉDICO:\n")
                    _hospitalPrescriptions.value.forEach {
                        append("  * ${it.name} ${it.dosage} (${it.frequency}) via ${it.route}\n")
                    }

                    // Safety warnings calculated
                    append("- ALERTAS DE SEGURANÇA DETECTADOS PROGRAMATICAMENTE:\n")
                    _safetyAlerts.value.forEach {
                        append("  * [${it.type.name}] ${it.title}: ${it.description}\n")
                    }
                }

                val systemPrompt = """Você é um Assistente Médico AI Sênior Especialista em Farmacovigilância, Reconciliação Admissional e Segurança de Prescrição Hospitalar.
Sua tarefa é analisar os dados fornecidos deste paciente, sua reconciliação medicamentosa de admissão e os alertas de segurança contra duplicidade, fornecendo um parecer clínico e diretrizes para o prontuário.

Estruture seu parecer estritamente no seguinte modelo formatado:
[PARECER DE RECONCILIAÇÃO E SEGURANÇA MEDICAMENTOSA]

1. RESUMO CLÍNICO DO PACIENTE:
- Breve análise integrada da queixa principal, sinais vitais e laboratórios.

2. AVALIAÇÃO DA RECONCILIAÇÃO DE ADMISSÃO:
- Avaliar se as condutas domiciliares (manutenções, suspensões de metformina/antiagregantes antes de cirurgias ou exames, etc.) foram adequadas.
- Apontar se há risco de omissão de alguma terapia contínua essencial para o paciente.

3. CONTEXTO DE EVITAÇÃO DE DUPLICIDADE (SEGUNDO CHEQUE):
- Destacar se há prescrições ativas de medicamentos idênticos (ex: mesmo princípio ativo em nomes comerciais diferentes, como Aradois/Losartana) ou da mesma classe, alertando sobre os riscos potenciais no leito.
- Fornecer instruções específicas para a equipe de enfermagem sobre intervalos seguros de administração.

4. DIRETRIZES DE RECEITUÁRIO, CONDUTA E CRITÉRIOS DE ALTA SEGURA:
- Dieta sugerida e hidratação.
- Ajustes medicamentosos prioritários.
- Critérios clínicos para desfecho (alta ou necessidade de internação em UTI/enfermaria).

Aviso Legaj: "Este parecer é um suporte clínico de IA e deve ser revisado, assinado e carimbado pelo médico assistente."

Responda em Português do Brasil com formatação markdown limpa (use listas ordenadas ou marcadores simples)."""

                val request = GeminiRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                }

                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "Nenhum resultado gerado pelo assistente."

                updatePatientField("clinicalGuidanceText", resultText)
            } catch (e: Exception) {
                _generationError.value = "Erro ao conectar com a IA: ${e.localizedMessage ?: "Verifique a chave de API"}"
            } finally {
                _isGeneratingGuidance.value = false
            }
        }
    }

    // --- Patient Evolution History Manager ---
    fun savePatientEvolution(text: String) {
        val p = _selectedPatient.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val evolution = PatientEvolution(
                patientId = p.id,
                text = text.trim(),
                timestamp = System.currentTimeMillis()
            )
            repository.insertPatientEvolution(evolution)
        }
    }

    fun deletePatientEvolution(evolution: PatientEvolution) {
        viewModelScope.launch {
            repository.deletePatientEvolution(evolution)
        }
    }

    fun clearPatientEvolutions() {
        val p = _selectedPatient.value ?: return
        viewModelScope.launch {
            repository.clearPatientEvolutions(p.id)
        }
    }
}
