package com.howl.uwtracker.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * specs/backend/00-overview.md's "Routing: /api prefix & SPA fallback" — forwards any request that
 * isn't /api/**, /upload-run, or a static asset (has a file extension) to index.html, so React
 * Router can render deep links (e.g. a browser hitting /runs/42 directly). Explicit @RequestMapping
 * controllers (/api/**, /upload-run) win over this catch-all regardless of registration order —
 * Spring dispatches to the most specific match.
 */
@Controller
public class SpaFallbackController {

    @RequestMapping("/{path:[^.]*}")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @RequestMapping("/**/{path:[^.]*}")
    public String forwardNested() {
        return "forward:/index.html";
    }
}
