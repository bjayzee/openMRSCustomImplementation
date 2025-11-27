package org.openmrs.module.pinnaclecustomemrmodule.api.searchAndExport.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.openmrs.*;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.pinnaclecustomemrmodule.api.clinical.service.ClinicalService;
import org.openmrs.module.pinnaclecustomemrmodule.api.lab.service.LaboratoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service("pinnaclecustomemrmodule.ExportAndSearchService")
public class ExportAndSearchServiceImpl extends BaseOpenmrsService implements ExportAndSearchService {

    @Autowired private ClinicalService clinicalService;
    @Autowired private LaboratoryService laboratoryService;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd-MMM-yyyy");
    private static final PDFont BOLD = PDType1Font.HELVETICA_BOLD;
    private static final PDFont REGULAR = PD, PDType1Font.HELVETICA;
    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 15;
    private float yPosition = 750;

    @Override
    public byte[] exportPatientRecordAsPdf(Integer patientId, String watermark) throws IOException {
        Patient patient = Context.getPatientService().getPatient(patientId);
        if (patient == null) throw new IllegalArgumentException("Patient not found");

        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        PDPageContentStream content = new PDPageContentStream(document, page);

        yPosition = 750;
        content.setFont(BOLD, 18);
        writeLine(content, "PINNACLE CUSTOM EMR - PATIENT RECORD", 50);
        yPosition -= 30;

        // Header
        content.setFont(BOLD, 14);
        writeLine(content, "Patient Information", 50);
        content.setFont(REGULAR, 11);
        writeLine(content, "Name: " + patient.getPersonName().getFullName(), 70);
        writeLine(content, "ID: " + patient.getPatientIdentifier().getIdentifier(), 70);
        writeLine(content, "DOB: " + SDF.format(patient.getBirthdate()) +
                " | Age: " + patient.getAge() + " | Gender: " + patient.getGender(), 70);
        yPosition -= 10;

        // Allergies
        addSectionHeader(content, "Allergies");
        List<Obs> allergies = Context.getObsService().getObservationsByPersonAndConcept(patient,
                Context.getConceptService().getConceptByUuid("1427AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        if (allergies.isEmpty()) {
            writeLine(content, "No known allergies", 70);
        } else {
            for (Obs obs : allergies) {
                writeLine(content, "• " + obs.getValueCoded().getName().getName(), 70);
            }
        }

        // Diagnoses
        addSectionHeader(content, "Diagnoses");
        List<Obs> diagnoses = clinicalService.getDiagnoses(patient);
        if (diagnoses.isEmpty()) {
            writeLine(content, "No active diagnoses", 70);
        } else {
            for (Obs d : diagnoses) {
                writeLine(content, "• " + d.getValueCoded().getName().getName() +
                        " (" + SDF.format(d.getObsDatetime()) + ")", 70);
            }
        }

        // Latest Vitals
        addSectionHeader(content, "Latest Vital Signs");
        List<Obs> vitals = clinicalService.getVitals(patient);
        Obs latestTemp = findLatest(vitals, "Temperature");
        Obs latestPulse = findLatest(vitals, "Pulse");
        Obs latestBp = findLatest(vitals, "Systolic blood pressure");
        if (latestTemp != null || latestPulse != null) {
            writeLine(content, "Temp: " + (latestTemp != null ? latestTemp.getValueNumeric() + "°C" : "—"), 70);
            writeLine(content, "Pulse: " + (latestPulse != null ? latestPulse.getValueNumeric() + " bpm" : "—"), 70);
            writeLine(content, "BP: " + (latestBp != null ? latestBp.getValueNumeric() : "—") + "/— mmHg", 70);
        } else {
            writeLine(content, "No vital signs recorded", 70);
        }

        // Active Medications
        addSectionHeader(content, "Active Medications");
        List<Order> activeOrders = Context.getOrderService().getActiveOrders(patient, null, null, null);
        if (activeOrders.isEmpty()) {
            writeLine(content, "No active medications", 70);
        } else {
            for (Order o : activeOrders) {
                if (o instanceof DrugOrder) {
                    DrugOrder doo = (DrugOrder) o;
                    writeLine(content, "• " + doo.getDrug().getName() +
                            " " + doo.getDose() + " " + doo.getDoseUnits().getName().getName() +
                            " | " + doo.getRoute().getName().getName() +
                            " | " + doo.getFrequency().getName(), 70);
                }
            }
        }

        // Recent Labs
        addSectionHeader(content, "Recent Laboratory Results");
        List<Obs> labs = Context.getObsService().getObservationsByPersonAndConcept(patient,
                Context.getConceptService().getConceptByName("Laboratory Test Result"));
        if (labs.isEmpty()) {
            writeLine(content, "No lab results", 70);
        } else {
            for (Obs lab : labs) {
                writeLine(content, "• " + lab.getConcept().getName().getName() + ": " +
                        lab.getValueAsString(Context.getLocale()), 70);
            }
        }

        // Watermark
        if (watermark != null) {
            content.beginText();
            content.setFont(BOLD, 80);
            content.setNonStrokingColor(230, 230, 230);
            content.newLineAtOffset(100, 400);
            content.showText(watermark);
            content.endText();
        }

        content.close();

        // Footer
        PDPageContentStream footer = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true);
        footer.beginText();
        footer.setFont(REGULAR, 9);
        footer.newLineAtOffset(50, 30);
        footer.showText("Printed on: " + SDF.format(new Date()) +
                " | By: " + Context.getAuthenticatedUser().getPersonName().getFullName());
        footer.endText();
        footer.close();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();

        // Audit log
        Context.getAdministrationService().executeSQL(
            "INSERT INTO emr_access_log (patient_id, action, user_id, timestamp) VALUES (?, 'PDF_EXPORT_FULL', ?, NOW())",
            false, patientId, Context.getAuthenticatedUser().getId());

        return out.toByteArray();
    }

    @Override
    public byte[] exportPatientSummaryAsPdf(Integer patientId) throws IOException {
        return exportPatientRecordAsPdf(patientId, "CONFIDENTIAL - PINNACLE EMR");
    }

    private void addSectionHeader(PDPageContentStream content, String title) throws IOException {
        yPosition -= 20;
        content.setFont(BOLD, 12);
        writeLine(content, title.toUpperCase(), 50);
        content.setFont(REGULAR, 11);
        yPosition -= 5;
    }

    private void writeLine(PDPageContentStream content, String text, float x) throws IOException {
        if (yPosition < 100) {
            content.close();
            PDPage newPage = new PDPage();
            document.addPage(newPage);
            content = new PDPageContentStream(document, newPage);
            yPosition = 750;
        }
        content.beginText();
        content.newLineAtOffset(x, yPosition);
        content.showText(text);
        content.endText();
        yPosition -= LINE_HEIGHT;
    }

    private Obs findLatest(List<Obs> obsList, String conceptName) {
        return obsList.stream()
                .filter(o -> o.getConcept().getName().getName().equalsIgnoreCase(conceptName))
                .max((a, b) -> a.getObsDatetime().compareTo(b.getObsDatetime()))
                .orElse(null);
    }
}