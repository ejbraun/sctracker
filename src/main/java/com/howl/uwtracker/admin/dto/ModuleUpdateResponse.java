package com.howl.uwtracker.admin.dto;

/**
 * An already-registered module whose bucket folder now has a {@code .version.json} or
 * {@code .patch.txt} the registry row doesn't know about yet — surfaced by
 * {@code GET /api/admin/modules/discover} alongside brand-new folders (see
 * {@link DiscoveredModuleResponse}), so an admin can fill in a field that showed up after the
 * module was first registered (e.g. patch notes added later) without hand-typing the path.
 *
 * <p>Deliberately narrow: only ever proposes filling in a currently-{@code null} field, never
 * changing or clearing one that's already set — {@code artifact_object} isn't covered at all
 * (changing it is unusual enough to stay a manual edit). {@code proposedManifestObject} /
 * {@code proposedPatchNotesObject} are {@code null} when there's nothing new for that field; at
 * least one is non-null for every entry in the list. The admin's "Update" action is just
 * {@code PATCH /api/admin/modules/{moduleKey}} with these two fields — nulls there already mean
 * "leave alone".
 */
public record ModuleUpdateResponse(
        String moduleKey,
        String displayName,
        String bucketPrefix,
        String proposedManifestObject,
        String proposedPatchNotesObject) {
}
