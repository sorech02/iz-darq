package gov.nist.healthcare.iz.darq.controller.route;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import gov.nist.healthcare.iz.darq.access.security.CustomSecurityExpressionRoot;
import gov.nist.healthcare.iz.darq.controller.service.DescriptorService;
import gov.nist.healthcare.iz.darq.model.*;
import gov.nist.healthcare.iz.darq.repository.AnalysisReportRepository;
import gov.nist.healthcare.iz.darq.service.exception.NotFoundException;
import gov.nist.healthcare.iz.darq.repository.ADFMetaDataRepository;
import gov.nist.healthcare.iz.darq.users.domain.User;
import gov.nist.healthcare.iz.darq.users.facility.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import gov.nist.healthcare.iz.darq.adf.service.ADFStore;
import gov.nist.healthcare.iz.darq.adf.service.ADFStoreUploadHandler;
import gov.nist.healthcare.iz.darq.model.ADFDescriptor;
import gov.nist.healthcare.domain.OpAck;
import gov.nist.healthcare.domain.OpAck.AckStatus;
import gov.nist.healthcare.iz.darq.repository.DigestConfigurationRepository;
import gov.nist.healthcare.iz.darq.service.utils.ConfigurationService;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class ADFController {

	@Autowired
	private ADFStoreUploadHandler uploadHandler;
	@Autowired
	private ADFMetaDataRepository repo;
	@Autowired
	private ConfigurationService configService;
	@Autowired
	private DigestConfigurationRepository confRepo;
	@Autowired
	private ADFStore storage;
	@Autowired
	private FacilityService facilityService;
	@Autowired
	private AnalysisReportRepository analysisReportRepository;
	@Autowired
	private DescriptorService descriptorService;

	//  Facilities for user
	@RequestMapping(value = "/adf/facilities", method = RequestMethod.GET)
	@ResponseBody
	public List<UserFacilityView> forUser(
			@AuthenticationPrincipal User user
	) {
		return user.getPermissions().getFacilities().stream().map((facilityId) -> {
			try {
				Facility facility = this.facilityService.getFacilityById(facilityId);
				int files = this.repo.findByFacilityId(facilityId).size();
				int reports = this.analysisReportRepository.findByPublishedAndFacilityId(true, facilityId).size();
				return new UserFacilityView(facilityId, facility.getName(), reports, files);
			} catch (NotFoundException e) {
				return null;
			}
		}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	@RequestMapping(value = "/adf/private", method = RequestMethod.GET)
	@ResponseBody
	@PreAuthorize("hasPermission(DATA_PRIVATE_AUTHOR)")
	public int numberOfPrivateADF(
			@AuthenticationPrincipal User user
	) {
		return this.repo.findByOwnerIdAndFacilityId(user.getId(), null).size();
	}
    
	@RequestMapping(value="/adf/upload", method=RequestMethod.POST)
	@ResponseBody
	@PreAuthorize("AccessOperation(ADF, UPLOAD, #facility != null ? FACILITY(#facility) : GLOBAL)")
	public OpAck<Void> create(
			@AuthenticationPrincipal User user,
			@RequestParam("file") MultipartFile file,
			@RequestParam("name") String name,
			@RequestParam(value = "facility", required = false) String facility
	) throws Exception{
		InputStream stream = file.getInputStream();
		long size = file.getSize();
		uploadHandler.handle(name, facility, stream, user.getId(), size);
		return new OpAck<>(AckStatus.SUCCESS, "ADF Uploaded", null, "upload");
	}
	
	@RequestMapping(value="/adf/facility/{facilityId}", method=RequestMethod.GET)
	@ResponseBody
	@PreAuthorize("AccessOperation(ADF, VIEW, FACILITY(#facilityId))")
	public List<ADFDescriptor> listFacility(
			@AuthenticationPrincipal User user,
			@PathVariable("facilityId") String facilityId) {
		List<UserUploadedFile> files = repo.findByFacilityId(facilityId);
		return getADFDescriptorFromFiles(files, user);
	}


	@RequestMapping(value="/adf", method=RequestMethod.GET)
	@ResponseBody
	@PreAuthorize("AccessOperation(ADF, VIEW, GLOBAL, OWNED())")
	public List<ADFDescriptor> list(
			@AuthenticationPrincipal User user) {
		List<UserUploadedFile> files = repo.findByOwnerIdAndFacilityIdIsNull(user.getId());
		return getADFDescriptorFromFiles(files, user);
	}

	@RequestMapping(value="/adf/{id}", method=RequestMethod.GET)
	@ResponseBody
	@PreAuthorize("AccessResource(#request, ADF, VIEW, #id)")
	public UserUploadedFile get(
			HttpServletRequest request,
			@PathVariable("id") String id) throws Exception {
		return (UserUploadedFile) request.getAttribute(CustomSecurityExpressionRoot.RESOURCE_ATTRIBUTE);
	}
	
	@RequestMapping(value="/adf/{id}", method=RequestMethod.DELETE)
	@ResponseBody
	@PreAuthorize("AccessResource(ADF, DELETE, #id)")
	public OpAck<UserUploadedFile> delete(
			@PathVariable("id") String id) throws Exception {
		storage.delete(id);
		return new OpAck<>(AckStatus.SUCCESS, "File Deleted Successfully", null, "adf-delete");
	}

	List<ADFDescriptor> getADFDescriptorFromFiles(List<UserUploadedFile> files, User user) {
		List<ADFDescriptor> result = new ArrayList<>();
		List<DigestConfiguration> configurations = this.confRepo.findAccessibleTo(user.getId());
		for(UserUploadedFile md : files){
			result.add(this.descriptorService.getADFDescriptor(md, this.configService.compatibilities(md.getConfiguration(), configurations)));
		}
		return result;
	}

}