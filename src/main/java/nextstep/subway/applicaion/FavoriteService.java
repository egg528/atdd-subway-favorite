package nextstep.subway.applicaion;

import nextstep.auth.user.User;
import nextstep.member.application.MemberService;
import nextstep.member.domain.Member;
import nextstep.subway.applicaion.dto.FavoriteRequest;
import nextstep.subway.applicaion.dto.FavoriteResponse;
import nextstep.subway.domain.Favorite;
import nextstep.subway.domain.FavoriteRepository;
import nextstep.subway.domain.FavoriteValidationService;
import nextstep.subway.domain.Station;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class FavoriteService {
    private final MemberService memberService;
    private final StationService stationService;
    private final FavoriteValidationService favoriteValidationService;
    private final FavoriteRepository favoriteRepository;

    public FavoriteService(MemberService memberService,
                           StationService stationService,
                           FavoriteValidationService favoriteValidationService,
                           FavoriteRepository favoriteRepository) {
        this.memberService = memberService;
        this.stationService = stationService;
        this.favoriteValidationService = favoriteValidationService;
        this.favoriteRepository = favoriteRepository;
    }

    public FavoriteResponse createFavorite(User user, FavoriteRequest request) {
        Member member = memberService.findMember(user.getPrincipal());
        Station source = stationService.findById(request.getSource());
        Station target = stationService.findById(request.getTarget());

        favoriteValidationService.validateDuplicate(member.getId(), source.getId(), target.getId());

        Favorite favorite = request.toEntity();
        favorite.toMember(member.getId());
        favoriteRepository.save(favorite);
        return new FavoriteResponse(favorite, source, target);
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> findFavorites(User user) {
        Member member = memberService.findMember(user.getPrincipal());
        List<Favorite> favorites = favoriteRepository.findByMemberId(member.getId());
        List<Station> favoriteStations = findFavoriteStations(favorites);

        return favorites.stream()
                .map(it -> createFavoriteResponse(it, favoriteStations))
                .collect(Collectors.toList());
    }

    private List<Station> findFavoriteStations(List<Favorite> favorites) {
        List<Long> stationIds = new ArrayList<>();

        favorites.forEach(it -> {
            stationIds.add(it.getSource());
            stationIds.add(it.getTarget());
        });

        return stationService.findByIds(stationIds);
    }

    private FavoriteResponse createFavoriteResponse(Favorite favorite, List<Station> favoriteStations) {
        Station source = filterStationsById(favoriteStations, favorite.getSource());
        Station target = filterStationsById(favoriteStations, favorite.getTarget());
        return new FavoriteResponse(favorite, source, target);
    }

    private Station filterStationsById(List<Station> stations, Long id) {
        return stations.stream()
                .filter(it -> it.matchId(id))
                .findAny()
                .orElseThrow();
    }

    public void deleteFavorite(User user, Long id) {
        Member member = memberService.findMember(user.getPrincipal());
        Favorite favorite = favoriteRepository.findById(id)
                .orElseThrow(IllegalArgumentException::new);

        favoriteValidationService.validateOwner(favorite, member.getId());

        favoriteRepository.delete(favorite);
    }
}
