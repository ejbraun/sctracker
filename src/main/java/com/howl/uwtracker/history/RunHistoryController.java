package com.howl.uwtracker.history;

import com.howl.uwtracker.history.dto.RunDetailResponse;
import com.howl.uwtracker.history.dto.RunSummaryResponse;
import com.howl.uwtracker.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** specs/backend/06-run-history.md. Protected by SessionAuthInterceptor (under /api/**). */
@RestController
@RequestMapping("/api/runs")
public class RunHistoryController {

    private static final int DEFAULT_SIZE = 25;

    private final RunHistoryService runHistoryService;

    public RunHistoryController(RunHistoryService runHistoryService) {
        this.runHistoryService = runHistoryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<RunSummaryResponse>> search(
            @RequestParam(required = false) Long person,
            @RequestParam(required = false) Long character,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer map,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false, name = "end_reason") String endReason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {

        RunHistoryFilter filter = new RunHistoryFilter(person, character, role, map, from, to, completed, endReason);
        var results = runHistoryService.search(filter, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.from(results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunDetailResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(runHistoryService.detail(id));
    }
}
