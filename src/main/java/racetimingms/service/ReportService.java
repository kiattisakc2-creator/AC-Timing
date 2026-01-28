package racetimingms.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRGraphics2DExporter;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleGraphics2DExporterOutput;
import net.sf.jasperreports.export.SimpleGraphics2DReportConfiguration;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfReportConfiguration;

import racetimingms.constants.Constants;

@Slf4j
@Service
public class ReportService {
    
    @Autowired
    @Qualifier("mainJdbcTemplate")
    JdbcTemplate jdbcTemplate;

    private static final String GENERATE_REPORT_ERROR = "Can't generate report";
    
    public byte[] generateReport(String template, Map<String, Object> paramMap) throws Exception {
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        InputStream input = null;
        try {
            input = getClass().getResourceAsStream(Constants.REPORT_PATH + template);
            JasperReport jasReport = JasperCompileManager.compileReport(input);
            JasperPrint jasPrint = JasperFillManager.fillReport(jasReport, paramMap, new JREmptyDataSource());

            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(pdfOutputStream));

            SimplePdfReportConfiguration reportConfig = new SimplePdfReportConfiguration();
            reportConfig.setSizePageToContent(true);
            reportConfig.setForceLineBreakPolicy(false);

            exporter.exportReport();
        } catch (Exception ex) {
            log.error(GENERATE_REPORT_ERROR, ex);
            throw new Exception(ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
        }
        return pdfOutputStream.toByteArray();
    }

    public byte[] generateReportQuery(String template, Map<String, Object> paramMap) throws Exception {
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        Connection connection = null;
        InputStream input = null;

        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            input = getClass().getResourceAsStream(Constants.REPORT_PATH + template);
            JasperReport jasReport = JasperCompileManager.compileReport(input);
            JasperPrint jasPrint = JasperFillManager.fillReport(jasReport, paramMap, connection);

            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(pdfOutputStream));

            SimplePdfReportConfiguration reportConfig = new SimplePdfReportConfiguration();
            reportConfig.setSizePageToContent(true);
            reportConfig.setForceLineBreakPolicy(false);
            exporter.exportReport();
        } catch (Exception ex) {
            log.error(GENERATE_REPORT_ERROR, ex);
            throw new Exception(ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
        return pdfOutputStream.toByteArray();
    }
    
    public byte[] generateReportQuery(String template, String subTemplate, Map<String, Object> paramMap) throws Exception {
        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        Connection connection = null;
        InputStream input = null;
        InputStream input2 = null;

        try {
            connection = jdbcTemplate.getDataSource().getConnection();
            input = getClass().getResourceAsStream(Constants.REPORT_PATH + template);
            JasperReport jasReport = JasperCompileManager.compileReport(input);

            input2 = getClass().getResourceAsStream(Constants.REPORT_PATH + subTemplate);
            JasperReport jasperSubReport = JasperCompileManager.compileReport(input2);
            paramMap.put("subreportParameter", jasperSubReport);

            JasperPrint jasPrint = JasperFillManager.fillReport(jasReport, paramMap, connection);

            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasPrint));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(pdfOutputStream));

            SimplePdfReportConfiguration reportConfig = new SimplePdfReportConfiguration();
            reportConfig.setSizePageToContent(true);
            reportConfig.setForceLineBreakPolicy(false);
            exporter.exportReport();
        } catch (Exception ex) {
            log.error(GENERATE_REPORT_ERROR, ex);
            throw new Exception(ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
            if (input2 != null) {
                try {
                    input2.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
        return pdfOutputStream.toByteArray();
    }
    
    public byte[] generateReportImage(String template, Map<String, Object> paramMap) throws Exception {
        InputStream input = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()){
            input = getClass().getResourceAsStream(Constants.REPORT_PATH + template);
            JasperReport jasReport = JasperCompileManager.compileReport(input);
            JasperPrint jasPrint = JasperFillManager.fillReport(jasReport, paramMap, new JREmptyDataSource());
    
            int pageWidth = jasPrint.getPageWidth();
            int pageHeight = jasPrint.getPageHeight();
    
            BufferedImage bufferedImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = bufferedImage.createGraphics();
            SimpleGraphics2DExporterOutput dataExport = new SimpleGraphics2DExporterOutput();
            dataExport.setGraphics2D(graphics);
            JRGraphics2DExporter exporter = new JRGraphics2DExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasPrint));
            exporter.setExporterOutput(dataExport);
    
            SimpleGraphics2DReportConfiguration reportConfig = new SimpleGraphics2DReportConfiguration();
            exporter.setConfiguration(reportConfig);
    
            exporter.exportReport();
    
            ImageIO.write(bufferedImage, "png", outputStream);
            graphics.dispose();
    
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error(GENERATE_REPORT_ERROR, ex);
            throw new Exception(ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
        }
    }

    public byte[] generateReportImageQuery(String template, Map<String, Object> paramMap) throws Exception {
        Connection connection = null;
        InputStream input = null;
    
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            connection = jdbcTemplate.getDataSource().getConnection();
            input = getClass().getResourceAsStream(Constants.REPORT_PATH + template);
            JasperReport jasReport = JasperCompileManager.compileReport(input);
            JasperPrint jasPrint = JasperFillManager.fillReport(jasReport, paramMap, connection);
    
            int pageWidth = jasPrint.getPageWidth();
            int pageHeight = jasPrint.getPageHeight();
    
            BufferedImage bufferedImage = new BufferedImage(pageWidth, pageHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = bufferedImage.createGraphics();
            SimpleGraphics2DExporterOutput dataExport = new SimpleGraphics2DExporterOutput();
            dataExport.setGraphics2D(graphics);
            JRGraphics2DExporter exporter = new JRGraphics2DExporter();
            exporter.setExporterInput(new SimpleExporterInput(jasPrint));
            exporter.setExporterOutput(dataExport);
    
            SimpleGraphics2DReportConfiguration reportConfig = new SimpleGraphics2DReportConfiguration();
            exporter.setConfiguration(reportConfig);
    
            exporter.exportReport();
    
            ImageIO.write(bufferedImage, "png", outputStream);
            graphics.dispose();
    
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.error(GENERATE_REPORT_ERROR, ex);
            throw new Exception(ex);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ex) {
                    log.error(GENERATE_REPORT_ERROR, ex);
                }
            }
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
    }
}
