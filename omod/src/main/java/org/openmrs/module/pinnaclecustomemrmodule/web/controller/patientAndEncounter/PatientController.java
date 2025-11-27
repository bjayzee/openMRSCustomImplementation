package org.openmrs.module.pinnaclecustomemrmodule.web.controller.patientAndEncounter;

import org.openmrs.Allergy;
import org.openmrs.Patient;
// import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.AllergyService;
import org.openmrs.module.pinnaclecustomemrmodule.api.patientAndEncounter.service.MPIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pinnacle/api/v1")
public class PatientController {

    @Autowired private MPIService mpiService;
    // @Autowired private AllergyService allergyService;

    @GetMapping("/patients/search")
    public List<Patient> searchPatients(@RequestParam("q") String q) {
        return mpiService.searchPatients(q);
    }

    @GetMapping("/patients/{uuid}")
    public Patient getPatient(@PathVariable String uuid) {
        return mpiService.getPatientById(uuid);
    }

    @PostMapping("/patients")
    public Patient createPatient(@RequestBody Patient patient) {
        return mpiService.createPatient(patient);
    }

    @PostMapping("/patients/merge")
    public Patient mergePatients(@RequestParam Integer sourceId,
                                 @RequestParam Integer targetId,
                                 @RequestParam(required = false) String reason) {
        return mpiService.mergePatients(sourceId, targetId, reason);
    }

    @PostMapping("/patients/split")
    public Patient splitPatient(@RequestParam Integer mergedPatientId,
                                @RequestParam(value = "encounterIds", required = false) List<Integer> encounterIds,
                                @RequestParam(required = false) String reason) {
        Integer[] ids = encounterIds != null ? encounterIds.toArray(new Integer[0]) : null;
        return mpiService.splitPatient(mergedPatientId, ids, reason);
    }

    // @PostMapping("/patients/{uuid}/allergies")
    // public Allergy recordAllergy(@PathVariable String uuid,
    //                              @RequestParam String allergen,
    //                              @RequestParam(required = false) String reaction,
    //                              @RequestParam(required = false) String severity) {
    //     Patient patient = mpiService.getPatientById(uuid);
    //     if (patient == null) throw new RuntimeException("Patient not found");
    //     return allergyService.recordAllergy(patient, allergen, reaction, severity);
    // }

    // @GetMapping("/patients/{uuid}/allergies")
    // public List<Allergy> getAllergies(@PathVariable String uuid) {
    //     Patient patient = mpiService.getPatientById(uuid);
    //     return allergyService.getAllergies(patient);
    // }
}