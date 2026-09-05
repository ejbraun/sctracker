package com.howl.uwtracker.admin.dto;

import java.util.List;

/**
 * Body of {@code GET /api/admin/modules/discover}: {@code discovered} is brand-new bucket folders
 * with no registry row at all ({@link DiscoveredModuleResponse}); {@code updates} is existing
 * modules whose bucket folder has a field they haven't registered yet ({@link ModuleUpdateResponse}).
 * One scan, one bucket walk, both lists.
 */
public record BucketScanResponse(List<DiscoveredModuleResponse> discovered, List<ModuleUpdateResponse> updates) {
}
