package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "concerts")
@Builder
public class Concert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "concert_id")
	private Long concertId;

	@Column(name = "concert_name", nullable = false)
	private String concertName;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "genres", columnDefinition = "text[]", nullable = false)
	private List<String> genres;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "casts", columnDefinition = "jsonb")
	private Map<String, Object> casts;

	@Column(name = "performance_start_date")
	private LocalDate performanceStartDate;

	@Column(name = "performance_end_date")
	private LocalDate performanceEndDate;

	@Column(name = "booking_url")
	private String bookingUrl;

	@Column(name = "poster_img_url")
	private String posterImgUrl;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}