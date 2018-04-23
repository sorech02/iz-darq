package gov.nist.healthcare.iz.darq.analyzer.domain;

import java.util.ArrayList;
import java.util.List;

import gov.nist.healthcare.iz.darq.digest.domain.ConfigurationPayload;

public class AnalysisReport {
	private String adfName;
	private String name;
	private String description;
	private List<AnalysisSectionResult> sections;
	private ConfigurationPayload configuration;
	
	public AnalysisReport() {
		super();
		this.sections = new ArrayList<>();
	}
	public String getName() {
		return name;
	}
	public String getAdfName() {
		return adfName;
	}
	public void setAdfName(String adfName) {
		this.adfName = adfName;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public List<AnalysisSectionResult> getSections() {
		return sections;
	}
	public void setSections(List<AnalysisSectionResult> sections) {
		this.sections = sections;
	}
	public ConfigurationPayload getConfiguration() {
		return configuration;
	}
	public void setConfiguration(ConfigurationPayload configuration) {
		this.configuration = configuration;
	}

}
