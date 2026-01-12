package fcmt.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class RegisterResponseDto {

	private final boolean success;

	private final String message;

	public static RegisterResponseDto success() {
		return RegisterResponseDto.builder().success(true).message("회원가입이 완료되었습니다.").build();
	}

	public static RegisterResponseDto fail() {
		return RegisterResponseDto.builder().success(false).message("회원가입이 실패했습니다.").build();
	}

}
