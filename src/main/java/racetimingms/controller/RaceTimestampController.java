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
import racetimingms.model.DatabaseData;
import racetimingms.model.PagingData;
import racetimingms.model.ResponseStatus;
import racetimingms.model.ResultData;
import racetimingms.model.TimeRecordData;
import racetimingms.model.request.UploadParticipantAndTimeRequest;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.RaceTimestampService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/raceTimestamp")
public class RaceTimestampController {
    
	@Autowired
	private RaceTimestampService raceTimestampService;

	@Autowired
	private ObjectMapper mapper;

	@PostMapping("/updateRaceTimestamp")
	public ResponseEntity<NormalizedResponse> updateRaceTimestamp(@RequestHeader Map<String, String> headers,
			@RequestBody ResultData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		raceTimestampService.updateRaceTimestamp(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateParticipantTimeCP")
	public ResponseEntity<NormalizedResponse> updateParticipantTimeCP(@RequestHeader Map<String, String> headers,
			@RequestBody List<TimeRecordData> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		raceTimestampService.updateParticipantTimeCP(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateParticipantAndTime")
	public ResponseEntity<NormalizedResponse> updateParticipantAndTime(@RequestHeader Map<String, String> headers,
			@RequestBody List<UploadParticipantAndTimeRequest> body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		raceTimestampService.updateParticipantAndTime(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteRaceTimestamp")
	public ResponseEntity<NormalizedResponse> deleteRaceTimestamp(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		raceTimestampService.deleteRaceTimestamp(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
