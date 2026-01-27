package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "artists")
public class Artist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "artist_id", nullable = false)
	private Long artistId;

	@Column(name = "spotify_artist_id", nullable = false)
	private String spotifyArtistId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "genres", columnDefinition = "text[]", nullable = false)
	private List<String> genres;

	@Column(name = "artist_name", nullable = false)
	private String artistName;

}
