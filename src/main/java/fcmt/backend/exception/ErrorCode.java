package fcmt.backend.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "유저를 찾을 수 없습니다"),

	INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "비밀번호가 올바르지 않습니다"),

	TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "토큰이 유효하지 않습니다"),

	SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "세션이 만료되었습니다."),

	AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "아이디 혹은 비밀번호가 유효하지 않습니다"),
	DUPLICATE_USER(HttpStatus.CONFLICT, "DUPLICATE_USER", "이미 존재하는 아이디입니다"),

	// AI 사용 관련 에러 메시지
	AI_API_ERROR(HttpStatus.BAD_GATEWAY, "A001", "AI 서비스와의 통신이 원활하지 않습니다."),
	GENRE_NOT_FOUND(HttpStatus.PARTIAL_CONTENT, "A002", "가수 장르 정보를 분석할 수 없습니다."),

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다");

	private final HttpStatus status;

	private final String code;

	private final String message;

}
