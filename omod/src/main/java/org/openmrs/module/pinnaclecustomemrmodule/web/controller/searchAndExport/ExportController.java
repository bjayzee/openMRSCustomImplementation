package org.openmrs.module.pinnaclecustomemrmodule.web.controller.searchAndExport;

import org.openmrs.module.pinnaclecustomemrmodule.api.searchAndExport.service.ExportAndSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@RestController
@RequestMapping("/pinnacle/api/v1")
public class ExportController {

    @Autowired
    private ExportAndSearchService exportService;

    @GetMapping("/patients/{patientId}/record.pdf")
    public ResponseEntity<byte[]> exportFullRecord(
            @PathVariable Integer patientId,
            @RequestParam(defaultValue = "CONFIDENTIAL") String watermark) throws IOException {

        byte[] pdf = exportService.exportPatientRecordAsPdf(patientId, watermark);

        String filename = "Patient_Record_" + patientId + "_" + new Date().getTime() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/patients/{patientId}/summary.pdf")
    public ResponseEntity<byte[]> exportSummary(@PathVariable Integer patientId) throws IOException {
        byte[] pdf = exportService.exportPatientSummaryAsPdf(patientId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=Summary_" + patientId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}