package racetimingms.service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import racetimingms.model.Response;

@Service
public class ExcelGeneratorService {
	Logger logger = LoggerFactory.getLogger(ExcelGeneratorService.class);
	private static final String GENERATE_EXCEL_ERROR = "Can't generate excel";
	public static final List<String> ACCESS_HEADERS = Collections
			.unmodifiableList(Arrays.asList("Content-Type", "Content-Disposition"));

	public byte[] generateExcelForMultipleSheets(List<Map<String, Object>> dataSets) {
		ByteArrayOutputStream excelOutputStream = new ByteArrayOutputStream();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			for (Map<String, Object> data : dataSets) {
				XSSFSheet sheet = workbook.createSheet("" + data.get("sheetName"));
				CreationHelper createHelper = workbook.getCreationHelper();
				Row row = sheet.createRow(0);
				CellStyle myStyle = workbook.createCellStyle();
				myStyle.setAlignment(HorizontalAlignment.CENTER);
				myStyle.setVerticalAlignment(VerticalAlignment.CENTER);
				myStyle.setBorderBottom(BorderStyle.MEDIUM);
				myStyle.setBorderLeft(BorderStyle.MEDIUM);
				myStyle.setBorderRight(BorderStyle.MEDIUM);
				myStyle.setBorderTop(BorderStyle.MEDIUM);
				myStyle.setWrapText(true);
				Integer count = 0;
				Cell cell = row.createCell(count++);
				for (String title : (String[]) data.get("columns")) {
					cell.setCellStyle(myStyle);
					cell.setCellValue(createHelper.createRichTextString(title));
					cell = row.createCell(count++);
				}
				CellStyle dataStyle = workbook.createCellStyle();
				dataStyle.setBorderBottom(BorderStyle.THIN);
				dataStyle.setBorderLeft(BorderStyle.THIN);
				dataStyle.setBorderRight(BorderStyle.THIN);
				dataStyle.setBorderTop(BorderStyle.THIN);
				Integer index = 1;
				for (List<Object> entries : (List<List<Object>>) data.get("datas")) {
					Integer cnt = 0;
					row = sheet.createRow(index);
					Cell dataCell = row.createCell(cnt++);
					for (Object entry : entries) {
						dataCell.setCellValue(
								createHelper.createRichTextString(entry != null ? String.valueOf(entry) : null));
						dataCell.setCellStyle(dataStyle);
						dataCell = row.createCell(cnt++);
					}
					index++;
				}
				for (int i = 0; i < count; i++) {
					sheet.autoSizeColumn(i);
				}
			}
			workbook.write(excelOutputStream);
		} catch (Exception ex) {
			logger.error(GENERATE_EXCEL_ERROR, ex);
		}
		return excelOutputStream.toByteArray();
	}

	public byte[] generateCustomExcelForMultipleSheets(List<Map<String, Object>> dataSets) {
		ByteArrayOutputStream excelOutputStream = new ByteArrayOutputStream();
		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			for (Map<String, Object> data : dataSets) {
				XSSFSheet sheet = workbook.createSheet("" + data.get("sheetName"));
				CreationHelper createHelper = workbook.getCreationHelper();
				CellStyle myStyle = workbook.createCellStyle();
				myStyle.setAlignment(HorizontalAlignment.CENTER);
				myStyle.setVerticalAlignment(VerticalAlignment.CENTER);
				myStyle.setBorderBottom(BorderStyle.MEDIUM);
				myStyle.setBorderLeft(BorderStyle.MEDIUM);
				myStyle.setBorderRight(BorderStyle.MEDIUM);
				myStyle.setBorderTop(BorderStyle.MEDIUM);
				myStyle.setWrapText(true);
				Integer cellIndex = 0;
				Integer rowIndex = 0;
				Row row = sheet.createRow(rowIndex++);
				Cell cell = row.createCell(cellIndex++);
				if (data.get("preHeader") != null) {
					for (String title : (List<String>) data.get("preHeader")) {
						cell.setCellStyle(myStyle);
						cell.setCellValue(createHelper.createRichTextString(title));
						cell = row.createCell(cellIndex++);
					}
					row = sheet.createRow(rowIndex++);
				}

				cellIndex = 0;
				cell = row.createCell(cellIndex++);
				for (String title : (String[]) data.get("columns")) {
					cell.setCellStyle(myStyle);
					cell.setCellValue(createHelper.createRichTextString(title));
					cell = row.createCell(cellIndex++);
				}

				CellStyle dataStyle = workbook.createCellStyle();
				dataStyle.setBorderBottom(BorderStyle.THIN);
				dataStyle.setBorderLeft(BorderStyle.THIN);
				dataStyle.setBorderRight(BorderStyle.THIN);
				dataStyle.setBorderTop(BorderStyle.THIN);
				for (List<Object> entries : (List<List<Object>>) data.get("datas")) {
					Integer cnt = 0;
					row = sheet.createRow(rowIndex++);
					Cell dataCell = row.createCell(cnt++);
					for (Object entry : entries) {
						dataCell.setCellValue(
								createHelper.createRichTextString(entry != null ? String.valueOf(entry) : null));
						dataCell.setCellStyle(dataStyle);
						dataCell = row.createCell(cnt++);
					}
				}
				for (int i = 0; i < cellIndex; i++) {
					sheet.autoSizeColumn(i);
				}
			}
			workbook.write(excelOutputStream);
		} catch (Exception ex) {
			logger.error(GENERATE_EXCEL_ERROR, ex);
		}
		return excelOutputStream.toByteArray();
	}

	public String buildResponseData(Response response) {
		JSONObject formatted = new JSONObject();
		formatted.put("isSuccess", response.getIsSuccess() == null ? JSONObject.NULL : response.getIsSuccess());
		formatted.put("data", response.getData() == null ? JSONObject.NULL : response.getData());
		return formatted.toString();
	}
}
