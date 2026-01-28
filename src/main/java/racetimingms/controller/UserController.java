package racetimingms.controller;

import java.sql.SQLException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import racetimingms.model.ResponseStatus;
import racetimingms.model.UserData;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.UserService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/user")
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper mapper;

	@GetMapping("/getAllUser")
	public ResponseEntity<NormalizedResponse> getAllUser(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = userService.getAllUser(paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getAllUserRole")
	public ResponseEntity<NormalizedResponse> getAllUserRole(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "paging", required = false) String pagingJson)
			throws SQLException, JsonProcessingException {
		NormalizedResponse response = new NormalizedResponse();

		PagingData paging = null;
		if (pagingJson != null) {
			paging = mapper.readValue(pagingJson, PagingData.class);
		}
		DatabaseData data = userService.getAllUserRole(paging);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@GetMapping("/getUserById")
	public ResponseEntity<NormalizedResponse> getUserById(@RequestHeader Map<String, String> headers,
			@RequestParam(value = "id", required = false) String id) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		Map<String, Object> data = userService.getUserById(id);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		response.setData(data);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/createUser")
	public ResponseEntity<NormalizedResponse> createUser(@RequestHeader Map<String, String> headers,
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

	@PostMapping("/updateUser")
	public ResponseEntity<NormalizedResponse> updateUser(@RequestHeader Map<String, String> headers,
			@RequestBody UserData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		if (body.getOpw() != null && !Strings.isNullOrEmpty(body.getOpw())) {
			body.setNpw(passwordEncoder.encode(body.getNpw()));
			Map<String, Object> user = userService.findUserPasswordById(body.getUuid());
			String currentPassword = (String) user.get("password");

			if (this.passwordEncoder.matches(body.getOpw(), currentPassword)) {
				userService.updateUser(body);
				response.setStatus(
						ResponseStatus.builder().code("200").description("success").build());
				return new ResponseEntity<>(response, HttpStatus.OK);
			} else {
				response.setStatus(
						ResponseStatus.builder().code("10004").description("รหัสผ่านเดิมไม่ถูกต้อง").build());
				return new ResponseEntity<>(response, HttpStatus.OK);
			}

		} else {
			response.setStatus(
					ResponseStatus.builder().code("10003").description("โปรดระบุรหัสเดิม").build());
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
	}

	@PostMapping("/updateUserRole")
	public ResponseEntity<NormalizedResponse> updateUserRole(@RequestHeader Map<String, String> headers,
			@RequestBody UserData body) throws SQLException {
		NormalizedResponse response = new NormalizedResponse();

		userService.updateUserRole(body);
		response.setStatus(
				ResponseStatus.builder().code("200").description("success").build());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@DeleteMapping("/deleteUser")
	public ResponseEntity<NormalizedResponse> deleteUser(@RequestHeader Map<String, String> headers,
			@RequestParam("id") String id, @RequestParam("userId") String userId, @RequestParam("cpw") String cpw)
			throws SQLException {
		NormalizedResponse response = new NormalizedResponse();
		if (cpw != null) {
			Map<String, Object> user = userService.findUserPasswordById(userId);
			String userPassword = (String) user.get("password");
			if (this.passwordEncoder.matches(cpw, userPassword)) {
				userService.deleteUser(id);
				response.setStatus(
						ResponseStatus.builder().code("200").description("success").build());
				return new ResponseEntity<>(response, HttpStatus.OK);
			} else {
				response.setStatus(
						ResponseStatus.builder().code("10004").description("รหัสผ่านไม่ถูกต้อง").build());
				return new ResponseEntity<>(response, HttpStatus.OK);
			}
		} else {
			response.setStatus(
					ResponseStatus.builder().code("10003").description("โปรดระบุรหัสเดิม").build());
			return new ResponseEntity<>(response, HttpStatus.OK);
		}
	}
}
