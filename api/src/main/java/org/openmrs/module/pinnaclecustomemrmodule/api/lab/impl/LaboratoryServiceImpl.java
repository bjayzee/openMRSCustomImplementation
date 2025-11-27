package org.openmrs.module.pinnaclecustomemrmodule.api.lab.impl;

import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pinnaclecustomemrmodule.api.lab.service.LaboratoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service("pinnaclecustomemrmodule.LaboratoryService")
@Transactional
public class LaboratoryServiceImpl extends BaseOpenmrsService implements LaboratoryService {

    @Autowired private ObsService obsService;
    @Autowired private ConceptService conceptService;

    private static final String LAB_RESULT_CONCEPT = "Laboratory Test Result";
    private static final String LAB_TEST_ORDER_CONCEPT = "Laboratory Test";
    private static final String IMAGING_REPORT_CONCEPT = "Imaging Report";

    @Override
    public Obs receiveLabResult(Integer patientId, String testName, String resultValue, String units,
                                String referenceRange, String status, String accessionNumber) {

        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) throw new IllegalArgumentException("Patient not found: " + patientId);

        Concept testConcept = conceptService.getConceptByName(testName);
        if (testConcept == null) {
            throw new IllegalArgumentException("Lab test concept not found: " + testName);
        }

        Concept resultGroupConcept = getOrCreateConcept(LAB_RESULT_CONCEPT, "Test", "Misc");

        Obs resultGroup = new Obs();
        resultGroup.setPerson(patient);
        resultGroup.setObsDatetime(new Date());
        resultGroup.setConcept(resultGroupConcept);
        resultGroup.setAccessionNumber(accessionNumber);
        resultGroup.setComment("Status: " + status + " | Ref: " + referenceRange);

        // Main result
        Obs valueObs = new Obs();
        valueObs.setPerson(patient);
        valueObs.setObsDatetime(new Date());
        valueObs.setConcept(testConcept);

        if (isNumeric(resultValue)) {
            valueObs.setValueNumeric(Double.parseDouble(resultValue.replaceAll("[^0-9.-]", "")));
        } else {
            valueObs.setValueText(resultValue + (units != null ? " " + units : ""));
        }

        resultGroup.addGroupMember(valueObs);
        return obsService.saveObs(resultGroup, "Lab result received via Pinnacle EMR");
    }

    @Override
    public List<Obs> getPendingLabTests(Integer patientId) {
        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) return List.of();

        Concept labOrderConcept = getOrCreateConcept(LAB_TEST_ORDER_CONCEPT, "Test", "Procedure");

        return obsService.getObservationsByPersonAndConcept(patient, labOrderConcept).stream()
                .filter(obs -> !obs.getVoided())
                .filter(orderObs -> {
                    // Check if result exists
                    Concept testConcept = orderObs.getValueCoded();
                    if (testConcept == null) return true;
                    return obsService.getObservationsByPersonAndConcept(patient, testConcept).isEmpty();
                })
                .collect(Collectors.toList());
    }

    @Override
    public Obs attachImagingReport(Integer encounterId, String studyType, String pacsUrl, String reportText) {
        Encounter encounter = Context.getEncounterService().getEncounter(encounterId);
        if (encounter == null) throw new IllegalArgumentException("Encounter not found: " + encounterId);

        Concept imagingConcept = getOrCreateConcept(IMAGING_REPORT_CONCEPT, "Finding", "Misc");

        Obs reportObs = new Obs();
        reportObs.setPerson(encounter.getPatient());
        reportObs.setEncounter(encounter);
        reportObs.setObsDatetime(new Date());
        reportObs.setConcept(imagingConcept);
        reportObs.setValueText(studyType + " Report\n\nPACS URL: " + pacsUrl + "\n\n" + reportText);

        return obsService.saveObs(reportObs, "Imaging report attached via Pinnacle EMR");
    }

    private Concept getOrCreateConcept(String name, String datatypeName, String className) {
        Concept c = conceptService.getConceptByName(name);
        if (c != null) return c;

        c = new Concept();
        ConceptDatatype datatype = conceptService.getConceptDatatypeByName(datatypeName);
        ConceptClass conceptClass = conceptService.getConceptClassByName(className);

        if (datatype == null || conceptClass == null) {
            throw new IllegalStateException("Required datatype or class not found: " + datatypeName + "/" + className);
        }

        c.setDatatype(datatype);
        c.setConceptClass(conceptClass);

        ConceptName cn = new ConceptName(name, Context.getLocale());
        cn.setConceptNameType(ConceptNameType.FULLY_SPECIFIED);
        c.addName(cn);

        return conceptService.saveConcept(c);
    }

    private boolean isNumeric(String str) {
        if (str == null) return false;
        try {
            Double.parseDouble(str.replaceAll("[^0-9.-]", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}