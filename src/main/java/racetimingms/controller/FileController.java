package racetimingms.controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import racetimingms.constants.Constants;
import racetimingms.model.Response;
import racetimingms.model.ResponseStatus;
import racetimingms.model.response.NormalizedResponse;
import racetimingms.service.AWSService;
import racetimingms.service.ExcelGeneratorService;
import racetimingms.service.RunnerService;

@RestController
@RequestMapping(path = Constants.ENDPOINT + "/file")
public class FileController {

    @Autowired
    private AWSService awsService;
    
	@Autowired
	private RunnerService runnerService;

	@Autowired
	private ExcelGeneratorService excelGeneratorService;

    @PostMapping(path = "/uploadFile")
    public ResponseEntity<NormalizedResponse> uploadFile(@RequestParam("prefix") String prefix,
            @RequestParam("file") MultipartFile multipartFile) throws IllegalStateException, IOException {
        NormalizedResponse response = new NormalizedResponse();

        awsService.setPrefix(prefix);
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

        File file = new File(System.getProperty("java.io.tmpdir"), fileName);
        multipartFile.transferTo(file);
        fileName = awsService.uploadFile(file);
        if (file.delete()) {
            response.setStatus(
                    ResponseStatus.builder().code("200").description("success").build());
            response.setData(fileName);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping(path = "/getPublicUrl")
    public ResponseEntity<NormalizedResponse> getPublicUrl(@RequestParam("prefix") String prefix,
            @RequestParam("key") String key) {
        NormalizedResponse response = new NormalizedResponse();
        awsService.setPrefix(prefix);
        String url = awsService.getSharedUrl(key);
        response.setStatus(
                ResponseStatus.builder().code("200").description("success").build());
        response.setData(url);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

	@GetMapping("/downloadTemplateParticipant")
	public ResponseEntity<byte[]> downloadTemplateParticipant(@RequestParam("id") String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
		byte[] body = null;

		try {
			String title = runnerService.getTitleParticipant(id);
			List<Map<String, Object>> dataList = runnerService.getMockListParticipantTemplate(id);
			body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

			String encodedTitle = UriUtils.encode(title, StandardCharsets.UTF_8);

			ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
					.filename("Participant_" + encodedTitle + ".xlsx").build();
			headers.setContentDisposition(contentDisposition);

		} catch (Exception ex) {
			headers.setContentType(MediaType.APPLICATION_JSON);
			Response response = Response.builder().isSuccess(false).data(ex.getMessage()).build();
			return new ResponseEntity<>(excelGeneratorService.buildResponseData(response).getBytes(), headers,
					HttpStatus.OK);
		}

		return new ResponseEntity<>(body, headers, HttpStatus.OK);
	}

	@GetMapping("/downloadParticipantsData")
	public ResponseEntity<byte[]> downloadParticipantsData(@RequestParam("id") String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
		byte[] body = null;

		try {
			Map<String, Object> titleData = runnerService.getTitleParticipantByEvent(id);

			List<Map<String, Object>> dataList = runnerService.getAllParticipantDownload(id);
			body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

			String encodedTitle = UriUtils.encode((String) titleData.get("name"), StandardCharsets.UTF_8);
			String encodedEventType = UriUtils.encode((String) titleData.get("eventType"), StandardCharsets.UTF_8);

			ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
					.filename("Participant_" + encodedTitle + "_"+ encodedEventType + ".xlsx").build();
			headers.setContentDisposition(contentDisposition);

		} catch (Exception ex) {
			headers.setContentType(MediaType.APPLICATION_JSON);
			Response response = Response.builder().isSuccess(false).data(ex.getMessage()).build();
			return new ResponseEntity<>(excelGeneratorService.buildResponseData(response).getBytes(), headers,
					HttpStatus.OK);
		}

		return new ResponseEntity<>(body, headers, HttpStatus.OK);
	}

	@GetMapping("/downloadParticipantsAndTimeData")
	public ResponseEntity<byte[]> downloadParticipantsAndTimeData(@RequestParam("id") String id) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setAccessControlExposeHeaders(ExcelGeneratorService.ACCESS_HEADERS);
		byte[] body = null;

		try {
			Map<String, Object> titleData = runnerService.getTitleParticipantByEvent(id);

			List<Map<String, Object>> dataList = runnerService.getAllParticipantAndTimeDownload(id);
			body = excelGeneratorService.generateCustomExcelForMultipleSheets(dataList);

			String encodedTitle = UriUtils.encode((String) titleData.get("name"), StandardCharsets.UTF_8);
			String encodedEventType = UriUtils.encode((String) titleData.get("eventType"), StandardCharsets.UTF_8);

			ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
					.filename("Participant_Time_" + encodedTitle + "_"+ encodedEventType + ".xlsx").build();
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
