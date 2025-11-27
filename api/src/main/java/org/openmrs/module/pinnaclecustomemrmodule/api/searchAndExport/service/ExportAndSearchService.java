package org.openmrs.module.pinnaclecustomemrmodule.api.searchAndExport.service;

import java.io.IOException;

public interface ExportAndSearchService {
    byte[] exportPatientRecordAsPdf(Integer patientId, String watermark) throws IOException;
    byte[] exportPatientSummaryAsPdf(Integer patientId) throws IOException;
}