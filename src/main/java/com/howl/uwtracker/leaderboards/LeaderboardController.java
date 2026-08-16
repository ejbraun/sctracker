package com.howl.uwtracker.leaderboards;

import com.howl.uwtracker.auth.CurrentPersonId;
import com.howl.uwtracker.leaderboards.dto.ItemDropLeaderResponse;
import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalBestEntryResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalBestResponse;
import com.howl.uwtracker.leaderboards.dto.PersonalSectionBestResponse;
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
 * specs/backend/05-leaderboards.md. Protected by SessionAuthInterceptor (under /api/**).
 * {@code from}/{@code to} are optional on every endpoint here — the frontend's time-window filter
 * (past day/week/month/year, or unset for all time).
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
                                                                    @RequestParam(required = false) Integer limit,
                                                                    @RequestParam(required = false) Instant from,
                                                                    @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.overall(mapId, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}")
    public ResponseEntity<List<SectionEntryResponse>> section(@PathVariable Integer mapId,
                                                                @PathVariable String objectiveName,
                                                                @RequestParam(required = false) Integer limit,
                                                                @RequestParam(required = false) Instant from,
                                                                @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.section(mapId, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}/start")
    public ResponseEntity<List<SectionEntryResponse>> sectionStart(@PathVariable Integer mapId,
                                                                      @PathVariable String objectiveName,
                                                                      @RequestParam(required = false) Integer limit,
                                                                      @RequestParam(required = false) Instant from,
                                                                      @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.sectionStart(mapId, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/sections/{objectiveName}/finish")
    public ResponseEntity<List<SectionEntryResponse>> sectionFinish(@PathVariable Integer mapId,
                                                                       @PathVariable String objectiveName,
                                                                       @RequestParam(required = false) Integer limit,
                                                                       @RequestParam(required = false) Instant from,
                                                                       @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.sectionFinish(mapId, objectiveName, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/streaks/completed")
    public ResponseEntity<List<UserStreakResponse>> longestCompletedStreak(@PathVariable Integer mapId,
                                                                              @RequestParam(required = false) Integer limit,
                                                                              @RequestParam(required = false) Instant from,
                                                                              @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.longestCompletedStreak(mapId, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/luckiest-players")
    public ResponseEntity<List<ItemDropLeaderResponse>> luckiestPlayers(@PathVariable Integer mapId,
                                                                          @RequestParam(required = false) Instant from,
                                                                          @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.luckiestPlayers(mapId, from, to));
    }

    @GetMapping("/me/maps/{mapId}/overall")
    public ResponseEntity<PersonalBestResponse> personalOverall(@CurrentPersonId Long personId, @PathVariable Integer mapId) {
        Long durationMs = leaderboardService.personalOverallBestMs(personId, mapId);
        return durationMs == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(new PersonalBestResponse(durationMs));
    }

    @GetMapping("/me/maps/{mapId}/overall/top")
    public ResponseEntity<List<PersonalBestEntryResponse>> personalOverallTop(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                                @RequestParam(required = false) Integer limit,
                                                                                @RequestParam(required = false) Instant from,
                                                                                @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(leaderboardService.personalOverallTop(personId, mapId, limit, from, to));
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}")
    public ResponseEntity<PersonalSectionBestResponse> personalSection(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                         @PathVariable String objectiveName,
                                                                         @RequestParam(required = false) Instant from,
                                                                         @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse best = leaderboardService.personalSectionBestMs(personId, mapId, objectiveName, from, to);
        return best == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(best);
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}/start")
    public ResponseEntity<PersonalSectionBestResponse> personalSectionStart(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                               @PathVariable String objectiveName,
                                                                               @RequestParam(required = false) Instant from,
                                                                               @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse fastest = leaderboardService.personalSectionFastestStart(personId, mapId, objectiveName, from, to);
        return fastest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(fastest);
    }

    @GetMapping("/me/maps/{mapId}/sections/{objectiveName}/finish")
    public ResponseEntity<PersonalSectionBestResponse> personalSectionFinish(@CurrentPersonId Long personId, @PathVariable Integer mapId,
                                                                                @PathVariable String objectiveName,
                                                                                @RequestParam(required = false) Instant from,
                                                                                @RequestParam(required = false) Instant to) {
        PersonalSectionBestResponse fastest = leaderboardService.personalSectionFinishMs(personId, mapId, objectiveName, from, to);
        return fastest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(fastest);
    }
}
