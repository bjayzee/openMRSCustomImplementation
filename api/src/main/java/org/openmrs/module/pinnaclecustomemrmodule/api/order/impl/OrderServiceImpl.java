package org.openmrs.module.pinnaclecustomemrmodule.api.order.impl;

import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@Transactional
public class OrderServiceImpl extends BaseOpenmrsService implements OrderService {

    @Override
    public DrugOrder prescribeMedication(Integer patientId, String drugName, Double dose, String doseUnit,
                                        String route, String frequency, Integer durationDays) {
        Patient patient = Context.getPatientService().getPatient(patientId);
        Drug drug = Context.getConceptService().getDrugByName(drugName);

        if (hasAllergyConflict(patient, drugName)) {
            throw new IllegalStateException("Patient has allergy to: " + drugName);
        }

        DrugOrder order = new DrugOrder();
        order.setPatient(patient);
        order.setDrug(drug);
        order.setDose(dose);
        order.setDoseUnits(Context.getConceptService().getConceptByName(doseUnit));
        order.setRoute(Context.getConceptService().getConceptByName(route));
        order.setFrequency(Context.getOrderService().getOrderFrequencyByConcept(
            Context.getConceptService().getConceptByName(frequency)));
        order.setStartDate(new Date());
        if (durationDays != null) {
            order.setAutoExpireDate(new Date(System.currentTimeMillis() + durationDays * 24L * 60 * 60 * 1000));
        }
        order.setOrderReasonNonCoded("Prescribed in OPD");

        return (DrugOrder) Context.getOrderService().saveOrder(order, Context.getAuthenticatedUser());
    }

    @Override
    public TestOrder orderLabTest(Integer patientId, String testName) {
        Patient patient = Context.getPatientService().getPatient(patientId);
        Concept testConcept = Context.getConceptService().getConceptByName(testName);

        TestOrder order = new TestOrder();
        order.setPatient(patient);
        order.setConcept(testConcept);
        order.setOrderType(Context.getOrderService().getOrderTypeByName("Lab Order"));
        order.setCareSetting(Context.getOrderService().getCareSettingByName("Outpatient"));

        return (TestOrder) Context.getOrderService().saveOrder(order, Context.getAuthenticatedUser());
    }

    private boolean hasAllergyConflict(Patient patient, String drugName) {
        String sql = """
            SELECT COUNT(*) FROM obs o
            JOIN concept_name cn ON o.value_coded = cn.concept_id
            WHERE o.person_id = ? AND o.concept_id = (
                SELECT concept_id FROM concept WHERE uuid = '1427AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
            ) AND LOWER(cn.name) LIKE LOWER(?)
            """; // Allergy concept
        List<List<Object>> result = Context.getAdministrationService().executeSQL(sql, true, patient.getId(), "%" + drugName + "%");
        return result.get(0).get(0).toString().equals("0") ? false : true;
    }
}