package fcmt.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ArtistAiResponseDto {

	private String spotifyArtistId;

	private String artistName;

	// [TODO] 장르가 gid 기반 or 이름 매칭?
	private List<String> genres;

}
