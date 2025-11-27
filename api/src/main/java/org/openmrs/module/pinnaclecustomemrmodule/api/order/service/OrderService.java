package org.openmrs.module.pinnaclecustomemrmodule.api.order.service;

public interface OrderService {
    DrugOrder prescribeMedication(Integer patientId, String drugName, Double dose, String doseUnit,
                                        String route, String frequency, Integer durationDays);
    
    TestOrder orderLabTest(Integer patientId, String testName);
    
}
