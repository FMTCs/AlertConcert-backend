package fcmt.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpotifySearchResponseDto {

	private Artists artists;

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Artists {

		private List<ArtistItem> items;

	}

	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ArtistItem {

		private String id;

		private String name;

	}

}
