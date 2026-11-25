package ktb3.fullstack.week4.api.advice;

import ktb3.fullstack.week4.common.error.codes.ErrorCode;
import ktb3.fullstack.week4.common.error.codes.GenericError;
import ktb3.fullstack.week4.common.error.exception.ApiException;
import ktb3.fullstack.week4.dto.common.ApiResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    // 정의된 오류 처리
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(ApiException e) {
        ErrorCode code = e.getCode();
        return ResponseEntity.
                status(code.getStatus()).
                body(ApiResponse.error(code.getMessage()));
    }

    // RequestBody의 dto가 @Valid 어노테이션을 통과하지 못한 요청은 400 으로 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidAnnotationError() {
        return ResponseEntity.
                status(GenericError.INVALID_REQUEST.getStatus()).
                body(ApiResponse.error(GenericError.INVALID_REQUEST.getMessage()));
    }

    // 경로는 있지만 메서드가 다른 요청 → 405 (catch-all이 500으로 바꾸지 않게), Allow 헤더에 허용 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.
                status(GenericError.METHOD_NOT_ALLOWED.getStatus()).
                headers(e.getHeaders()).
                body(ApiResponse.error(GenericError.METHOD_NOT_ALLOWED.getMessage()));
    }

    // 동시 요청이 중복 검사를 함께 통과해 UNIQUE 제약에서 걸린 경우의 안전망 → 409
    // 같은 예외로 번역되는 다른 위반(값 길이 초과 등)은 500으로 둔다
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cve
                && cve.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
            return ResponseEntity.
                    status(GenericError.CONFLICT.getStatus()).
                    body(ApiResponse.error(GenericError.CONFLICT.getMessage()));
        }
        return handleUnexpected();
    }

    // 예기치 못한 오류 500 으로 일반화
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected() {
        return ResponseEntity.
                status(GenericError.INTERNAL_SERVER_ERROR.getStatus()).
                body(ApiResponse.error(GenericError.INTERNAL_SERVER_ERROR.getMessage()));
    }

}
