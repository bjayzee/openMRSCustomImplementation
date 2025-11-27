package org.openmrs.module.pinnaclecustomemrmodule.web.controller.lab;

import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.pinnaclecustomemrmodule.api.lab.service.LaboratoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pinnacle/api/v1")
public class LaboratoryController {

    @Autowired
    private LaboratoryService laboratoryService;

    /**
     * Receive lab result from LIS (e.g., via HL7, FHIR, or direct API call)
     * POST /pinnacle/api/v1/lab/results
     */
    @PostMapping("/lab/results")
    public ResponseEntity<Map<String, Object>> receiveLabResult(@RequestBody LabResultRequest request) {
        validateLabResultRequest(request);

        Patient patient = Context.getPatientService().getPatient(request.getPatientId());
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Patient not found: " + request.getPatientId()));
        }

        Obs resultObs = laboratoryService.receiveLabResult(
                request.getPatientId(),
                request.getTestName(),
                request.getResultValue(),
                request.getUnits(),
                request.getReferenceRange(),
                request.getStatus(),
                request.getAccessionNumber()
        );

        Map<String, Object> data = new HashMap<>();
        data.put("obsUuid", resultObs.getUuid());
        data.put("test", request.getTestName());
        data.put("result", request.getResultValue() + " " + request.getUnits());
        data.put("status", request.getStatus());
        data.put("receivedAt", new Date());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildSuccessResponse("Lab result received and saved successfully", data));
    }

    /**
     * Get all pending (ordered but not resulted) lab tests for a patient
     * GET /pinnacle/api/v1/patients/{patientId}/lab/pending
     */
    @GetMapping("/patients/{patientId}/lab/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingLabTests(@PathVariable Integer patientId) {
        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) {
            return ResponseEntity.notFound().build();
        }

        List<Obs> pending = laboratoryService.getPendingLabTests(patientId);

        List<Map<String, Object>> result = pending.stream()
                .map(obs -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("orderObsUuid", obs.getUuid());
                    map.put("testName", obs.getValueCoded() != null ?
                            obs.getValueCoded().getName().getName() : "Unknown Test");
                    map.put("orderedDate", obs.getObsDatetime());
                    map.put("orderNumber", obs.getOrder() != null ? obs.getOrder().getOrderNumber() : null);
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Attach imaging report (X-ray, CT, MRI, Ultrasound) with PACS link
     * POST /pinnacle/api/v1/encounters/{encounterId}/imaging
     */
    @PostMapping("/encounters/{encounterId}/imaging")
    public ResponseEntity<Map<String, Object>> attachImagingReport(
            @PathVariable Integer encounterId,
            @RequestBody ImagingReportRequest request) {

        if (request.getStudyType() == null || request.getPacsUrl() == null) {
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("studyType and pacsUrl are required"));
        }

        Obs reportObs = laboratoryService.attachImagingReport(
                encounterId,
                request.getStudyType(),
                request.getPacsUrl(),
                request.getReportText() != null ? request.getReportText() : ""
        );

        Map<String, Object> data = new HashMap<>();
        data.put("obsUuid", reportObs.getUuid());
        data.put("studyType", request.getStudyType());
        data.put("pacsUrl", request.getPacsUrl());
        data.put("attachedAt", new Date());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildSuccessResponse("Imaging report attached successfully", data));
    }

    // ==================== Request DTOs ====================

    public static class LabResultRequest {
        private Integer patientId;
        private String testName;
        private String resultValue;
        private String units;
        private String referenceRange;
        private String status = "FINAL";
        private String accessionNumber;

        // Getters and Setters
        public Integer getPatientId() { return patientId; }
        public void setPatientId(Integer patientId) { this.patientId = patientId; }
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        public String getResultValue() { return resultValue; }
        public void setResultValue(String resultValue) { this.resultValue = resultValue; }
        public String getUnits() { return units; }
        public void setUnits(String units) { this.units = units; }
        public String getReferenceRange() { return referenceRange; }
        public void setReferenceRange(String referenceRange) { this.referenceRange = referenceRange; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getAccessionNumber() { return accessionNumber; }
        public void setAccessionNumber(String accessionNumber) { this.accessionNumber = accessionNumber; }
    }

    public static class ImagingReportRequest {
        private String studyType;        // e.g., "Chest X-ray", "CT Brain"
        private String pacsUrl;          // e.g., "http://pacs.example.com/viewer?study=12345"
        private String reportText;       // Optional radiologist report

        // Getters and Setters
        public String getStudyType() { return studyType; }
        public void setStudyType(String studyType) { this.studyType = studyType; }
        public String getPacsUrl() { return pacsUrl; }
        public void setPacsUrl(String pacsUrl) { this.pacsUrl = pacsUrl; }
        public String getReportText() { return reportText; }
        public void setReportText(String reportText) { this.reportText = reportText; }
    }

    // ==================== Validation & Response Helpers ====================

    private void validateLabResultRequest(LabResultRequest request) {
        if (request.getPatientId() == null) throw new IllegalArgumentException("patientId is required");
        if (request.getTestName() == null || request.getTestName().trim().isEmpty())
            throw new IllegalArgumentException("testName is required");
        if (request.getResultValue() == null || request.getResultValue().trim().isEmpty())
            throw new IllegalArgumentException("resultValue is required");
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

    // ==================== Global Exception Handling ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(buildErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleServerError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("Internal server error: " + ex.getMessage()));
    }
}