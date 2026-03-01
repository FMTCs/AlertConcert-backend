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

	// 회원가입 시 비밀번호 검증 오류
	PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."),
	INVALID_PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_LENGTH", "비밀번호는 8자 이상이어야 합니다."),

	USER_PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_PREFERENCE_NOT_FOUND", "선호도 정보가 없습니다."),
	// AI 사용 관련 에러 메시지
	AI_API_ERROR(HttpStatus.BAD_GATEWAY, "AI_API_ERROR", "AI 서비스와의 통신이 원활하지 않습니다."),
	GENRE_NOT_FOUND(HttpStatus.PARTIAL_CONTENT, "GENRE_NOT_FOUND", "가수 장르 정보를 분석할 수 없습니다."),

	// spotify api 사용 관련 에러 메시지
	// Spotify에 사용하는 token 인증값이 틀림
	SPOTIFY_APP_AUTH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SPOTIFY_APP_AUTH_FAILED",
			"시스템 인증 오류가 발생했습니다. 관리자에게 문의하세요."),
	// 응답엔 성공했으나 데이터가 부실한 경우
	SPOTIFY_INVALID_TOKEN(HttpStatus.BAD_GATEWAY, "SPOTIFY_INVALID_TOKEN", "Spotify 서비스 연결이 원활하지 않습니다"),
	// 네트워크 타임아웃, 커넥션 거부 등 spotify 서비스와 통신 장애
	SPOTIFY_API_ERROR(HttpStatus.BAD_GATEWAY, "SPOTIFY_API_ERROR", "외부 서비스(Spotify) 연동 중 오류가 발생했습니다."),

	// DTO 검증 시 에러 발생 처리 코드
	// 메시지는 기본적으로 불완전 DTO에서 직접 보냄
	INVALID_REGISTER_INPUT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_REGISTER_INPUT_VALUE", "회원가입에 실패했습니다."),

	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다");

	private final HttpStatus status;

	private final String code;

	private final String message;

}
