package racetimingms.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import racetimingms.constants.Constants;
import racetimingms.model.ResponseStatus;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.CoreService;


@RestController
@RequestMapping(path = Constants.ENDPOINT + "/master")
public class MasterDataController {

	@Autowired
    private CoreService coreService;

    @GetMapping("/getAllProvince")
    public ResponseEntity<NormalizedResponse> getAllProvince() throws SQLException {
        NormalizedResponse response = new NormalizedResponse();

        List<Map<String, Object>> data = coreService.getAllProvince();
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(data);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/getNationality")
    public ResponseEntity<NormalizedResponse> getNationality() throws SQLException {
        NormalizedResponse response = new NormalizedResponse();

        List<Map<String, Object>> data = coreService.getNationality();
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(data);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/getOrganizer")
    public ResponseEntity<NormalizedResponse> getOrganizer() throws SQLException {
        NormalizedResponse response = new NormalizedResponse();

        List<Map<String, Object>> data = coreService.getOrganizer();
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(data);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/getOtionData")
    public ResponseEntity<NormalizedResponse> getOtionData(@RequestHeader Map<String, String> headers,
    @RequestParam(value = "vData", required = false) String vData,
    @RequestParam(value = "tData", required = false) String tData
    ) throws SQLException {
        NormalizedResponse response = new NormalizedResponse();

        List<Map<String, Object>> data = coreService.getOtionData(vData, tData);
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(data);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/getOtionIdOtions")
    public ResponseEntity<NormalizedResponse> getOtionIdOtions(@RequestHeader Map<String, String> headers,
    @RequestParam(value = "vData", required = false) String vData,
    @RequestParam(value = "tData", required = false) String tData
    ) throws SQLException {
        NormalizedResponse response = new NormalizedResponse();

        List<Map<String, Object>> data = coreService.getOtionIdOtions(vData, tData);
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(data);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
