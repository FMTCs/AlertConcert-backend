package fcmt.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// 비즈니스 로직 관련 에러 처리 handler
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
		log.error("Business Error: Code[{}], Message[{}]", e.getErrorCode().getCode(), e.getMessage());
		ErrorCode errorCode = e.getErrorCode();
		ErrorResponse response = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
		return new ResponseEntity<>(response, errorCode.getStatus());
	}

	// 나머지 Exception handler
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e) {
		log.error("Unhandled Exception: ", e);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		ErrorResponse response = new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
		return new ResponseEntity<>(response, errorCode.getStatus());
	}

}
