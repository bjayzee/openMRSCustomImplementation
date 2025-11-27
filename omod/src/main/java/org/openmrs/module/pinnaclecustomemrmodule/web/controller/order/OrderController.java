package org.openmrs.module.pinnaclecustomemrmodule.web.controller.order;

import org.openmrs.DrugOrder;
import org.openmrs.Patient;
import org.openmrs.TestOrder;
import org.openmrs.api.context.Context;
import org.openmrs.module.pinnaclecustomemrmodule.api.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/pinnacle/api/v1")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * Prescribe medication with automatic allergy check
     * POST /pinnacle/api/v1/patients/{patientId}/prescriptions
     */
    @PostMapping("/patients/{patientId}/prescriptions")
    public ResponseEntity<Map<String, Object>> prescribeMedication(
            @PathVariable Integer patientId,
            @RequestBody PrescriptionRequest request) {

        validatePrescriptionRequest(request);

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Patient not found: " + patientId));
        }

        try {
            DrugOrder drugOrder = orderService.prescribeMedication(
                    patientId,
                    request.getDrugName(),
                    request.getDose(),
                    request.getDoseUnit(),
                    request.getRoute(),
                    request.getFrequency(),
                    request.getDurationDays()
            );

            Map<String, Object> data = new HashMap<>();
            data.put("orderUuid", drugOrder.getUuid());
            data.put("drug", request.getDrugName());
            data.put("dose", request.getDose() + " " + request.getDoseUnit());
            data.put("route", request.getRoute());
            data.put("frequency", request.getFrequency());
            data.put("durationDays", request.getDurationDays());
            data.put("startDate", drugOrder.getStartDate());
            data.put("status", "ACTIVE");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(buildSuccessResponse("Medication prescribed successfully", data));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(buildErrorResponse("ALLERGY CONFLICT: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("Failed to prescribe: " + e.getMessage()));
        }
    }

    /**
     * Order a lab test
     * POST /pinnacle/api/v1/patients/{patientId}/lab-orders
     */
    @PostMapping("/patients/{patientId}/lab-orders")
    public ResponseEntity<Map<String, Object>> orderLabTest(
            @PathVariable Integer patientId,
            @RequestBody LabOrderRequest request) {

        if (request.getTestName() == null || request.getTestName().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(buildErrorResponse("testName is required"));
        }

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorResponse("Patient not found: " + patientId));
        }

        try {
            TestOrder testOrder = orderService.orderLabTest(patientId, request.getTestName());

            Map<String, Object> data = new HashMap<>();
            data.put("orderUuid", testOrder.getUuid());
            data.put("testName", request.getTestName());
            data.put("orderNumber", testOrder.getOrderNumber());
            data.put("orderedDate", testOrder.getDateActivated());
            data.put("status", "ACTIVE");
            data.put("careSetting", "Outpatient");

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(buildSuccessResponse("Lab test ordered successfully", data));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(buildErrorResponse("Failed to order lab test: " + e.getMessage()));
        }
    }

    // ==================== Request DTOs ====================

    public static class PrescriptionRequest {
        private String drugName;
        private Double dose;
        private String doseUnit;     // e.g., "mg", "tablet(s)"
        private String route;        // e.g., "Oral", "IV"
        private String frequency;    // e.g., "Once daily", "Twice daily"
        private Integer durationDays;

        // Getters and Setters
        public String getDrugName() { return drugName; }
        public void setDrugName(String drugName) { this.drugName = drugName; }
        public Double getDose() { return dose; }
        public void setDose(Double dose) { this.dose = dose; }
        public String getDoseUnit() { return doseUnit; }
        public void setDoseUnit(String doseUnit) { this.doseUnit = doseUnit; }
        public String getRoute() { return route; }
        public void setRoute(String route) { this.route = route; }
        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }
        public Integer getDurationDays() { return durationDays; }
        public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }
    }

    public static class LabOrderRequest {
        private String testName;  // Must match concept name in dictionary

        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
    }

    // ==================== Validation & Response Helpers ====================

    private void validatePrescriptionRequest(PrescriptionRequest req) {
        if (req.getDrugName() == null || req.getDrugName().trim().isEmpty())
            throw new IllegalArgumentException("drugName is required");
        if (req.getDose() == null || req.getDose() <= 0)
            throw new IllegalArgumentException("Valid dose is required");
        if (req.getDoseUnit() == null || req.getDoseUnit().trim().isEmpty())
            throw new IllegalArgumentException("doseUnit is required");
        if (req.getRoute() == null || req.getRoute().trim().isEmpty())
            throw new IllegalArgumentException("route is required");
        if (req.getFrequency() == null || req.getFrequency().trim().isEmpty())
            throw new IllegalArgumentException("frequency is required");
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

    // ==================== Global Exception Handler ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(buildErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleError(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse("Server error: " + ex.getMessage()));
    }
}