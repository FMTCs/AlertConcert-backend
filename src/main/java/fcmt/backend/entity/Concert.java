package fcmt.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

@Entity
@Table(name = "concerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concert {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long concertId;

	@Column(nullable = false)
	private String concertName;

	@Type(StringArrayType.class)
	@Column(columnDefinition = "text[]")
	private String[] genres;

	@Type(JsonBinaryType.class)
	@Column(columnDefinition = "jsonb")
	private String casts;

	private LocalDate bookingStartDate;

	private LocalDate bookingEndDate;

	private String bookingUrl;

	private String posterImgUrl;

}