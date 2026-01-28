package racetimingms.controller;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import racetimingms.constants.Constants;
import racetimingms.model.Response;
import racetimingms.service.AWSService;
import racetimingms.service.ExcelGeneratorService;
import racetimingms.service.ReportService;
import racetimingms.service.RunnerService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/public-api")
public class PublicDocumentController {

    @Autowired
    ReportService reportService;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    @Autowired
    private AWSService awsService;

    @Autowired
    private RunnerService runnerService;

    @Autowired
    private ExcelGeneratorService excelGeneratorService;

    private static final List<String> ACCESS_HEADERS = Collections
            .unmodifiableList(Arrays.asList("Content-Type", "Content-Disposition"));

    public Timestamp getCurrentTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
        return new Timestamp(calendar.getTimeInMillis());
    }

    public String optString(String value) {
        return Optional.ofNullable(value).orElse("");
    }

    private String replaceConstants(String sql) {
        sql = sql.replaceAll(":database", Constants.DATABASE);
        return sql;
    }

    @GetMapping("/doc/getCertificateReport")
    @Cacheable(value = "getCertificateReport", key = "#id", cacheManager = "reportCacheManager")
    public ResponseEntity<byte[]> getCertificateReport(@RequestHeader Map<String, String> headers,
            @RequestParam("id") String id) throws Exception {
        HttpHeaders resHeader = new HttpHeaders();
        resHeader.setContentType(MediaType.APPLICATION_PDF);
        resHeader.setAccessControlExposeHeaders(ACCESS_HEADERS);
        byte[] data = null;
        String template = "/Certificate.jrxml";
        ArrayList<Object> values = new ArrayList<>();
        values.add(id);
        String sql = """
                SELECT  CONCAT(a.firstName, IFNULL(CONCAT(' ', a.lastName), '')) AS runner,
                        et.name AS event, c.bgUrl AS bg, c.certTextColor, CAST(gt.gunTime AS CHAR) AS gunTime,
                        CAST(CASE ct.type WHEN 'rfid' THEN ct.chipTime ELSE gt.gunTime END AS CHAR) AS chipTime
                FROM :database.participant a
                INNER JOIN :database.event et ON et.id = a.eventId
                INNER JOIN :database.campaign c ON c.id = et.campaignId
                LEFT JOIN :database.calculateGunTime gt ON gt.participantId = a.id AND gt.campaignId = c.id AND gt.eventId = et.id
                LEFT JOIN :database.calculateChipTime ct ON ct.participantId = a.id AND ct.eventId = et.id
                WHERE a.uuid = ?
                """;

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), values.toArray());
            Map<String, Object> paramMap = new HashMap<>();

            String bgValue = (String) result.get("bg");

            if (bgValue == null || bgValue.isEmpty()) {
                paramMap.put("bg", optString(bgValue));
            } else {
                String prefixEvent = "campaign";
                awsService.setPrefix(prefixEvent);
                String urlEvent = awsService.getSharedUrl(bgValue);
                paramMap.put("bg", optString(urlEvent));
            }

            String certTextColor = (String) result.get("certTextColor");
            paramMap.put("certTextColor", (certTextColor == null || certTextColor.trim().isEmpty()) ? "Dark" : optString(certTextColor));

            paramMap.put("runner", optString((String) result.get("runner")));
            paramMap.put("bibNo", optString((String) result.get("bibNo")));
            paramMap.put("event", optString((String) result.get("event")));
            paramMap.put("gunTime", optString((String) result.get("gunTime")));
            paramMap.put("chipTime", optString((String) result.get("chipTime")));
            paramMap.put("participantUuid", optString(id));

            data = reportService.generateReportImageQuery(template, paramMap);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Certificate-" + getCurrentTime().getTime() + ".jpg").build();
            resHeader.setContentDisposition(contentDisposition);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(data, resHeader, HttpStatus.OK);
    }

    @GetMapping("/doc/getEslipReport")
    @Cacheable(value = "getEslipReport", key = "#id", cacheManager = "reportCacheManager")
    public ResponseEntity<byte[]> getEslipReport(@RequestHeader Map<String, String> headers,
            @RequestParam("id") String id) throws Exception {
        HttpHeaders resHeader = new HttpHeaders();
        resHeader.setContentType(MediaType.APPLICATION_PDF);
        resHeader.setAccessControlExposeHeaders(ACCESS_HEADERS);
        byte[] data = null;
        String template = "/Eslip.jrxml";
        ArrayList<Object> values = new ArrayList<>();
        values.add(id);
        String sql = """
                SELECT b.logoUrl AS logo
                FROM :database.participant a
                INNER JOIN :database.event et ON et.id = a.eventId
                INNER JOIN :database.campaign b ON b.id = et.campaignId
                WHERE a.uuid = ?
                """;

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(replaceConstants(sql), values.toArray());
            Map<String, Object> paramMap = new HashMap<>();
            String logoValue = (String) result.get("logo");
            if (logoValue == null || logoValue.isEmpty()) {
                paramMap.put("logo", optString(logoValue));
            } else {
                String prefixEvent = "campaign";
                awsService.setPrefix(prefixEvent);
                String urlEvent = awsService.getSharedUrl(logoValue);
                paramMap.put("logo", optString(urlEvent));
            }
            paramMap.put("participantUuid", optString(id));

            data = reportService.generateReportImageQuery(template, paramMap);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Eslip-" + getCurrentTime().getTime() + ".jpg").build();
            resHeader.setContentDisposition(contentDisposition);

        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(data, resHeader, HttpStatus.OK);
    }

    @GetMapping("/doc/getResultReport")
    public ResponseEntity<byte[]> getResultReport(@RequestHeader Map<String, String> headers,
            @RequestParam("id") String id,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "ageGroup", required = false) String ageGroup,
            @RequestParam(value = "topRank", required = false) String topRank,
            @RequestParam(value = "type", required = false) String type) throws Exception {
        HttpHeaders resHeader = new HttpHeaders();
        resHeader.setContentType(MediaType.APPLICATION_PDF);
        resHeader.setAccessControlExposeHeaders(ACCESS_HEADERS);
        byte[] data = null;
        String template = "/ranking_report.jrxml";
        try {
            Map<String, Object> paramMap = new HashMap<>();

            paramMap.put("id", id);

            if (type != null && !type.isEmpty()) {
                paramMap.put("type", type);
            }
            if (gender != null && !gender.isEmpty()) {
                paramMap.put("gender", gender);
            }
            if (ageGroup != null && !ageGroup.isEmpty()) {
                paramMap.put("ageGroup", ageGroup);
            }
            if (topRank != null && !topRank.isEmpty()) {
                paramMap.put("topRank", topRank);
            }

            data = reportService.generateReportQuery(template, paramMap);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Result-" + getCurrentTime().getTime() + ".pdf").build();
            resHeader.setContentDisposition(contentDisposition);

        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(data, resHeader, HttpStatus.OK);
    }

    @GetMapping("/doc/getResultExcel")
    public ResponseEntity<byte[]> getResultExcel(
            @RequestParam("id") String id,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "ageGroup", required = false) String ageGroup,
            @RequestParam(value = "topRank", required = false) String topRank,
            @RequestParam(value = "type", required = false) String type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
        byte[] body = null;

        try {
            Map<String, Object> titleData = runnerService.getTitleParticipantByEvent(id);

            List<Map<String, Object>> dataList = runnerService.getResultExcel(id, gender, ageGroup, topRank, type);
            body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

            String encodedTitle = UriUtils.encode((String) titleData.get("name"), StandardCharsets.UTF_8);
            String encodedEventType = UriUtils.encode((String) titleData.get("eventType"), StandardCharsets.UTF_8);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Result_Participants_" + encodedTitle + "_" + encodedEventType + ".xlsx").build();
            headers.setContentDisposition(contentDisposition);

        } catch (Exception ex) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            Response response = Response.builder().isSuccess(false).data(ex.getMessage()).build();
            return new ResponseEntity<>(excelGeneratorService.buildResponseData(response).getBytes(), headers,
                    HttpStatus.OK);
        }

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/doc/getResultLiveExcel")
    public ResponseEntity<byte[]> getResultLiveExcel(
            @RequestParam("id") String id,
			@RequestParam(value = "gender", required = false) String gender,
			@RequestParam(value = "ageGroup", required = false) String ageGroup,
            @RequestParam(value = "type", required = false) String type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
        byte[] body = null;

        try {
            String titleData = runnerService.getTitleParticipant(id);

            List<Map<String, Object>> dataList = runnerService.getResultLiveExcel(id, gender, ageGroup, type);
            body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

            String encodedTitle = UriUtils.encode(titleData, StandardCharsets.UTF_8);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Result_Live_Participants_" + encodedTitle + ".xlsx").build();
            headers.setContentDisposition(contentDisposition);

        } catch (Exception ex) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            Response response = Response.builder().isSuccess(false).data(ex.getMessage()).build();
            return new ResponseEntity<>(excelGeneratorService.buildResponseData(response).getBytes(), headers,
                    HttpStatus.OK);
        }

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/doc/getResultMoreDetailExcel")
    public ResponseEntity<byte[]> getResultMoreDetailExcel(
            @RequestParam("id") String id,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "ageGroup", required = false) String ageGroup,
            @RequestParam(value = "topRank", required = false) String topRank,
            @RequestParam(value = "type", required = false) String type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
        byte[] body = null;

        try {
            Map<String, Object> titleData = runnerService.getTitleParticipantByEvent(id);

            List<Map<String, Object>> dataList = runnerService.getResultMoreDetailExcel(id, gender, ageGroup, topRank, type);
            body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

            String encodedTitle = UriUtils.encode((String) titleData.get("name"), StandardCharsets.UTF_8);
            String encodedEventType = UriUtils.encode((String) titleData.get("eventType"), StandardCharsets.UTF_8);

            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename("Result_Participants_Detail_" + encodedTitle + "_" + encodedEventType + ".xlsx").build();
            headers.setContentDisposition(contentDisposition);

        } catch (Exception ex) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            Response response = Response.builder().isSuccess(false).data(ex.getMessage()).build();
            return new ResponseEntity<>(excelGeneratorService.buildResponseData(response).getBytes(), headers,
                    HttpStatus.OK);
        }

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
