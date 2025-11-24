package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.module.emr.EmrCoreConstants;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.PatientEncounterService;

public class PatientEncounterServiceImpl extends BaseOpenmrsService implements PatientEncounterService {

    private AdministrationService administrationService;
    private EncounterService encounterService;

    public PatientEncounterServiceImpl(AdministrationService administrationService, EncounterService encounterService) {
        this.administrationService = administrationService;
        this.encounterService = encounterService;
    }

    @Override
    public Encounter createEncounter(Integer patientId, String encounterTypeName, Integer locationId,
            Integer providerId) {
        Patient patient = Context.getPatientService().getPatient(patientId);
        EncounterType encounterType = encounterService.getEncounterType(encounterTypeName);

        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setEncounterType(encounterType);
        encounter.setEncounterDatetime(new Date());
        encounter.setLocation(Context.getLocationService().getLocation(locationId));

        if (providerId != null) {
            Provider provider = Context.getProviderService().getProvider(providerId);
            EncounterRole role = Context.getEncounterService().getEncounterRoleByName("Attending Provider");
            encounter.addProvider(role, provider);
        }

        encounterService.saveEncounter(encounter);

        // Record initial status
        changeStatus(encounter.getEncounterId(),
                EmrCoreConstants.EncounterStatus.REGISTERED.name(),
                Context.getAuthenticatedUser().getId());

        return encounter;
    }

    @Override
    public Encounter changeStatus(Integer encounterId, String newStatus, Integer changedBy) {
        Encounter encounter = encounterService.getEncounter(encounterId);
        if (encounter == null) {
            throw new IllegalArgumentException("Encounter not found: " + encounterId);
        }

        String currentStatus = getCurrentStatus(encounterId);
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException("Cannot transition from " + currentStatus + " to " + newStatus);
        }

        String sql = "INSERT INTO emr_encounter_status (encounter_id, status, changed_by, changed_on) VALUES (?, ?, ?, NOW())";
        administrationService.executeSQL(sql, new Object[] { encounterId, newStatus, changedBy }, false);

        return encounter;
    }

    @Override
    public String getCurrentStatus(Integer encounterId) {
        String sql = "SELECT status FROM emr_encounter_status WHERE encounter_id = ? ORDER BY changed_on DESC LIMIT 1";
        List<Object> rows = administrationService.executeSQL(sql, new Object[] { encounterId }, true);

        if (rows != null && !rows.isEmpty()) {
            Object[] row = (Object[]) rows.get(0);
            return (String) row[0];
        }

        return null;
    }

    @Override
    public List<Encounter> findByStatus(String status) {
        String sql = "SELECT encounter_id FROM emr_encounter_status s1 " +
                "WHERE status = ? " +
                "AND changed_on = (SELECT MAX(changed_on) FROM emr_encounter_status s2 WHERE s2.encounter_id = s1.encounter_id)";

        List<Object> rows = administrationService.executeSQL(sql, new Object[] { status }, true);
        List<Encounter> results = new ArrayList<>();

        if (rows != null) {
            for (Object rowObj : rows) {
                Object[] row = (Object[]) rowObj;
                Integer encounterId = (Integer) row[0];
                results.add(encounterService.getEncounter(encounterId));
            }
        }

        return results;
    }

    private boolean isValidTransition(String current, String next) {
        if (current == null && "REGISTERED".equals(next))
            return true;
        if (current == null)
            return false;

        switch (current) {
            case "REGISTERED":
                return "IN_PROGRESS".equals(next);
            case "IN_PROGRESS":
                return "DISCHARGED".equals(next) || "CLOSED".equals(next);
            case "DISCHARGED":
                return "CLOSED".equals(next);
            default:
                return false;
        }
    }
}
