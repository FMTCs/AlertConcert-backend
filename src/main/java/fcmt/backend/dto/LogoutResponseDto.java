package fcmt.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class LogoutResponseDto {

	private final boolean success;

	private final String message;

	public static LogoutResponseDto success() {
		return LogoutResponseDto.builder().success(true).message("로그아웃이 완료되었습니다.").build();
	}

	public static LogoutResponseDto fail() {
		return LogoutResponseDto.builder().success(false).message("로그아웃이 실패했습니다.").build();
	}

}
