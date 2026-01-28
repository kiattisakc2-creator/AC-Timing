package racetimingms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.Optional;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<String> handleSQLException(SQLException ex) {
        String userFriendlyMessage = errorHandler(ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"description\": \"" + userFriendlyMessage + "\"}");
    }

    public String errorHandler(String reason) {
        String newReason = "";
        if (reason != null && !reason.isEmpty()) {
            if (reason.contains("Duplicate entry")) {
                newReason = "ข้อมูลซ้ำกับในระบบ";
            } else if (reason.contains("Incorrect result size: expected 1, actual 0")) {
                newReason = "ไม่พบข้อมูล";
            } else if (reason.contains("Unknown column")) {
                newReason = "ไม่พบฟิลด์ที่ดึงข้อมูล";
            } else {
                newReason = "ระบบขัดข้อง";
            }
        }

        log.error(Optional.ofNullable(reason).orElse("") + " | " + newReason);
        return newReason;
    }
}
