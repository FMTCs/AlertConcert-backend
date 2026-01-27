package fcmt.backend.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
public class RecommendResponseDto {

	private List<ArtistDto> topArtists; // 사용자의 선호 아티스트 (UserPreference에서 추출)

	private List<GenreDto> topGenres;

	private List<ConcertDto> recommendedConcerts; // 추천된 공연 목록 (Concert 엔티티에서 변환)

	@Getter
	@Builder
	public static class ArtistDto {

		private String name;

		private String artistId;

	}

	@Getter
	@Builder
	public static class GenreDto {

		private String name;

	}

	@Getter
	@Builder
	public static class ConcertDto {

		private String concertName;

		private List<String> casts;

		private List<String> genres;

		private String posterImgUrl;

		private String bookingUrl;

		private int matchingRate;

	}

}