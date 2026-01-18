package fcmt.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
public class Artist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "artist_id")
    private Long artistId;

    @Column(name = "artist_name")
    private String artistName;

    @Column(name = "spotify_artist_id")
    private String spotifyArtistId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "genres", columnDefinition = "text[]", nullable = false)
    private List<String> genres;
}
