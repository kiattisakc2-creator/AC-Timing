package racetimingms.controller;

import java.sql.SQLException;
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
import racetimingms.model.RoleData;
import racetimingms.model.DatabaseData;
import racetimingms.model.PagingData;
import racetimingms.model.ResponseStatus;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.RoleService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/role")
public class RoleController {

        @Autowired
        private RoleService roleService;

        @Autowired
        private ObjectMapper mapper;

        @GetMapping("/getAllRole")
        public ResponseEntity<NormalizedResponse> getAllRole(@RequestHeader Map<String, String> headers,
                        @RequestParam(value = "paging", required = false) String pagingJson)
                        throws SQLException, JsonProcessingException {
                NormalizedResponse response = new NormalizedResponse();

                PagingData paging = null;
                if (pagingJson != null) {
                        paging = mapper.readValue(pagingJson, PagingData.class);
                }
                DatabaseData data = roleService.getAllRole(paging);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                response.setData(data);
                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @GetMapping("/getRoleById")
        public ResponseEntity<NormalizedResponse> getRoleById(@RequestHeader Map<String, String> headers,
                        @RequestParam(value = "id", required = false) String id) throws SQLException {
                NormalizedResponse response = new NormalizedResponse();

                Map<String, Object> data = roleService.getRoleById(id);
                response.setData(data);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                response.setData(data);
                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @PostMapping("/createRole")
        public ResponseEntity<NormalizedResponse> createRole(@RequestHeader Map<String, String> headers,
                        @RequestBody RoleData body) throws SQLException {
                NormalizedResponse response = new NormalizedResponse();

                roleService.createRole(body);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @PostMapping("/updateRole")
        public ResponseEntity<NormalizedResponse> updateRole(@RequestHeader Map<String, String> headers,
                        @RequestBody RoleData body) throws SQLException {
                NormalizedResponse response = new NormalizedResponse();

                roleService.updateRole(body);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @PostMapping("/updatePermission")
        public ResponseEntity<NormalizedResponse> updatePermission(@RequestHeader Map<String, String> headers,
                        @RequestBody RoleData body) throws SQLException {
                NormalizedResponse response = new NormalizedResponse();

                roleService.updatePermission(body);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @DeleteMapping("/deleteRole")
        public ResponseEntity<NormalizedResponse> deleteRole(@RequestHeader Map<String, String> headers,
                        @RequestParam("id") String id) throws SQLException {
                NormalizedResponse response = new NormalizedResponse();

                roleService.deleteRole(id);
                response.setStatus(
                                ResponseStatus.builder().code("200").description("success").build());
                return new ResponseEntity<>(response, HttpStatus.OK);
        }
}
