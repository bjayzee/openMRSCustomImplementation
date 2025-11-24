package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonName;
import org.openmrs.activelist.Allergy;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.idgen.AutoGenerationOption;
import org.openmrs.module.idgen.IdentifierSource;
import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.MPIService;
import org.springframework.beans.factory.annotation.Autowired;

public class MPIServiceImpl extends BaseOpenmrsService implements MPIService {
    @Autowired
    private PatientService patientService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private LocationService locationService;
    @Autowired
    private AdministrationService administrationService;

    @Override
    public Patient createPatient(Patient p) {
        // Ensure patient has an identifier
        if (p.getPatientIdentifier() == null) {
            PatientIdentifierType idType = patientService
                    .getPatientIdentifierTypeByName(EmrCoreConstants.DEFAULT_IDGEN_TYPE);
            if (idType == null) {
                throw new IllegalStateException(
                        "Identifier type '" + Core.DEFAULT_IDGEN_TYPE + "' not found");
            }
            PatientIdentifier id = new PatientIdentifier(generateUniqueId(), idType,
                    locationService.getDefaultLocation());
            p.addIdentifier(id);
        }
        return patientService.savePatient(p);
    }

    @Override
    public Patient getPatientById(String uuid) {
        return patientService.getPatientByUuid(uuid);
    }

    @Override
    public List<Patient> searchPatients(String q) {
        if (q == null || q.isEmpty())
            return Collections.emptyList();
        String lower = q.toLowerCase();
        return patientService.getAllPatients().stream()
                .filter(p -> {
                    PersonName name = p.getPersonName();
                    boolean matchName = name != null && ((name.getGivenName() != null
                            && name.getGivenName().toLowerCase().contains(lower))
                            || (name.getFamilyName() != null && name.getFamilyName().toLowerCase().contains(lower)));
                    boolean matchId = p.getIdentifiers().stream()
                            .anyMatch(pi -> pi.getIdentifier().toLowerCase().contains(lower));
                    return matchName || matchId;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Patient mergePatients(Integer sourceId, Integer targetId, String reason) {
        Patient source = patientService.getPatient(sourceId);
        Patient target = patientService.getPatient(targetId);
        if (source == null || target == null)
            throw new IllegalArgumentException("Invalid patient id(s)");
        // Merge identifiers (avoid duplicates)
        for (PatientIdentifier pid : new ArrayList<>(source.getIdentifiers())) {
            boolean exists = target.getIdentifiers().stream()
                    .anyMatch(tid -> tid.getIdentifier().equals(pid.getIdentifier()));
            if (!exists) {
                target.addIdentifier(pid);
            }
        }
        // Move all encounters
        List<Encounter> encs = encounterService.getEncountersByPatient(source);
        for (Encounter e : encs) {
            e.setPatient(target);
            encounterService.saveEncounter(e);
        }
        // Save target then void source for traceability
        patientService.savePatient(target);
        patientService.voidPatient(source,
                "Merged into " + target.getUuid() + ". Reason: " + (reason == null ? "none" : reason));
        return target;
    }

    @Override
    public Patient splitPatient(Integer mergedPatientId, Integer[] encounterIds, String reason) {
        Patient merged = patientService.getPatient(mergedPatientId);
        if (merged == null)
            throw new IllegalArgumentException("Patient not found");
        Patient newPatient = new Patient();
        newPatient.setNames(merged.getNames());
        newPatient.setGender(merged.getGender());
        newPatient.setBirthdate(merged.getBirthdate());
        newPatient.addIdentifier(new PatientIdentifier(generateUniqueId(),
                patientService.getPatientIdentifierTypeByName(EmrCoreConstants.DEFAULT_IDGEN_TYPE),
                locationService.getDefaultLocation()));
        newPatient = patientService.savePatient(newPatient);
        if (encounterIds != null) {
            for (Integer encId : encounterIds) {
                Encounter e = encounterService.getEncounter(encId);
                if (e != null && e.getPatient().equals(merged)) {
                    e.setPatient(newPatient);
                    encounterService.saveEncounter(e);
                }
            }
        }
        // Optionally write an audit log: using AdministrationService SQL for simplicity
        administrationService.executeSQL("INSERT INTO emr_patient_alias (patient_id, alias) VALUES (?,?)",
                new Object[] { newPatient.getPatientId(), "split_from_" + merged.getPatientId() });
        return newPatient;
    }

    private String generateUniqueId() {
        try {
            IdentifierSource source = Context.getService(IdgenService.class)
                    .getIdentifierSources().stream()
                    .filter(s -> s instanceof AutoGenerationOption)
                    .findFirst()
                    .orElse(null);
            if (source != null) {
                return Context.getService(IdgenService.class).generateIdentifier(source, "Registration");
            }
        } catch (Exception e) {
            // fall back to UUID
        }
        return "EMR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public void recordAllergy(Patient patient, String allergen, String reaction) {
        Obs allergyObs = new Obs();
        allergyObs.setPerson(patient);
        allergyObs.setConcept(conceptService.getConceptByName("ALLERGY")); // Predefine concept
        allergyObs.setValueCoded(conceptService.getConceptByName(allergen));
        obsService.saveObs(allergyObs);
    }

    @Override
public List<Allergy> getAllergies(Patient patient) {
return Context.getAllergyService().getAllergies(patient);
}


@Override
public void voidAllergy(Integer allergyId, String reason) {
Allergy a = Context.getAllergyService().getAllergy(allergyId);
if (a != null) {
Context.getAllergyService().voidAllergy(a, reason);
}
}
}
