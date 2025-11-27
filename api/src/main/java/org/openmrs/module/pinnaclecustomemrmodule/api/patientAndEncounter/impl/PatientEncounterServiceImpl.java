package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.impl;

import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.PatientEncounterService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PatientEncounterServiceImpl extends BaseOpenmrsService implements PatientEncounterService {

    @Override
    public Encounter createEncounter(Integer patientId, String encounterTypeName,
            Integer locationId, Integer providerId) {

        Patient patient = Context.getPatientService().getPatient(patientId);
        EncounterType encounterType = Context.getEncounterService().getEncounterType(encounterTypeName);
        Location location = Context.getLocationService().getLocation(locationId);

        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setEncounterType(encounterType);
        encounter.setEncounterDatetime(new Date());
        encounter.setLocation(location);

        // Add provider if applicable
        if (providerId != null) {
            Provider provider = Context.getProviderService().getProvider(providerId);
            EncounterRole defaultRole = Context.getEncounterService().getEncounterRoleByUuid(
                    EncounterRole.UNKNOWN_ENCOUNTER_ROLE_UUID);
            encounter.addProvider(defaultRole, provider);
        }

        // Save encounter
        Context.getEncounterService().saveEncounter(encounter);

        // Initial status = REGISTERED
        changeStatus(encounter.getEncounterId(), "REGISTERED",
                Context.getAuthenticatedUser().getId());

        return encounter;
    }

    @Override
    public Encounter changeStatus(Integer encounterId, String newStatus, Integer changedBy) {

        Encounter e = Context.getEncounterService().getEncounter(encounterId);
        if (e == null)
            throw new IllegalArgumentException("Encounter not found: " + encounterId);

        String current = getCurrentStatus(encounterId);

        if (!isValidTransition(current, newStatus)) {
            throw new IllegalStateException("Invalid status transition from " + current + " to " + newStatus);
        }

        String sql = "INSERT INTO emr_encounter_status (encounter_id, status, changed_by, changed_on) " +
                "VALUES (" + encounterId + ", '" + newStatus.replace("'", "''") + "', " +
                changedBy + ", NOW())";

        Context.getAdministrationService().executeSQL(sql, false);

        return e;
    }

    @Override
    public String getCurrentStatus(Integer encounterId) {

        String sql = "SELECT status FROM emr_encounter_status WHERE encounter_id = " + encounterId +
                " ORDER BY changed_on DESC LIMIT 1";

        List<List<Object>> rows = Context.getAdministrationService().executeSQL(sql, true);

        if (rows != null && !rows.isEmpty()) {
            List<Object> row = rows.get(0);
            return (String) row.get(0);

        }
        return null;
    }

    @Override
    public List<Encounter> findByStatus(String status) {

        String sql = "SELECT encounter_id FROM emr_encounter_status s1 WHERE status = '" +
                status.replace("'", "''") + "' AND changed_on = (" +
                "SELECT MAX(changed_on) FROM emr_encounter_status s2 WHERE s2.encounter_id = s1.encounter_id)";

        List<List<Object>> rows = Context.getAdministrationService().executeSQL(sql, true);

        List<Encounter> results = new ArrayList<>();

        if (rows != null) {
            for (Object r : rows) {
                Object[] row = (Object[]) r;
                Integer encId = (Integer) row[0];
                Encounter encounter = Context.getEncounterService().getEncounter(encId);
                if (encounter != null)
                    results.add(encounter);
            }
        }

        return results;
    }

    private boolean isValidTransition(String current, String next) {

        if (current == null && next.equals("REGISTERED"))
            return true;

        if (current == null)
            return false;

        switch (current) {
            case "REGISTERED":
                return next.equals("IN_PROGRESS");

            case "IN_PROGRESS":
                return next.equals("DISCHARGED") || next.equals("CLOSED");

            case "DISCHARGED":
                return next.equals("CLOSED");

            default:
                return false;
        }
    }
}
