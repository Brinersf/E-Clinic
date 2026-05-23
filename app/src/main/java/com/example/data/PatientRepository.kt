package com.example.data

import kotlinx.coroutines.flow.Flow

class PatientRepository(private val patientDao: PatientDao) {
    val allPatients: Flow<List<Patient>> = patientDao.getAllPatients()

    suspend fun getPatientById(id: Long): Patient? = patientDao.getPatientById(id)

    suspend fun insertPatient(patient: Patient): Long = patientDao.insertPatient(patient)

    suspend fun updatePatient(patient: Patient) = patientDao.updatePatient(patient)

    suspend fun deletePatient(patient: Patient) = patientDao.deletePatient(patient)

    // Home Medications
    fun getHomeMedications(patientId: Long): Flow<List<HomeMedication>> =
        patientDao.getHomeMedications(patientId)

    suspend fun insertHomeMedication(med: HomeMedication) =
        patientDao.insertHomeMedication(med)

    suspend fun deleteHomeMedication(med: HomeMedication) =
        patientDao.deleteHomeMedication(med)

    suspend fun clearHomeMedications(patientId: Long) =
        patientDao.clearHomeMedications(patientId)

    // Hospital Prescriptions
    fun getHospitalPrescriptions(patientId: Long): Flow<List<HospitalPrescription>> =
        patientDao.getHospitalPrescriptions(patientId)

    suspend fun insertHospitalPrescription(prescription: HospitalPrescription) =
        patientDao.insertHospitalPrescription(prescription)

    suspend fun deleteHospitalPrescription(prescription: HospitalPrescription) =
        patientDao.deleteHospitalPrescription(prescription)

    suspend fun clearHospitalPrescriptions(patientId: Long) =
        patientDao.clearHospitalPrescriptions(patientId)

    // Dose Administrations
    fun getDoseAdministrations(patientId: Long): Flow<List<DoseAdministration>> =
        patientDao.getDoseAdministrations(patientId)

    suspend fun insertDoseAdministration(admin: DoseAdministration) =
        patientDao.insertDoseAdministration(admin)

    suspend fun deleteDoseAdministration(admin: DoseAdministration) =
        patientDao.deleteDoseAdministration(admin)

    suspend fun clearDoseAdministrations(patientId: Long) =
        patientDao.clearDoseAdministrations(patientId)

    // Patient Evolutions (Histórico)
    fun getPatientEvolutions(patientId: Long): Flow<List<PatientEvolution>> =
        patientDao.getPatientEvolutions(patientId)

    suspend fun insertPatientEvolution(evolution: PatientEvolution): Long =
        patientDao.insertPatientEvolution(evolution)

    suspend fun deletePatientEvolution(evolution: PatientEvolution) =
        patientDao.deletePatientEvolution(evolution)

    suspend fun clearPatientEvolutions(patientId: Long) =
        patientDao.clearPatientEvolutions(patientId)
}
