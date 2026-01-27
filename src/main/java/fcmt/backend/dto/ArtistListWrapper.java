package fcmt.backend.dto;

import java.util.List;

public record ArtistListWrapper(List<ArtistAiResponseDto> artists) {
	public ArtistListWrapper {
		// null 체크 및 읽기 전용 리스트로 복사하여 저장
		artists = (artists != null) ? List.copyOf(artists) : List.of();
	}

	@Override
	public List<ArtistAiResponseDto> artists() {
		// 원본 대신 복사본 반환
		return List.copyOf(artists);
	}
}