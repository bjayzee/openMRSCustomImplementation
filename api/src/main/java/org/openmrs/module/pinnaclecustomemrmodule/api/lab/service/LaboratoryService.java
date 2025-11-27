package org.openmrs.module.pinnaclecustomemrmodule.api.lab.service;

import org.openmrs.Obs;
import java.util.List;

public interface LaboratoryService {

    Obs receiveLabResult(Integer patientId, String testName, String resultValue, String units,
            String referenceRange, String status, String accessionNumber);

    
    List<Obs> getPendingLabTests(Integer patientId);

    
    Obs attachImagingReport(Integer encounterId, String studyType, String pacsUrl, String reportText);
}