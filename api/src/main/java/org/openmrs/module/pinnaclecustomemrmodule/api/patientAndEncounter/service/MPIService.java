package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service;

import java.util.List;

import org.openmrs.Patient;
import org.openmrs.activelist.Allergy;

public interface MPIService {
	
	Patient createPatient(Patient p);
	
	Patient getPatientById(String uuid);
	
	List<Patient> searchPatients(String q);
	
	Patient mergePatients(Integer sourceId, Integer targetId, String reason);
	
	Patient splitPatient(Integer mergedPatientId, Integer[] encounterIds, String reason);
	
	void recordAllergy(Patient patient, String allergen, String reaction);
	
	List<Allergy> getAllergies(Patient patient);
	
	void voidAllergy(Integer allergyId, String reason);
}
