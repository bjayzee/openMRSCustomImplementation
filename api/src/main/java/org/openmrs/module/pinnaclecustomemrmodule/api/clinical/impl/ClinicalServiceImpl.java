package org.openmrs.module.pinnaclecustomemrmodule.api.clinical.impl;

import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pinnaclecustomemrmodule.api.clinical.service.ClinicalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("pinnaclecustomemrmodule.ClinicalService")
@Transactional
public class ClinicalServiceImpl extends BaseOpenmrsService implements ClinicalService {

    @Autowired private ObsService obsService;
    @Autowired private ConceptService conceptService;
    @Autowired private EncounterService encounterService;

    // Predefined concept names (will be created in Liquibase)
    private static final String SOAP_GROUP = "SOAP Note";
    private static final String VITALS_GROUP = "Vital Signs";
    private static final String DIAGNOSIS_CONCEPT = "Diagnosis";
    private static final Map<String, String> VITAL_CONCEPTS = new HashMap<>() {{
        put("temperature", "Temperature");
        put("pulse", "Pulse");
        put("bp_systolic", "Systolic blood pressure");
        put("bp_diastolic", "Diastolic blood pressure");
        put("respiratory_rate", "Respiratory rate");
        put("spo2", "Oxygen saturation");
        put("height", "Height (cm)");
        put("weight", "Weight (kg)");
    }};

    @Override
    public Obs saveSoapNote(Integer encounterId, String subjective, String objective, String assessment, String plan) {
        Encounter encounter = getEncounter(encounterId);
        Concept soapGroupConcept = getConcept(SOAP_GROUP);

        Obs soapObs = new Obs();
        soapObs.setObsDatetime(new Date());
        soapObs.setPerson(encounter.getPatient());
        soapObs.setEncounter(encounter);
        soapObs.setConcept(soapGroupConcept);

        soapObs.addGroupMember(createTextObs(encounter, "Subjective", subjective));
        soapObs.addGroupMember(createTextObs(encounter, "Objective", objective));
        soapObs.addGroupMember(createTextObs(encounter, "Assessment", assessment));
        soapObs.addGroupMember(createTextObs(encounter, "Plan", plan));

        return obsService.saveObs(soapObs, "SOAP note saved via Pinnacle EMR");
    }

    @Override
    public Obs saveVitals(Integer encounterId, Map<String, Object> vitals) {
        Encounter encounter = getEncounter(encounterId);
        Concept vitalsGroupConcept = getConcept(VITALS_GROUP);

        Obs vitalsGroup = new Obs();
        vitalsGroup.setObsDatetime(new Date());
        vitalsGroup.setPerson(encounter.getPatient());
        vitalsGroup.setEncounter(encounter);
        vitalsGroup.setConcept(vitalsGroupConcept);

        for (Map.Entry<String, Object> entry : vitals.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            if (value == null) continue;

            String conceptName = VITAL_CONCEPTS.get(key);
            if (conceptName == null) continue;

            Concept concept = getConcept(conceptName);
            Obs obs = new Obs();
            obs.setPerson(encounter.getPatient());
            obs.setEncounter(encounter);
            obs.setConcept(concept);
            obs.setObsDatetime(new Date());

            if (value instanceof Number) {
                obs.setValueNumeric(((Number) value).doubleValue());
            } else {
                try {
                    obs.setValueNumeric(Double.parseDouble(value.toString()));
                } catch (NumberFormatException ignored) {}
            }

            vitalsGroup.addGroupMember(obs);
        }

        return obsService.saveObs(vitalsGroup, "Vital signs recorded via Pinnacle EMR");
    }

