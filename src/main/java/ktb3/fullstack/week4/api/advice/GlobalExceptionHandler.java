package ktb3.fullstack.week4.api.advice;

import ktb3.fullstack.week4.common.error.codes.ErrorCode;
import ktb3.fullstack.week4.common.error.codes.GenericError;
import ktb3.fullstack.week4.common.error.exception.ApiException;
import ktb3.fullstack.week4.dto.common.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
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

    // 동시 요청이 중복 검사를 함께 통과해 DB 제약에서 걸린 경우의 안전망 → 409
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation() {
        return ResponseEntity.
                status(GenericError.CONFLICT.getStatus()).
                body(ApiResponse.error(GenericError.CONFLICT.getMessage()));
    }

    // 예기치 못한 오류 500 으로 일반화
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected() {
        return ResponseEntity.
                status(GenericError.INTERNAL_SERVER_ERROR.getStatus()).
                body(ApiResponse.error(GenericError.INTERNAL_SERVER_ERROR.getMessage()));
    }

}
