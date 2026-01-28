package racetimingms.controller;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import racetimingms.model.UserData;
import racetimingms.model.UserTokenData;
import racetimingms.model.request.RaceTimestampRequest;
import racetimingms.model.request.UserStationRequest;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.DatabaseService;
import racetimingms.service.EmailService;
import racetimingms.service.EventService;
import racetimingms.service.RaceTimestampService;
import racetimingms.service.RunnerService;
import racetimingms.service.UserService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/public-api")
public class PublicAPIController {

	@Autowired
	private UserService userService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EventService eventService;

	@Autowired
	private RunnerService runnerService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private RaceTimestampService raceTimestampService;

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private DatabaseService databaseService;

	@PostMapping("/register")
	public ResponseEntity<NormalizedResponse> register(
			@RequestBody UserData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> userData = userService.checkUserEmail(body.getEmail());
		if (userData == null || userData.isEmpty()) {
			body.setPassword(passwordEncoder.encode(body.getPassword()));
			userService.createUser(body);

			response.setStatus(
					ResponseStatus.builder().code("200").description("success").build());
			return new ResponseEntity<>(response, HttpStatus.OK);
		} else {
			response.setStatus(
					ResponseStatus.builder().code("10005").description("มีอีเมลอยู่ในระบบแล้ว").build());
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
	}

	@PostMapping("/loginStation")
	public ResponseEntity<NormalizedResponse> loginStation(@RequestHeader Map<String, String> headers,
			@RequestBody UserStationRequest body) throws SQLException, UnsupportedEncodingException {
		NormalizedResponse response = new NormalizedResponse();

		String encodedHeader = URLEncoder.encode(body.getUsername() + body.getCampaignUuid() + body.getPassword(),
				StandardCharsets.UTF_8.name());

		if (encodedHeader.equals(headers.get("authorizationstation"))) {
			Map<String, Object> data = userService.userStation(body);
			response.setStatus(ResponseStatus.builder().code("200").description("success").build());
			response.setData(data);
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		response.setStatus(ResponseStatus.builder().code("404").description("Not Found").build());
		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
	}

	@PostMapping("/checkUserEmail")
	public ResponseEntity<NormalizedResponse> checkUserEmail(@RequestHeader Map<String, String> headers,
			@RequestBody UserData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = userService.checkUserEmail(body.getEmail());
		if (data != null && !data.isEmpty()) {
			Integer tokenId = userService.createUserToken(((Number) data.get("id")).intValue());
			if (tokenId >= 1) {
				String tokenUuid = databaseService.getUuidById("token", tokenId);
				String username = data.get("username") != null ? data.get("username").toString() : "";
				emailService.sendResetPasswordMail(body.getEmail(), username, tokenUuid);
			}
		}
		response.setStatus(ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getUserToken")
	public ResponseEntity<NormalizedResponse> getUserToken(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Boolean data = userService.checkUserToken(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getCampaignByDate")
	public ResponseEntity<NormalizedResponse> getCampaignByDate(
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "user", required = false) String user,
			@RequestParam(value = "role", required = false) String role,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = eventService.getCampaignByDate(type, user, role, paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getCampaignDetailById")
	public ResponseEntity<NormalizedResponse> getCampaignDetailById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = eventService.getCampaignDetailById(id);
		response.setData(data);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getEventDetailById")
	public ResponseEntity<NormalizedResponse> getEventDetailById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = eventService.getEventDetailById(id);
		response.setData(data);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getAllParticipantByEvent")
	public ResponseEntity<NormalizedResponse> getAllParticipantByEvent(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "paging", required = false) String pagingJson,
			@RequestParam(value = "eventName", required = false) String eventName,
			@RequestParam(value = "gender", required = false) String gender,
			@RequestParam(value = "ageGroup", required = false) String ageGroup,
			@RequestParam(value = "favorites", required = false) String favorites,
			@RequestParam(value = "type", required = false) String type)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = runnerService.getAllParticipantByEvent(id, paging, eventName, gender, ageGroup, favorites,
				type);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getAllParticipantByEventMoreDetail")
	public ResponseEntity<NormalizedResponse> getAllParticipantByEventMoreDetail(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "paging", required = false) String pagingJson,
			@RequestParam(value = "eventName", required = false) String eventName,
			@RequestParam(value = "gender", required = false) String gender,
			@RequestParam(value = "ageGroup", required = false) String ageGroup,
			@RequestParam(value = "favorites", required = false) String favorites,
			@RequestParam(value = "type", required = false) String type)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		Map<String, Object> data = runnerService.getAllParticipantByEventMoreDetail(id, paging, eventName, gender, ageGroup, favorites,
				type);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getAllStatusByEvent")
	public ResponseEntity<NormalizedResponse> getAllStatusByEvent(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getAllStatusByEvent(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getAllParticipantWithStationByEvent")
	public ResponseEntity<NormalizedResponse> getAllParticipantWithStationByEvent(
			@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id,
			@RequestParam(value = "paging", required = false) String pagingJson,
			@RequestParam(value = "gender", required = false) String gender,
			@RequestParam(value = "ageGroup", required = false) String ageGroup,
			@RequestParam(value = "type", required = false) String type)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		Map<String, Object> data = runnerService.getAllParticipantWithStationByEvent(id, paging, gender, ageGroup, type);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getStartersByAge")
	public ResponseEntity<NormalizedResponse> getStartersByAge(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getStartersByAge(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getWithdrawalByAge")
	public ResponseEntity<NormalizedResponse> getWithdrawalByAge(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getWithdrawalByAge(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getWithdrawalByCheckpoint")
	public ResponseEntity<NormalizedResponse> getWithdrawalByCheckpoint(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getWithdrawalByCheckpoint(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getFinishByTime")
	public ResponseEntity<NormalizedResponse> getFinishByTime(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getFinishByTime(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/raceTimestamp/getRaceTimestampByStation")
	public ResponseEntity<NormalizedResponse> getRaceTimestampByStation(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id,
			@RequestParam("campaignUuid") String campaignUuid,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException, UnsupportedEncodingException {
		NormalizedResponse response = new NormalizedResponse();

		String encodedHeader = URLEncoder.encode(campaignUuid + id, StandardCharsets.UTF_8.name());

		if (encodedHeader.equals(headers.get("authorizationstation"))) {

			PagingData paging = null;
			if (pagingJson != null) {
				paging = mapper.readValue(pagingJson, PagingData.class);
			}

			DatabaseData data = raceTimestampService.getRaceTimestampByStation(id, paging);
			response.setStatus(
					ResponseStatus.builder().code("200").description("success").build());
			response.setData(data);
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		response.setStatus(ResponseStatus.builder().code("404").description("Not Found").build());
		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
	}

	@GetMapping("/campaign/getCheckpointById")
	public ResponseEntity<NormalizedResponse> getCheckpointById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = eventService.getCheckpointById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getLatestParticipantByCheckpoint")
	public ResponseEntity<NormalizedResponse> getLatestParticipantByCheckpoint(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id,
			@RequestParam(value = "eventUuid", required = false) String eventUuid,
			@RequestParam(value = "paging", required = false) String pagingJson,
			@RequestParam(value = "checkpointName", required = false) String checkpointName,
			@RequestParam(value = "gender", required = false) String gender,
			@RequestParam(value = "ageGroup", required = false) String ageGroup)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = runnerService.getLatestParticipantByCheckpoint(id, eventUuid, paging, checkpointName, gender,
				ageGroup);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getCampaignById")
	public ResponseEntity<NormalizedResponse> getCampaignById(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = eventService.getCampaignById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/campaign/getParticipantByChipCode")
	public ResponseEntity<NormalizedResponse> getParticipantByChipCode(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id,
			@RequestParam(value = "chipCode", required = false) String chipCode,
			@RequestParam(value = "bibNo", required = false) String bibNo)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		List<Map<String, Object>> data = runnerService.getParticipantByChipCode(id, chipCode, bibNo);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/raceTimestamp/getParticipantBycampaign")
	public ResponseEntity<NormalizedResponse> getParticipantBycampaign(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id,
			@RequestParam("campaignUuid") String campaignUuid)
			throws SQLException, JsonProcessingException, UnsupportedEncodingException {
		NormalizedResponse response = new NormalizedResponse();

		String encodedHeader = URLEncoder.encode(campaignUuid + id, StandardCharsets.UTF_8.name());

		if (encodedHeader.equals(headers.get("authorizationstation"))) {

			List<Map<String, Object>> data = raceTimestampService.getParticipantBycampaign(campaignUuid);
			response.setStatus(
					ResponseStatus.builder().code("200").description("success").build());
			response.setData(data);
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		response.setStatus(ResponseStatus.builder().code("404").description("Not Found").build());
		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
	}

	@PostMapping("/raceTimestamp/createRaceTimestampWithQRCode")
	public ResponseEntity<NormalizedResponse> createRaceTimestampWithQRCode(@RequestHeader Map<String, String> headers,
			@RequestBody RaceTimestampRequest body) throws SQLException, UnsupportedEncodingException {
		NormalizedResponse response = new NormalizedResponse();

		String encodedHeader = URLEncoder.encode(body.getCampaignUuid() + body.getStationUuid(),
				StandardCharsets.UTF_8.name());

		if (encodedHeader.equals(headers.get("authorizationstation"))) {
			raceTimestampService.createRaceTimestampWithQRCode(body);
			response.setStatus(
					ResponseStatus.builder().code("200").description("success").build());
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
		response.setStatus(ResponseStatus.builder().code("404").description("Not Found").build());
		return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
	}

	@PostMapping("/user/updatePassword")
	public ResponseEntity<NormalizedResponse> updatePassword(@RequestHeader Map<String, String> headers,
			@RequestBody UserData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		body.setNpw(passwordEncoder.encode(body.getNpw()));
		userService.updateUser(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateUserToken")
	public ResponseEntity<NormalizedResponse> updateUserToken(@RequestHeader Map<String, String> headers,
			@RequestBody UserTokenData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Boolean data = userService.checkUserToken(body.getUuid());
		if (data) {
			body.setNpw(passwordEncoder.encode(body.getNpw()));
			userService.updateUserToken(body);
		}
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
