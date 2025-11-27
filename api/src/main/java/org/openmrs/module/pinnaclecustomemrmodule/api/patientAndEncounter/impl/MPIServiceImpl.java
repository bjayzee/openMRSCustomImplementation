package org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.impl;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonName;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.idgen.AutoGenerationOption;
import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.MPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("pinnaclecustomemrmodule.MPIService")
@Transactional
public class MPIServiceImpl extends BaseOpenmrsService implements MPIService {

   @Autowired
   private PatientService patientService;
   @Autowired
   private EncounterService encounterService;
   @Autowired
   private LocationService locationService;
   @Autowired
   private AdministrationService administrationService;
   @Autowired
   private IdentifierSourceService identifierSourceService;

   private static final String ID_TYPE_NAME = "OpenMRS ID"; // Change if your site uses different name

   @Override
   public Patient createPatient(Patient patient) {
      if (patient == null)
         throw new IllegalArgumentException("Patient cannot be null");

      if (patient.getIdentifiers().isEmpty()) {
         PatientIdentifierType type = patientService.getPatientIdentifierTypeByName(ID_TYPE_NAME);
         if (type == null) {
            type = patientService.getAllPatientIdentifierTypes(false).get(0);
         }

         PatientIdentifier id = new PatientIdentifier();
         id.setIdentifier(generateUniqueId());
         id.setIdentifierType(type);
         id.setLocation(locationService.getDefaultLocation());
         id.setPreferred(true);

         patient.addIdentifier(id);
      }

      return patientService.savePatient(patient);
   }

   @Override
   public Patient getPatientById(String uuid) {
      return StringUtils.isNotBlank(uuid) ? patientService.getPatientByUuid(uuid) : null;
   }

   @Override
   public List<Patient> searchPatients(String query) {
      if (StringUtils.isBlank(query))
         return Collections.emptyList();

      String q = query.toLowerCase().trim();
      return patientService.getAllPatients(true).stream()
            .filter(p -> {
               PersonName n = p.getPersonName();
               boolean nameMatch = n != null && (StringUtils.containsIgnoreCase(n.getGivenName(), q) ||
                     StringUtils.containsIgnoreCase(n.getFamilyName(), q) ||
                     StringUtils.containsIgnoreCase(n.getFullName(), q));
               boolean idMatch = p.getIdentifiers().stream()
                     .anyMatch(i -> StringUtils.containsIgnoreCase(i.getIdentifier(), q));
               return nameMatch || idMatch;
            })
            .limit(100)
            .collect(Collectors.toList());
   }

   @Override
   public Patient mergePatients(Integer sourceId, Integer targetId, String reason) {
      Patient source = patientService.getPatient(sourceId);
      Patient target = patientService.getPatient(targetId);
      if (source == null || target == null || source.equals(target)) {
         throw new IllegalArgumentException("Invalid patients");
      }

      // Transfer identifiers
      for (PatientIdentifier id : new ArrayList<>(source.getIdentifiers())) {
         boolean exists = target.getIdentifiers().stream()
               .anyMatch(t -> t.getIdentifierType().equals(id.getIdentifierType()) &&
                     t.getIdentifier().equals(id.getIdentifier()));
         if (!exists) {
            id.setPatient(target);
            target.addIdentifier(id);
         }
      }

      // Transfer encounters
      encounterService.getEncountersByPatient(source).forEach(e -> {
         e.setPatient(target);
         encounterService.saveEncounter(e);
      });

      patientService.savePatient(target);
      patientService.voidPatient(source, "Merged into " + target.getUuid() +
            (StringUtils.isNotBlank(reason) ? ". Reason: " + reason : ""));

      return target;
   }

   @Override
   public Patient splitPatient(Integer mergedPatientId, Integer[] encounterIdsToMove, String reason) {
      Patient original = patientService.getPatient(mergedPatientId);
      if (original == null)
         throw new IllegalArgumentException("Patient not found");

      Patient newPatient = new Patient();
      newPatient.setGender(original.getGender());
      newPatient.setBirthdate(original.getBirthdate());
      newPatient.setBirthdateEstimated(original.getBirthdateEstimated());
      newPatient.setNames(original.getNames());
      newPatient.setAddresses(original.getAddresses());

      PatientIdentifierType type = patientService.getPatientIdentifierTypeByName(ID_TYPE_NAME);
      if (type == null)
         type = patientService.getAllPatientIdentifierTypes(false).get(0);

      PatientIdentifier newId = new PatientIdentifier(generateUniqueId(), type, locationService.getDefaultLocation());
      newId.setPreferred(true);
      newPatient.addIdentifier(newId);

      newPatient = patientService.savePatient(newPatient);

      if (encounterIdsToMove != null) {
         for (Integer id : encounterIdsToMove) {
            Encounter e = encounterService.getEncounter(id);
            if (e != null && e.getPatient().equals(original)) {
               e.setPatient(newPatient);
               encounterService.saveEncounter(e);
            }
         }
      }

      return newPatient;
   }

   private String generateUniqueId() {
      try {
         PatientIdentifierType idType = patientService.getPatientIdentifierTypeByName(ID_TYPE_NAME);
         if (idType == null) {
            idType = patientService.getAllPatientIdentifierTypes(false).stream()
                  .filter(t -> !t.isRetired())
                  .findFirst()
                  .orElse(null);
         }

         if (idType != null) {
            String id = identifierSourceService.generateIdentifier(idType, "Pinnacle Registration");
            if (StringUtils.isNotBlank(id)) {
               return id;
            }
         }
      } catch (Exception ignored) {
      }

      return "P" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
   }
}