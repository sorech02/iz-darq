package gov.nist.healthcare.iz.darq.digest.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import gov.nist.healthcare.iz.darq.digest.domain.ADChunk;
import gov.nist.healthcare.iz.darq.digest.domain.Fraction;
import gov.nist.healthcare.iz.darq.digest.domain.PatientPayload;
import gov.nist.healthcare.iz.darq.digest.domain.VaccinationPayload;
import gov.nist.healthcare.iz.darq.digest.service.AgeGroupService;
import gov.nist.healthcare.iz.darq.digest.service.ConfigurationProvider;
import gov.nist.healthcare.iz.darq.digest.service.RecordChewer;
import gov.nist.healthcare.iz.darq.parser.model.AggregatePatientRecord;
import gov.nist.healthcare.iz.darq.parser.model.VaccineRecord;

@Service
public class SimpleRecordChewer implements RecordChewer {

	@Autowired
	private SimpleGroupService groupService;
	@Autowired
	private LineItemizer itemizer;

	
	@Override
	public ADChunk munch(ConfigurationProvider configuration, AggregatePatientRecord apr, LocalDate date) {
		AgeGroupService ageGroupCalculator = configuration.ageGroupService();
		DQAValidator validator = new DQAValidator(ageGroupCalculator, configuration.detectionFilter());
		CodeCollector collector = new CodeCollector();
		
		validator.validateRecord(apr, date);
		try {
			collector.collectPatient(itemizer.itemizePatient(apr.patient), ageGroupCalculator.getGroup(apr.patient.date_of_birth.toString(), date.toString("yyyyMMdd")));
			for(VaccineRecord vr : apr.history){
				collector.collectVaccination(itemizer.itemizeVax(vr), ageGroupCalculator.getGroup(apr.patient.date_of_birth.toString(), vr.administration_date.toString()), vr.admin_location.toString());
			}
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		System.out.println(validator.patientDetections().get("2g").get("MQE0141"));
		
		Map<String, String> providers = new HashMap<>();
		Map<String, Map<String, VaccinationPayload>> vaccinationSection = this.groupService.makeVxSectionProvider(configuration.ageGroupService(), validator.getVxInfo(), validator.vaccinationDetections(), collector.getVaccinationCodes());
		Map<String, PatientPayload> patientSection = this.groupService.makePatSectionAge(configuration.ageGroupService(), validator.patientDetections(), collector.getPatientCodes());
		Map<String, Fraction> extraction = collector.getExtract();
		List<String> issues = new ArrayList<>();
//		System.out.println(patientSection.get("2g").getDetection().get("MQE0141"));
		
		Map<String, Map<String, VaccinationPayload>> deIdentifiedSection = new HashMap<>();
		for(String provider : vaccinationSection.keySet()){
			String hash = DigestUtils.md5DigestAsHex(provider.getBytes());
			deIdentifiedSection.put(hash, vaccinationSection.get(provider));
			providers.put(provider, hash);
		}
		
		
		return new ADChunk(providers, deIdentifiedSection, patientSection, extraction, issues, apr.history.size(), 1);
	}	

}
