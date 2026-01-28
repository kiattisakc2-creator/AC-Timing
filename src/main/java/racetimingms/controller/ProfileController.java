package racetimingms.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import racetimingms.constants.Constants;
import racetimingms.model.ResponseStatus;
import racetimingms.model.ProfileData;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.ProfileService;
import racetimingms.service.CoreService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/profile")
public class ProfileController {
    
	@Autowired
	private ProfileService profileService;
    
	@Autowired
	private CoreService coreService;

	@GetMapping("/getProfileById")
	public ResponseEntity<NormalizedResponse> getProfileById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = profileService.getProfileById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/updateProfile")
	public ResponseEntity<NormalizedResponse> updateProfile(@RequestHeader Map<String, String> headers,
			@RequestBody ProfileData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		if (body.getPrefixPath() != null) {
			String fileName = coreService.checkSameFile(body.getPictureUrl(), body.getUuid(), "user",  "prefixPath", "pictureUrl");
			if (fileName != null && !(fileName.equals(body.getPictureUrl()))) {
				body.setPictureUrl(fileName);
			}
		}

		profileService.updateProfile(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
