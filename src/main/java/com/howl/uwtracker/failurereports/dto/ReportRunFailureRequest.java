package com.howl.uwtracker.failurereports.dto;

import java.util.List;

public record ReportRunFailureRequest(Long runId, List<String> roles) {
}
