package org.openmrs.module.pinnaclecustomemrmodule.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.pinnaclecustomemrmodule.api.clinical.service.ClinicalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pinnacle/api/v1")
public class ClinicalController {

    @Autowired
    private ClinicalService clinicalService;

    // ==================== SOAP Notes ====================
    @PostMapping("/encounters/{encounterId}/soap")
    public ResponseEntity<Map<String, Object>> saveSoapNote(
            @PathVariable Integer encounterId,
            @RequestBody Map<String, String> soapData) {

        validateSoapData(soapData);

        Obs soapObs = clinicalService.saveSoapNote(
                encounterId,
                soapData.get("subjective"),
                soapData.get("objective"),
                soapData.get("assessment"),
                soapData.get("plan")
        );

        return ResponseEntity.ok(buildSuccessResponse("SOAP note saved successfully", soapObs.getUuid()));
    }

    // ==================== Vital Signs ====================
    @PostMapping("/encounters/{encounterId}/vitals")
    public ResponseEntity<Map<String, Object>> saveVitals(
            @PathVariable Integer encounterId,
            @RequestBody Map<String, Object> vitals) {

        if (vitals == null || vitals.isEmpty()) {
            return ResponseEntity.badRequest().body(buildErrorResponse("Vitals data is required"));
        }

        Obs vitalsGroup = clinicalService.saveVitals(encounterId, vitals);

        Map<String, Object> response = buildSuccessResponse("Vital signs recorded", vitalsGroup.getUuid());
        response.put("recorded_vitals", vitals.keySet());
        return ResponseEntity.ok(response);
    }

    
    @PostMapping("/patients/{patientId}/vital")
    public ResponseEntity<Map<String, Object>> recordSingleVital(
            @PathVariable Integer patientId,
            @RequestBody Map<String, Object> payload) {

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) return ResponseEntity.notFound().build();

        String concept = (String) payload.get("conceptName");
        Object valueObj = payload.get("value");
        if (concept == null || valueObj == null) {
            return ResponseEntity.badRequest().body(buildErrorResponse("conceptName and value are required"));
        }

        Double value;
        try {
            value = Double.valueOf(valueObj.toString());
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(buildErrorResponse("Invalid numeric value"));
        }

        Obs obs = clinicalService.recordVital(patient, concept, value);
        return ResponseEntity.ok(buildSuccessResponse("Vital recorded", obs.getUuid()));
    }

    
    @GetMapping("/patients/{patientId}/vitals")
    public ResponseEntity<List<Map<String, Object>>> getPatientVitals(
            @PathVariable Integer patientId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) return ResponseEntity.notFound().build();

        List<Obs> vitals = clinicalService.getVitals(patient);
        vitals = vitals.stream().skip(offset).limit(limit).collect(Collectors.toList());

        List<Map<String, Object>> result = vitals.stream()
                .map(obs -> Map.of(
                        "concept", obs.getConcept().getName().getName(),
                        "value", obs.getValueNumeric() != null ? obs.getValueNumeric() : obs.getValueText(),
                        "date", obs.getObsDatetime(),
                        "uuid", obs.getUuid()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ==================== Diagnoses ====================
    
    @PostMapping("/patients/{patientId}/diagnoses")
    public ResponseEntity<Map<String, Object>> addDiagnosis(
            @PathVariable Integer patientId,
            @RequestBody Map<String, Integer> request) {

        Integer icdConceptId = request.get("icdConceptId");
        if (icdConceptId == null) return ResponseEntity.badRequest().body(buildErrorResponse("icdConceptId is required"));

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) return ResponseEntity.notFound().build();

        Obs diagnosis = clinicalService.addDiagnosis(patient, icdConceptId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildSuccessResponse("Diagnosis added", diagnosis.getUuid()));
    }

    
    @GetMapping("/patients/{patientId}/diagnoses")
    public ResponseEntity<List<Map<String, Object>>> getDiagnoses(
            @PathVariable Integer patientId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) return ResponseEntity.notFound().build();

        List<Obs> diagnoses = clinicalService.getDiagnoses(patient);
        diagnoses = diagnoses.stream().skip(offset).limit(limit).collect(Collectors.toList());

        List<Map<String, Object>> result = diagnoses.stream()
                .map(obs -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("diagnosis", obs.getValueCoded() != null ? obs.getValueCoded().getName().getName() : "Unknown");
                    map.put("date", obs.getObsDatetime());
                    map.put("obsId", obs.getId());
                    map.put("uuid", obs.getUuid());
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    
    @DeleteMapping("/diagnoses/{obsId}")
    public ResponseEntity<Map<String, Object>> removeDiagnosis(
            @PathVariable Integer obsId,
            @RequestParam(required = false) String reason) {

        try {
            clinicalService.removeDiagnosis(obsId, reason != null ? reason : "Removed via API");
            return ResponseEntity.ok(buildSuccessResponse("Diagnosis removed successfully", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    
    @PutMapping("/diagnoses/{obsId}")
    public ResponseEntity<Map<String, Object>> updateDiagnosis(
            @PathVariable Integer obsId,
            @RequestBody Map<String, Integer> request) {

        Integer newIcdConceptId = request.get("newIcdConceptId");
        if (newIcdConceptId == null) return ResponseEntity.badRequest().body(buildErrorResponse("newIcdConceptId is required"));

        try {
            Obs updated = clinicalService.updateDiagnosis(obsId, newIcdConceptId);
            return ResponseEntity.ok(buildSuccessResponse("Diagnosis updated", updated.getUuid()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== Helpers ====================
    private void validateSoapData(Map<String, String> soapData) {
        if (soapData == null) throw new IllegalArgumentException("SOAP data is required");
        if (soapData.get("subjective") == null && soapData.get("objective") == null &&
            soapData.get("assessment") == null && soapData.get("plan") == null) {
            throw new IllegalArgumentException("At least one SOAP field must be provided");
        }
    }

    private Map<String, Object> buildSuccessResponse(String message, Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", message);
        resp.put("timestamp", new Date());
        if (data != null) resp.put("data", data);
        return resp;
    }

    private Map<String, Object> buildErrorResponse(String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("error", message);
        resp.put("timestamp", new Date());
        return resp;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(buildErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("An unexpected error occurred: " + ex.getMessage()));
    }
}
