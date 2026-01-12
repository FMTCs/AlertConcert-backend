package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Getter
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

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "preference", columnDefinition = "jsonb", nullable = false)
	private Map<String, Object> preference;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

}