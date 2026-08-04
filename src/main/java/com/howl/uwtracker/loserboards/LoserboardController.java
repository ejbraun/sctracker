package com.howl.uwtracker.loserboards;

import com.howl.uwtracker.leaderboards.dto.LeaderboardEntryResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserDeathsResponse;
import com.howl.uwtracker.loserboards.dto.RoleUserFailResponse;
import com.howl.uwtracker.loserboards.dto.UserResignResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * "Loserboards" — the mirror image of {@code LeaderboardController}. Protected by
 * SessionAuthInterceptor (under /api/**). {@code from}/{@code to} are optional on every endpoint
 * here, same time-window filter as the leaderboards API.
 */
@RestController
@RequestMapping("/api/loserboards")
public class LoserboardController {

    private final LoserboardService loserboardService;

    public LoserboardController(LoserboardService loserboardService) {
        this.loserboardService = loserboardService;
    }

    @GetMapping("/maps/{mapId}/worst")
    public ResponseEntity<List<LeaderboardEntryResponse>> worst(@PathVariable Integer mapId,
                                                                  @RequestParam(required = false) Integer limit,
                                                                  @RequestParam(required = false) Instant from,
                                                                  @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(loserboardService.worst(mapId, limit, from, to));
    }

    @GetMapping("/maps/{mapId}/role-deaths")
    public ResponseEntity<List<RoleUserDeathsResponse>> roleDeaths(@PathVariable Integer mapId,
                                                                     @RequestParam(required = false) Instant from,
                                                                     @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(loserboardService.roleDeaths(mapId, from, to));
    }

    @GetMapping("/maps/{mapId}/role-fails")
    public ResponseEntity<List<RoleUserFailResponse>> roleFails(@PathVariable Integer mapId,
                                                                  @RequestParam(required = false) Instant from,
                                                                  @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(loserboardService.roleFails(mapId, from, to));
    }

    @GetMapping("/maps/{mapId}/global-fails")
    public ResponseEntity<List<UserResignResponse>> globalFails(@PathVariable Integer mapId,
                                                                  @RequestParam(required = false) Instant from,
                                                                  @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(loserboardService.globalFails(mapId, from, to));
    }
}
