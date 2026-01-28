package racetimingms.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import racetimingms.constants.Constants;
import racetimingms.model.ResponseStatus;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.SyncService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.sql.SQLException;
import java.util.List;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @GetMapping("/last-sync-error")
    public ResponseEntity<NormalizedResponse> wasLastSyncError(@RequestHeader Map<String, String> headers,
            @RequestParam(value = "campaignId", required = false) Integer campaignId) {
        NormalizedResponse response = new NormalizedResponse();
        boolean isError = syncService.wasLastSyncError(campaignId);

        response.setStatus(ResponseStatus.builder().code("200").description("success").build());
        response.setData(isError);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/all-campaign-sync-errors")
    public ResponseEntity<NormalizedResponse> getAllCampaignSyncErrors(@RequestHeader Map<String, String> headers)
            throws SQLException {
        NormalizedResponse response = new NormalizedResponse();
        List<Map<String, Object>> campaignErrors = syncService.getAllCampaignSyncErrors();

        response.setStatus(ResponseStatus.builder().code("200").description("success").build());
        response.setData(campaignErrors);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/sync-data")
    public ResponseEntity<NormalizedResponse> getSyncData(@RequestHeader Map<String, String> headers,
            @RequestParam("id") String id) throws SQLException {
        NormalizedResponse response = new NormalizedResponse();
        Map<String, Object> syncData = syncService.getSyncData(id);

        response.setStatus(ResponseStatus.builder().code("200").description("success").build());
        response.setData(syncData);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
