package app.hantteok.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class Errors {
    private static final Logger log = LoggerFactory.getLogger(Errors.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e, HttpServletRequest r) {
        return response(e.status, e.code, e.getMessage(), r);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<?> invalid(Exception e, HttpServletRequest r) {
        String message =
                e instanceof MethodArgumentNotValidException v
                        ? v.getBindingResult().getFieldErrors().stream()
                                .map(x -> x.getField() + ": " + x.getDefaultMessage())
                                .findFirst()
                                .orElse("입력값을 확인해 주세요.")
                        : "요청 형식과 입력값을 확인해 주세요.";
        return response(400, "INVALID_REQUEST", message, r);
    }

    @ExceptionHandler({CannotAcquireLockException.class, DuplicateKeyException.class})
    ResponseEntity<?> conflict(Exception e, HttpServletRequest r) {
        return response(409, "CONCURRENT_UPDATE", "동시에 처리 중인 요청이 있습니다. 상태를 확인하고 다시 시도해 주세요.", r);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing(Exception e, HttpServletRequest r) {
        return response(404, "NOT_FOUND", "경로를 찾을 수 없습니다.", r);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e, HttpServletRequest r) {
        return response(405, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다.", r);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> other(Exception e, HttpServletRequest r) {
        log.error("Request failed: {} {}", r.getMethod(), r.getRequestURI(), e);
        return response(500, "INTERNAL_ERROR", "서버 처리 중 오류가 발생했습니다.", r);
    }

    private ResponseEntity<?> response(
            int status, String code, String message, HttpServletRequest r) {
        return ResponseEntity.status(status)
                .body(
                        Map.of(
                                "code",
                                code,
                                "message",
                                message,
                                "path",
                                r.getRequestURI(),
                                "timestamp",
                                Instant.now()));
    }
}
