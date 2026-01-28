package racetimingms.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import racetimingms.constants.Constants;
import racetimingms.model.ResponseStatus;
import racetimingms.model.StationData;
import racetimingms.model.DatabaseData;
import racetimingms.model.EventData;
import racetimingms.model.CampaignData;
import racetimingms.model.CheckpointMappingData;
import racetimingms.model.PagingData;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.CoreService;
import racetimingms.service.EventService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/event")
public class EventController {

	@Autowired
	private EventService eventService;

	@Autowired
	private CoreService coreService;

	@Autowired
	private ObjectMapper mapper;

	@GetMapping("/getAllCampaign")
	public ResponseEntity<NormalizedResponse> getAllCampaign(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "user", required = false) String user,
			@RequestParam(value = "role", required = false) String role,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = eventService.getAllCampaign(id, user, role, paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getEventByCampaign")
	public ResponseEntity<NormalizedResponse> getEventByCampaign(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "user", required = false) String user,
			@RequestParam(value = "role", required = false) String role,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = eventService.getEventByCampaign(id, user, role, paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getEventById")
	public ResponseEntity<NormalizedResponse> getEventById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = eventService.getEventById(id);
		response.setData(data);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getEventTypeById")
	public ResponseEntity<NormalizedResponse> getEventTypeById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = eventService.getEventTypeById(id);
		response.setData(data);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getCheckpointById")
	public ResponseEntity<NormalizedResponse> getCheckpointById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = eventService.getCheckpointById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getCheckpointMappingById")
	public ResponseEntity<NormalizedResponse> getCheckpointMappingById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "campaignUuid", required = false) String campaignUuid,
			@RequestParam(value = "eventUuid", required = false) String eventUuid) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = eventService.getCheckpointMappingById(campaignUuid, eventUuid);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}


	@GetMapping("/getUserStationById")
	public ResponseEntity<NormalizedResponse> getUserStationById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = eventService.getUserStationById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/createCampaign")
	public ResponseEntity<NormalizedResponse> createCampaign(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.createCampaign(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/createEvent")
	public ResponseEntity<NormalizedResponse> createEvent(@RequestHeader Map<String, String> headers,
			@RequestBody EventData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.createEvent(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCampaign")
	public ResponseEntity<NormalizedResponse> updateCampaign(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		if (body.getPrefixPath() != null) {
			if (body.getIsLogo()) {
				String fileName = coreService.checkSameFile(body.getLogoUrl(), body.getUuid(), "campaign",
						"prefixPath", "logoUrl");
				if (fileName != null && !(fileName.equals(body.getLogoUrl()))) {
					body.setLogoUrl(fileName);
				}
			}
			if (body.getIsPicture()) {
				String fileName = coreService.checkSameFile(body.getPictureUrl(), body.getUuid(), "campaign",
						"prefixPath", "pictureUrl");
				if (fileName != null && !(fileName.equals(body.getPictureUrl()))) {
					body.setPictureUrl(fileName);
				}
			}
		}
		eventService.updateCampaign(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCampaignTemplate")
	public ResponseEntity<NormalizedResponse> updateCampaignTemplate(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateCampaignTemplate(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateApproveCertificate")
	public ResponseEntity<NormalizedResponse> updateApproveCertificate(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateApproveCertificate(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCampaignStatus")
	public ResponseEntity<NormalizedResponse> updateCampaignStatus(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateCampaignStatus(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateEvent")
	public ResponseEntity<NormalizedResponse> updateEvent(@RequestHeader Map<String, String> headers,
			@RequestBody EventData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		// if (body.getPrefixPath() != null) {
		// 	if (body.getIsPicture()) {
		// 		String fileName = coreService.checkSameFile(body.getPictureUrl(), body.getUuid(), "event",
		// 				"prefixPath", "pictureUrl");
		// 		if (fileName != null && !(fileName.equals(body.getPictureUrl()))) {
		// 			body.setPictureUrl(fileName);
		// 		}
		// 	}
		// 	if (body.getIsAward()) {
		// 		String fileName = coreService.checkSameFile(body.getAwardUrl(), body.getUuid(), "event",
		// 				"prefixPath", "awardUrl");
		// 		if (fileName != null && !(fileName.equals(body.getAwardUrl()))) {
		// 			body.setAwardUrl(fileName);
		// 		}
		// 	}
		// 	if (body.getIsSouvenir()) {
		// 		String fileName = coreService.checkSameFile(body.getSouvenirUrl(), body.getUuid(), "event",
		// 				"prefixPath", "souvenirUrl");
		// 		if (fileName != null && !(fileName.equals(body.getSouvenirUrl()))) {
		// 			body.setSouvenirUrl(fileName);
		// 		}
		// 	}
		// 	if (body.getIsMap()) {
		// 		String fileName = coreService.checkSameFile(body.getMapUrl(), body.getUuid(), "event",
		// 				"prefixPath", "mapUrl");
		// 		if (fileName != null && !(fileName.equals(body.getMapUrl()))) {
		// 			body.setMapUrl(fileName);
		// 		}
		// 	}
		// 	if (body.getIsSchedule()) {
		// 		String fileName = coreService.checkSameFile(body.getScheduleUrl(), body.getUuid(), "event",
		// 				"prefixPath", "scheduleUrl");
		// 		if (fileName != null && !(fileName.equals(body.getScheduleUrl()))) {
		// 			body.setScheduleUrl(fileName);
		// 		}
		// 	}
		// }
		eventService.updateEvent(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCheckpoint")
	public ResponseEntity<NormalizedResponse> updateCheckpoint(@RequestHeader Map<String, String> headers,
			@RequestBody List<StationData> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateCheckpoint(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCheckpointMapping")
	public ResponseEntity<NormalizedResponse> updateCheckpointMapping(@RequestHeader Map<String, String> headers,
			@RequestBody List<CheckpointMappingData> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateCheckpointMapping(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateCampaignActive")
	public ResponseEntity<NormalizedResponse> updateCampaignActive(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateCampaignActive(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateEventActive")
	public ResponseEntity<NormalizedResponse> updateEventActive(@RequestHeader Map<String, String> headers,
			@RequestBody EventData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateEventActive(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateIsAutoFix")
	public ResponseEntity<NormalizedResponse> updateIsAutoFix(@RequestHeader Map<String, String> headers,
			@RequestBody EventData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateIsAutoFix(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateIsFinished")
	public ResponseEntity<NormalizedResponse> updateIsFinished(@RequestHeader Map<String, String> headers,
			@RequestBody EventData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateIsFinished(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateIsDraft")
	public ResponseEntity<NormalizedResponse> updateIsDraft(@RequestHeader Map<String, String> headers,
			@RequestBody CampaignData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.updateIsDraft(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteCampaign")
	public ResponseEntity<NormalizedResponse> deleteCampaign(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.deleteCampaign(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteEvent")
	public ResponseEntity<NormalizedResponse> deleteEvent(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		eventService.deleteEvent(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