    @Override
    public Obs recordVital(Patient patient, String conceptName, Double value) {
        if (patient == null || conceptName == null || value == null) {
            throw new IllegalArgumentException("Patient, conceptName, and value are required");
        }

        Concept concept = conceptService.getConceptByName(conceptName);
        if (concept == null) {
            throw new IllegalArgumentException("Concept not found: " + conceptName);
        }

        Obs obs = new Obs();
        obs.setPerson(patient);
        obs.setObsDatetime(new Date());
        obs.setLocation(Context.getLocationService().getDefaultLocation());
        obs.setConcept(concept);
        obs.setValueNumeric(value);

        return obsService.saveObs(obs, "Vital recorded via Pinnacle EMR");
    }

    @Override
    public List<Obs> getVitals(Patient patient) {
        if (patient == null) return Collections.emptyList();

        List<Concept> vitalConcepts = new ArrayList<>();
        for (String name : VITAL_CONCEPTS.values()) {
            Concept c = conceptService.getConceptByName(name);
            if (c != null) vitalConcepts.add(c);
        }

        return obsService.getObservationsByPersonAndConcepts(patient, vitalConcepts);
    }

    @Override
    public Obs addDiagnosis(Patient patient, Integer icdConceptId) {
        if (patient == null || icdConceptId == null) {
            throw new IllegalArgumentException("Patient and ICD concept ID required");
        }

        Concept diagnosisConcept = getConcept(DIAGNOSIS_CONCEPT);
        Concept icdConcept = conceptService.getConcept(icdConceptId);
        if (icdConcept == null) {
            throw new IllegalArgumentException("ICD-10 concept not found: " + icdConceptId);
        }

        Obs diagnosisObs = new Obs();
        diagnosisObs.setPerson(patient);
        diagnosisObs.setObsDatetime(new Date());
        diagnosisObs.setConcept(diagnosisConcept);
        diagnosisObs.setValueCoded(icdConcept);
        diagnosisObs.setComment("ICD-10 Diagnosis");

        return obsService.saveObs(diagnosisObs, "Diagnosis added via Pinnacle EMR");
    }

    @Override
    public List<Obs> getDiagnoses(Patient patient) {
        if (patient == null) return Collections.emptyList();
        Concept diagnosisConcept = getConcept(DIAGNOSIS_CONCEPT);
        return obsService.getObservationsByPersonAndConcept(patient, diagnosisConcept);
    }

    @Override
    public void removeDiagnosis(Integer obsId, String reason) {
        Obs obs = obsService.getObs(obsId);
        if (obs == null) throw new IllegalArgumentException("Diagnosis Obs not found");

        obsService.voidObs(obs, reason != null ? reason : "Removed via Pinnacle EMR");
    }

    @Override
    public Obs updateDiagnosis(Integer obsId, Integer newIcdConceptId) {
        Obs obs = obsService.getObs(obsId);
        if (obs == null) throw new IllegalArgumentException("Diagnosis Obs not found");

        Concept newConcept = conceptService.getConcept(newIcdConceptId);
        if (newConcept == null) throw new IllegalArgumentException("New ICD concept not found");

        obs.setValueCoded(newConcept);
        obs.setDateChanged(new Date());
        obs.setChangedBy(Context.getAuthenticatedUser());

        return obsService.saveObs(obs, "Diagnosis updated via Pinnacle EMR");
    }

    // Helper methods
    private Encounter getEncounter(Integer id) {
        Encounter e = encounterService.getEncounter(id);
        if (e == null) throw new IllegalArgumentException("Encounter not found: " + id);
        return e;
    }

    private Concept getConcept(String name) {
        Concept c = conceptService.getConceptByName(name);
        if (c == null) {
            throw new IllegalStateException("Required concept not found in dictionary: " + name +
                    ". Run Liquibase update or import concept dictionary.");
        }
        return c;
    }

    private Obs createTextObs(Encounter encounter, String conceptName, String value) {
        Obs obs = new Obs();
        obs.setPerson(encounter.getPatient());
        obs.setEncounter(encounter);
        obs.setConcept(getConcept(conceptName));
        obs.setValueText(value);
        obs.setObsDatetime(new Date());
        return obs;
    }
}