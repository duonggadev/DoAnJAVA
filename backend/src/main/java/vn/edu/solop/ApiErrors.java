package vn.edu.solop;

import java.util.Map;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> database(DataAccessException error) {
        Throwable cause = error.getMostSpecificCause();
        if(error instanceof org.springframework.dao.ConcurrencyFailureException || (cause instanceof SQLException concurrent && "40001".equals(concurrent.getSQLState())))
            return ResponseEntity.status(409).body(Map.of("message","Dữ liệu đang được cập nhật đồng thời. Hãy tải lại và thử lại thao tác."));
        if (cause instanceof SQLException sql && ((sql.getErrorCode() & 0xff) == 19
                || (sql.getSQLState() != null && sql.getSQLState().startsWith("23")))) {
            String message = sql.getMessage().toLowerCase(java.util.Locale.ROOT).contains("unique") ? "Mã hoặc email đã được sử dụng."
                    : "Dữ liệu đang được sử dụng hoặc vi phạm ràng buộc.";
            return ResponseEntity.status(409).body(Map.of("message", message));
        }
        throw error;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> oversized() {
        return ResponseEntity.status(413).body(Map.of("message","Tệp tải lên vượt quá giới hạn 2 MB."));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<Map<String,String>> malformed(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("message","Dữ liệu gửi lên thiếu hoặc không đúng định dạng."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("message", error.getReason()));
    }
}
