// package fcmt.backend.service;
//
// import fcmt.backend.ai.AiClient;
// import fcmt.backend.entity.Artist;
// import fcmt.backend.repository.ArtistRepository;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;
//
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Optional;
//
// @Service
// @RequiredArgsConstructor
// public class ArtistService {
// private final ArtistRepository artistRepository;
// private final AiClient aiClient;
//
// public static List<String> getArtistGenres(List<String> ids) {
// List<String> unKnownArtists = new ArrayList<>();
// for (String spotifyId: ids) {
// Optional<Artist> artist = artistRepository.findByspotifyArtistId(spotifyId);
//
// if(artist.isPresent()) {
// log.info(">>> 이미 존재하는 가수 입니다!");
// } else {
// unKnownArtists.add(id);
// }
// }
//
// if(!unKnownArtists.isEmpty()) {
// List<ArtistDto> newArtist = aiClient.fetchGenresFromAi(unKnownArtists);
// for(ArtistDto dto: newArtist) {
// // DB에 저장
// Artist artistEntity = new Artist(dto.getSpotifyAtristId, dto.getArtistName,
// dto.getGenres)
// }
// }
//
// return new ArrayList<String>();
// }
// }
