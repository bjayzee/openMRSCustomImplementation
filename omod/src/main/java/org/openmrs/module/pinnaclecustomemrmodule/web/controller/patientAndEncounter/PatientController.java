package org.openmrs.module.pinnaclecustomemrmodule.web.controller.patientAndEncounter;

import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.activelist.Allergy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/pinnacle/api/patients")
public class PatientController {

    @Autowired
    private MpiService mpiService;

    @Autowired
    private AllergyService allergyService;


    @PostMapping
    public Patient createPatient(@RequestBody Patient p) {
        return mpiService.createPatient(p);
    }

    @GetMapping("/search")
    public List<Patient> search(@RequestParam("q") String q) {
        return mpiService.searchPatients(q);
    }

    @GetMapping("/{uuid}")
    public Patient getByUuid(@PathVariable String uuid) {
        return mpiService.getPatientByUuid(uuid);
    }

    @PostMapping("/merge")
    public Patient merge(@RequestParam Integer sourceId, @RequestParam Integer targetId,
            @RequestParam(required = false) String reason) {
        return mpiService.mergePatients(sourceId, targetId, reason);
    }

    @PostMapping("/split")
    public Patient split(@RequestParam Integer mergedPatientId, @RequestBody(required = false) Integer[] encounterIds,
            @RequestParam(required = false) String reason) {
        return mpiService.splitPatient(mergedPatientId, encounterIds, reason);
    }

    @PostMapping("/{uuid}/allergies")
    public Allergy addAllergy(@PathVariable String uuid, @RequestParam String allergenConcept,
            @RequestParam(required = false) String reactionConcept,
            @RequestParam(required = false) String severityConcept) {
        Patient p = mpiService.getPatientByUuid(uuid);
        return allergyService.recordAllergy(p, allergenConcept, reactionConcept, severityConcept);
    }

    @GetMapping("/{uuid}/allergies")
    public List<Allergy> getAllergies(@PathVariable String uuid) {
        Patient p = mpiService.getPatientByUuid(uuid);
        return allergyService.getAllergies(p);
    }

}
