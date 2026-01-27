package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "user_preferences")
public class UserPreference {

	@Id
	@Column(name = "uid")
	private Long uid;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "uid")
	private User user;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "artist_ids", columnDefinition = "bigint[]", nullable = false)
	private List<Long> artistIds;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}