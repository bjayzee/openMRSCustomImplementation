package org.openmrs.module.pinnaclecustomemrmodule.api.clinical.service;

import org.openmrs.Obs;

import java.util.Map;

public interface ClinicalService {
    Obs saveSoapNote(Integer encounterId, String subjective, String objective, String assessment, String plan);

    Obs saveVitals(Integer encounterId, Map<String, Object> vitals);

    Obs recordVital(Patient patient, String conceptName, Double value);

    List<Obs> getVitals(Patient patient);

    Obs addDiagnosis(Patient patient, Integer icdConceptId);

    List<Obs> getDiagnoses(Patient patient);

    void removeDiagnosis(Integer obsId, String reason);

    Obs updateDiagnosis(Integer obsId, Integer icdConceptId);
}
