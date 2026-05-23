package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bed: String, // Leito
    val birthDate: String = "",
    val allergies: String = "",
    val history: String = "",
    val triageClass: String = "",
    val chiefComplaint: String = "",
    val pa: String = "",
    val fc: String = "",
    val temp: String = "",
    val spo2: String = "",
    val pain: String = "",
    val glasgow: String = "",
    val examGeral: String = "",
    val examResp: String = "",
    val examCardio: String = "",
    val examAbd: String = "",
    val examNeuro: String = "",
    val evolutionText: String = "",
    val clinicalGuidanceText: String = "",
    val labsJson: String = "[]", // JSON string of lab results
    val customExamsJson: String = "[]", // JSON string of custom added exams
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "home_medications",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class HomeMedication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val name: String,
    val dosage: String,
    val frequency: String,
    val action: String = "Pendente", // "Prescrito no Hospital", "Pausado/Suspenso", "Substituído", "Omitido"
    val justification: String = ""
)

@Entity(
    tableName = "hospital_prescriptions",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class HospitalPrescription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val name: String, // Commercial/prescribed name e.g. Tylenol, Aradois
    val activeIngredient: String, // Active material e.g. Paracetamol, Losartana
    val dosage: String,
    val frequency: String,
    val route: String, // VO, IV, etc.
    val isActive: Boolean = true
)

@Entity(
    tableName = "dose_administrations",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class DoseAdministration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val prescriptionName: String,
    val activeIngredient: String,
    val dosage: String,
    val administeredAt: Long = System.currentTimeMillis(),
    val nurseSignature: String = "Enf. Plantonista"
)

@Entity(
    tableName = "patient_evolutions",
    foreignKeys = [
        ForeignKey(
            entity = Patient::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("patientId")]
)
data class PatientEvolution(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patientId: Long,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY timestamp DESC")
    fun getAllPatients(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: Long): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient): Long

    @Update
    suspend fun updatePatient(patient: Patient)

    @Delete
    suspend fun deletePatient(patient: Patient)

    // Home Medications
    @Query("SELECT * FROM home_medications WHERE patientId = :patientId")
    fun getHomeMedications(patientId: Long): Flow<List<HomeMedication>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeMedication(med: HomeMedication)

    @Delete
    suspend fun deleteHomeMedication(med: HomeMedication)

    @Query("DELETE FROM home_medications WHERE patientId = :patientId")
    suspend fun clearHomeMedications(patientId: Long)

    // Hospital Prescriptions
    @Query("SELECT * FROM hospital_prescriptions WHERE patientId = :patientId")
    fun getHospitalPrescriptions(patientId: Long): Flow<List<HospitalPrescription>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHospitalPrescription(prescription: HospitalPrescription)

    @Delete
    suspend fun deleteHospitalPrescription(prescription: HospitalPrescription)

    @Query("DELETE FROM hospital_prescriptions WHERE patientId = :patientId")
    suspend fun clearHospitalPrescriptions(patientId: Long)

    // Dose Administrations
    @Query("SELECT * FROM dose_administrations WHERE patientId = :patientId ORDER BY administeredAt DESC")
    fun getDoseAdministrations(patientId: Long): Flow<List<DoseAdministration>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseAdministration(admin: DoseAdministration)

    @Delete
    suspend fun deleteDoseAdministration(admin: DoseAdministration)

    @Query("DELETE FROM dose_administrations WHERE patientId = :patientId")
    suspend fun clearDoseAdministrations(patientId: Long)

    // Patient Evolutions (Histórico)
    @Query("SELECT * FROM patient_evolutions WHERE patientId = :patientId ORDER BY timestamp DESC")
    fun getPatientEvolutions(patientId: Long): Flow<List<PatientEvolution>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatientEvolution(evolution: PatientEvolution): Long

    @Delete
    suspend fun deletePatientEvolution(evolution: PatientEvolution)

    @Query("DELETE FROM patient_evolutions WHERE patientId = :patientId")
    suspend fun clearPatientEvolutions(patientId: Long)
}

@Database(
    entities = [
        Patient::class,
        HomeMedication::class,
        HospitalPrescription::class,
        DoseAdministration::class,
        PatientEvolution::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PatientDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao

    companion object {
        @Volatile
        private var INSTANCE: PatientDatabase? = null

        fun getDatabase(context: Context): PatientDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PatientDatabase::class.java,
                    "patient_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
