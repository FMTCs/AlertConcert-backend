package fcmt.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
		log.warn("Validation failed: {}", e.getBindingResult().getAllErrors().get(0).getDefaultMessage());

		// 발생한 에러들 중 첫 번째 에러 메시지만 추출
		String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
		ErrorCode errorCode = ErrorCode.INVALID_REGISTER_INPUT_VALUE;
		// 이전에 정의한 ErrorCode.INVALID_INPUT_VALUE(400) 활용
		ErrorResponse response = new ErrorResponse(errorCode.getCode(), message);

		return new ResponseEntity<>(response, errorCode.getStatus());
	}

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
