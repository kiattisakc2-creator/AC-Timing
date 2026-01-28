package racetimingms.controller;

import java.io.File;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import racetimingms.constants.Constants;
import racetimingms.service.ReportService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/doc")
public class DocumentController {

    @Autowired
    ReportService reportService;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    private static final List<String> ACCESS_HEADERS = Collections
            .unmodifiableList(Arrays.asList("Content-Type", "Content-Disposition"));

    public Timestamp getCurrentTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"));
        return new Timestamp(calendar.getTimeInMillis());
    }

    public String optString(String value) {
        return Optional.ofNullable(value).orElse("");
    }

    @PostMapping("/getCertificateTemplateReport")
    public ResponseEntity<byte[]> getCertificateTemplateReport(
            @RequestHeader Map<String, String> headers,
            @RequestParam(value = "certTextColor", required = false) String certTextColor,
            @RequestParam(value = "bg", required = false) String bg,
            @RequestPart(value = "file", required = false) MultipartFile multipartFile)
            throws IllegalStateException, Exception {

        HttpHeaders resHeader = new HttpHeaders();
        resHeader.setContentType(MediaType.APPLICATION_PDF);
        resHeader.setAccessControlExposeHeaders(ACCESS_HEADERS);
        byte[] data = null;
        String template = "/CertificateTemplate.jrxml";

        String filePath = bg;
        File file = null;
        if (multipartFile != null && !multipartFile.isEmpty()) {
            String fileName = Optional.ofNullable(multipartFile.getOriginalFilename()).orElse("");
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());

            int index = fileName.lastIndexOf("/");
            String extractedFileName = fileName;
            if (index > 0) {
                extractedFileName = fileName.substring(index + 1);
            }

            index = extractedFileName.lastIndexOf(".");
            if (index > 0) {
                String extension = extractedFileName.substring(index + 1);
                String name = extractedFileName.substring(0, index);
                fileName = name + "-" + timestamp.getTime() + "." + extension;
            } else {
                fileName += "-" + timestamp.getTime();
            }

            file = new File(System.getProperty("java.io.tmpdir"), fileName);
            multipartFile.transferTo(file);
            filePath = file.getAbsolutePath();
        }
        if (certTextColor == null || certTextColor.trim().isEmpty()) {
            certTextColor = "Dark";
        }
        try {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("certTextColor", optString(certTextColor));
            paramMap.put("bg", optString(filePath));

            data = reportService.generateReportImage(template, paramMap);

            if (file != null && file.delete()) {
                ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                        .filename("CertificateTemplate-" + getCurrentTime().getTime() + ".jpg").build();
                resHeader.setContentDisposition(contentDisposition);
            }
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(data, resHeader, HttpStatus.OK);
    }
}
