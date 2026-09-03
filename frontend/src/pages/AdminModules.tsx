import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import type { AdminModule } from '../api/types';
import { Panel } from '../components/Panel';
import { ErrorBanner } from '../components/ErrorBanner';

const MODULES_KEY = ['admin', 'modules'] as const;

/**
 * Admin-only — gated by AdminRoute. Manages the `modules` registry: the artifacts gwsctracker
 * serves for the ProjectPotato launcher. Per-user access to a gated module is granted on the User
 * Management page, not here. current_* columns are read-only (filled from each artifact's manifest).
 */
export function AdminModules() {
  const modulesQuery = useQuery({
    queryKey: MODULES_KEY,
    queryFn: () => api.get<AdminModule[]>('/admin/modules'),
  });

  return (
    <div>
      <h1>Modules</h1>
      <Panel>
        <p>
          The downloadable artifacts gwsctracker hosts for the ProjectPotato launcher. Public modules
          download without a key; the rest are gated per user (grant access from{' '}
          <strong>User Management</strong>). <code>sctracker</code>, <code>pp-exe</code> and{' '}
          <code>pp-base</code> are built in — disable them rather than deleting.
        </p>

        <CreateModuleForm />

        {modulesQuery.isLoading && <p>Loading…</p>}
        {modulesQuery.data && (
          <table>
            <thead>
              <tr>
                <th>Key</th>
                <th>Display name</th>
                <th>Bucket prefix</th>
                <th>Artifact object</th>
                <th>Manifest object</th>
                <th>Content type</th>
                <th>Sort</th>
                <th>Public</th>
                <th>Enabled</th>
                <th>Version</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {modulesQuery.data.map((module) => (
                <ModuleRow key={module.id} module={module} />
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}

const BUILT_IN_KEYS = new Set(['sctracker', 'pp-exe', 'pp-base']);

function ModuleRow({ module }: { module: AdminModule }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState({
    display_name: module.display_name,
    bucket_prefix: module.bucket_prefix,
    artifact_object: module.artifact_object,
    manifest_object: module.manifest_object ?? '',
    content_type: module.content_type,
    sort_order: String(module.sort_order),
  });

  const patchMutation = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api.patch<AdminModule>(`/admin/modules/${module.module_key}`, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: MODULES_KEY }),
  });

  const deleteMutation = useMutation({
    mutationFn: () => api.delete<void>(`/admin/modules/${module.module_key}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: MODULES_KEY }),
  });

  const dirty =
    draft.display_name !== module.display_name ||
    draft.bucket_prefix !== module.bucket_prefix ||
    draft.artifact_object !== module.artifact_object ||
    draft.manifest_object !== (module.manifest_object ?? '') ||
    draft.content_type !== module.content_type ||
    draft.sort_order !== String(module.sort_order);

  function save() {
    patchMutation.mutate({
      display_name: draft.display_name,
      bucket_prefix: draft.bucket_prefix,
      artifact_object: draft.artifact_object,
      manifest_object: draft.manifest_object.trim() === '' ? null : draft.manifest_object,
      content_type: draft.content_type,
      sort_order: Number(draft.sort_order) || 0,
    });
  }

  const field = (key: keyof typeof draft, size = 16) => (
    <input
      aria-label={`${module.module_key} ${key}`}
      size={size}
      value={draft[key]}
      onChange={(e) => setDraft((d) => ({ ...d, [key]: e.target.value }))}
    />
  );

  return (
    <Fragment>
      <tr>
        <td>
          <code>{module.module_key}</code>
        </td>
        <td>{field('display_name')}</td>
        <td>{field('bucket_prefix')}</td>
        <td>{field('artifact_object')}</td>
        <td>{field('manifest_object')}</td>
        <td>{field('content_type')}</td>
        <td>{field('sort_order', 3)}</td>
        <td>
          <button
            onClick={() => patchMutation.mutate({ is_public: !module.is_public })}
            disabled={patchMutation.isPending}
          >
            {module.is_public ? 'Public' : 'Private'}
          </button>
        </td>
        <td>
          <button
            onClick={() => patchMutation.mutate({ enabled: !module.enabled })}
            disabled={patchMutation.isPending}
          >
            {module.enabled ? 'Enabled' : 'Disabled'}
          </button>
        </td>
        <td>
          {module.current_version ?? '—'}
          {module.version_detected_at && (
            <div>
              <small>{new Date(module.version_detected_at).toLocaleString()}</small>
            </div>
          )}
        </td>
        <td>
          <button onClick={save} disabled={!dirty || patchMutation.isPending}>
            Save
          </button>{' '}
          {!BUILT_IN_KEYS.has(module.module_key) && (
            <button
              onClick={() => {
                if (window.confirm(`Delete module "${module.module_key}"? This also removes every grant for it.`)) {
                  deleteMutation.mutate();
                }
              }}
              disabled={deleteMutation.isPending}
            >
              Delete
            </button>
          )}
        </td>
      </tr>
      {(patchMutation.error || deleteMutation.error) && (
        <tr>
          <td colSpan={11}>
            <ErrorBanner error={patchMutation.error ?? deleteMutation.error} />
          </td>
        </tr>
      )}
    </Fragment>
  );
}

function CreateModuleForm() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    module_key: '',
    display_name: '',
    bucket_prefix: '',
    artifact_object: '',
    manifest_object: '',
    content_type: '',
    sort_order: '0',
    is_public: false,
  });

  const createMutation = useMutation({
    mutationFn: () =>
      api.post<AdminModule>('/admin/modules', {
        module_key: form.module_key.trim(),
        display_name: form.display_name.trim(),
        is_public: form.is_public,
        bucket_prefix: form.bucket_prefix.trim(),
        artifact_object: form.artifact_object.trim(),
        manifest_object: form.manifest_object.trim() === '' ? null : form.manifest_object.trim(),
        content_type: form.content_type.trim() === '' ? null : form.content_type.trim(),
        sort_order: Number(form.sort_order) || 0,
      }),
    onSuccess: () => {
      setForm({
        module_key: '',
        display_name: '',
        bucket_prefix: '',
        artifact_object: '',
        manifest_object: '',
        content_type: '',
        sort_order: '0',
        is_public: false,
      });
      queryClient.invalidateQueries({ queryKey: MODULES_KEY });
    },
  });

  const canSubmit =
    form.module_key.trim() !== '' &&
    form.display_name.trim() !== '' &&
    form.bucket_prefix.trim() !== '' &&
    form.artifact_object.trim() !== '';

  const input = (key: keyof typeof form, placeholder: string) => (
    <input
      aria-label={placeholder}
      placeholder={placeholder}
      value={form[key] as string}
      onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
    />
  );

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) createMutation.mutate();
      }}
    >
      <h2>Add a module</h2>
      <ErrorBanner error={createMutation.error} />
      {input('module_key', 'module key (a-z0-9-)')}
      {input('display_name', 'display name')}
      {input('bucket_prefix', 'bucket prefix, e.g. plugins/Foo')}
      {input('artifact_object', 'artifact object, e.g. Foo.dll')}
      {input('manifest_object', 'manifest object (optional)')}
      {input('content_type', 'content type (optional)')}
      {input('sort_order', 'sort')}
      <label>
        <input
          type="checkbox"
          checked={form.is_public}
          onChange={(e) => setForm((f) => ({ ...f, is_public: e.target.checked }))}
        />
        Public
      </label>
      <button type="submit" disabled={!canSubmit || createMutation.isPending}>
        Create module
      </button>
    </form>
  );
}
