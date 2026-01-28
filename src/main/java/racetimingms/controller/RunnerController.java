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
import com.google.common.base.Strings;

import racetimingms.constants.Constants;
import racetimingms.model.DatabaseData;
import racetimingms.model.PagingData;
import racetimingms.model.ParticipantData;
import racetimingms.model.ResponseStatus;
import racetimingms.model.ProfileData;
import racetimingms.model.request.UploadParticipantRequest;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.ProfileService;
import racetimingms.service.RunnerService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/participant")
public class RunnerController {

	@Autowired
	private RunnerService runnerService;

	@Autowired
	private ProfileService profileService;

	@Autowired
	private ObjectMapper mapper;

	@GetMapping("/getAllParticipant")
	public ResponseEntity<NormalizedResponse> getAllParticipant(@RequestHeader Map<String, String> headers,
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
		DatabaseData data = runnerService.getAllParticipant(id, user, role, paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getRunnerById")
	public ResponseEntity<NormalizedResponse> getRunnerById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		DatabaseData data = runnerService.getRunnerById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getParticipantTimeCPById")
	public ResponseEntity<NormalizedResponse> getParticipantTimeCPById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "eventUuid", required = false) String eventUuid) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getParticipantTimeCPById(id, eventUuid);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getParticipantById")
	public ResponseEntity<NormalizedResponse> getParticipantById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = runnerService.getParticipantById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getCheckpointMappingByEvent")
	public ResponseEntity<NormalizedResponse> getCheckpointMappingByEvent(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getCheckpointMappingByEvent(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getForCheckCheckpointMappingByEvent")
	public ResponseEntity<NormalizedResponse> getForCheckCheckpointMappingByEvent(
			@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Boolean data = runnerService.checkCheckpointMappingByEvent(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/createRunner")
	public ResponseEntity<NormalizedResponse> createRunner(@RequestHeader Map<String, String> headers,
			@RequestBody List<UploadParticipantRequest> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		runnerService.createRunner(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateRunnerUpload")
	public ResponseEntity<NormalizedResponse> updateRunnerUpload(@RequestHeader Map<String, String> headers,
			@RequestBody List<UploadParticipantRequest> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		runnerService.updateRunnerUpload(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateRunner")
	public ResponseEntity<NormalizedResponse> updateRunner(@RequestHeader Map<String, String> headers,
			@RequestBody ProfileData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		profileService.updateProfile(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateParticipant")
	public ResponseEntity<NormalizedResponse> updateParticipant(@RequestHeader Map<String, String> headers,
			@RequestBody ParticipantData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		//remove idNo checking
		runnerService.updateParticipant(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteRunner")
	public ResponseEntity<NormalizedResponse> deleteRunner(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		runnerService.deleteRunner(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteParticipant")
	public ResponseEntity<NormalizedResponse> deleteParticipant(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		runnerService.deleteParticipant(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteAllParticipants")
	public ResponseEntity<NormalizedResponse> deleteAllParticipants(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		runnerService.deleteAllParticipants(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
