package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service;

import org.openmrs.Patient;
import java.util.List;

public interface MPIService {

    Patient createPatient(Patient patient);

    Patient getPatientById(String uuid);

    List<Patient> searchPatients(String query);

    Patient mergePatients(Integer sourcePatientId, Integer targetPatientId, String reason);

    Patient splitPatient(Integer mergedPatientId, Integer[] encounterIdsToMove, String reason);

    // Temporarily commented out — Allergy API module not available
    // Allergy recordAllergy(Patient patient, String allergenName, String reaction, String severity);
    // List<Allergy> getAllergies(Patient patient);
    // void voidAllergy(Integer allergyId, String reason);
}