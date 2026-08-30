package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.leaderboards.dto.GamblingStoneLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.ItemDropLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalBestEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalBestResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalSectionBestResponse;
import com.howl.uwtracker.leaderboards.dto.RoleMvpAwardResponse;
import com.howl.uwtracker.leaderboards.dto.SectionEntryResponse;
import com.howl.uwtracker.leaderboards.dto.UserStreakResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * specs/backend/05-leaderboards.md + the party-size dimension (specs/features/fow-and-party-size.md).
 * Protected by SessionAuthInterceptor (under /api/**). {@code from}/{@code to} (time window) and
 * {@code partySize} (null = all sizes for the map) are optional on every endpoint here.
 */
@RestController
@RequestMapping("/api/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/maps/{mapId}/overall")
    public ResponseEntity<List<LeaderboardEntryResponse>> overall(@PathVariable Integer mapId,
                                                                    @RequestParam(required = false) Integer partySize,
                                                                    @RequestParam(required = false) Integer limit,
                                                                    @RequestParam(required = false) Instant from,
                                                                    @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.overall(mapId, partySize, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}")
    public ResponseEntity<List<SectionEntryResponse>> section(@PathVariable Integer mapId,
                                                                @PathVariable String objectiveName,
                                                                @RequestParam(required = false) Integer partySize,
                                                                @RequestParam(required = false) Integer limit,
                                                                @RequestParam(required = false) Instant from,
                                                                @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.section(mapId, partySize, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}/start")
    public ResponseEntity<List<SectionEntryResponse>> sectionStart(@PathVariable Integer mapId,
                                                                      @PathVariable String objectiveName,
                                                                      @RequestParam(required = false) Integer partySize,
                                                                      @RequestParam(required = false) Integer limit,
                                                                      @RequestParam(required = false) Instant from,
                                                                      @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.sectionStart(mapId, partySize, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}/finish")
    public ResponseEntity<List<SectionEntryResponse>> sectionFinish(@PathVariable Integer mapId,
                                                                       @PathVariable String objectiveName,
                                                                       @RequestParam(required = false) Integer partySize,
                                                                       @RequestParam(required = false) Integer limit,
                                                                       @RequestParam(required = false) Instant from,
                                                                       @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.sectionFinish(mapId, partySize, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/streaks/completed")
    public ResponseEntity<List<UserStreakResponse>> longestCompletedStreak(@PathVariable Integer mapId,
                                                                              @RequestParam(required = false) Integer partySize,
                                                                              @RequestParam(required = false) Integer limit,
                                                                              @RequestParam(required = false) Instant from,
                                                                              @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.longestCompletedStreak(mapId, partySize, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/luckiest-players")
    public ResponseEntity<List<ItemDropLeaderResponse>> luckiestPlayers(@PathVariable Integer mapId,
                                                                          @RequestParam(required = false) Integer partySize,
                                                                          @RequestParam(required = false) Instant from,
                                                                          @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.luckiestPlayers(mapId, partySize, from, to));
    }

    @GetMapping("/maps/{mapId}/role-mvp-awards")
    public ResponseEntity<List<RoleMvpAwardResponse>> roleMvpAwards(@PathVariable Integer mapId,
                                                                       @RequestParam(required = false) Integer partySize,
                                                                       @RequestParam(required = false) Instant from,
                                                                       @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.roleMvpAwards(mapId, partySize, from, to));
    }

    @GetMapping("/maps/{mapId}/gamblers-anonymous")
    public ResponseEntity<List<GamblingStoneLeaderResponse>> gamblersAnonymous(@PathVariable Integer mapId,
                                                                                  @RequestParam(required = false) Integer partySize,
                                                                                  @RequestParam(required = false) Instant from,
                                                                                  @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.gamblersAnonymous(mapId, partySize, from, to));
    }

    @GetMapping("/me/maps/{mapId}/overall")
    public ResponseEntity<PersonalBestResponse> personalOverall(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                  @RequestParam(required = false) Integer partySize) {
        Long durationMs = leaderboardService.personalOverallBestMs(personId, mapId, partySize);
        return durationMs == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(new PersonalBestResponse(durationMs));
    }

    @GetMapping("/me/maps/{mapId}/overall/top")
    public ResponseEntity<List<PersonalBestEntryResponse>> personalOverallTop(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                                @RequestParam(required = false) Integer partySize,
                                                                                @RequestParam(required = false) Integer limit,
                                                                                @RequestParam(required = false) Instant from,
                                                                                @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.personalOverallTop(personId, mapId, partySize, limit, from, to));
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}")
    public ResponseEntity<PersonalSectionBestResponse> personalSection(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                         @PathVariable String objectiveName,
                                                                         @RequestParam(required = false) Integer partySize,
                                                                         @RequestParam(required = false) Instant from,
                                                                         @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse best = leaderboardService.personalSectionBestMs(personId, mapId, partySize, objectiveName, from, to);
        return best == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(best);
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}/start")
    public ResponseEntity<PersonalSectionBestResponse> personalSectionStart(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                               @PathVariable String objectiveName,
                                                                               @RequestParam(required = false) Integer partySize,
                                                                               @RequestParam(required = false) Instant from,
                                                                               @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse fastest = leaderboardService.personalSectionFastestStart(personId, mapId, partySize, objectiveName, from, to);
        return fastest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(fastest);
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}/finish")
    public ResponseEntity<PersonalSectionBestResponse> personalSectionFinish(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                                @PathVariable String objectiveName,
                                                                                @RequestParam(required = false) Integer partySize,
                                                                                @RequestParam(required = false) Instant from,
                                                                                @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse fastest = leaderboardService.personalSectionFinishMs(personId, mapId, partySize, objectiveName, from, to);
        return fastest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(fastest);
    }
}
