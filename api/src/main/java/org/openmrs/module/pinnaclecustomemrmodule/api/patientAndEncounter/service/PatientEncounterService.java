package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service;

import java.util.List;
import org.openmrs.Encounter;

public interface PatientEncounterService {

    Encounter createEncounter(Integer patientId, String encounterTypeName,
            Integer locationId, Integer providerId);

    Encounter changeStatus(Integer encounterId, String newStatus, Integer changedBy);

    String getCurrentStatus(Integer encounterId);

    List<Encounter> findByStatus(String status);
}
