<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;
use RuntimeException;

/**
 * Game → site LuckPerms track export. Per-server upsert; admin overrides stay.
 */
class PrefixCatalogImporter
{
    public const MAX_GROUPS = 256;

    /**
     * @param  array{schema?: mixed, hash: string, full?: mixed, force?: mixed, groups?: mixed}  $payload
     * @return array<string, mixed>
     */
    public function import(int $serverId, array $payload): array
    {
        $hash = (string) ($payload['hash'] ?? '');
        $schema = isset($payload['schema']) ? substr((string) $payload['schema'], 0, 32) : null;
        $full = $this->truthy($payload['full'] ?? false);
        $force = $this->truthy($payload['force'] ?? false);
        $groups = $this->normalizeGroups($payload['groups'] ?? []);

        $previous = PrefixCatalogImport::query()->where('server_id', $serverId)->first();
        if ($previous !== null && hash_equals($previous->hash, $hash)) {
            return [
                'unchanged' => true,
                'hash' => $hash,
                'imported_at' => $previous->imported_at?->toIso8601String(),
            ];
        }

        return DB::transaction(function () use ($serverId, $hash, $schema, $full, $force, $groups) {
            $now = Carbon::now();
            $incoming = collect($groups)->keyBy('group');
            $existing = PrefixCatalog::query()
                ->where('server_id', $serverId)
                ->lockForUpdate()
                ->get()
                ->keyBy('group');

            $wouldDisable = $existing
                ->filter(fn (PrefixCatalog $row) => $row->missing_since === null && ! $incoming->has($row->group));

            $this->assertMissingGuard($existing, $wouldDisable, $groups, $full, $force);

            $created = 0;
            $updated = 0;

            foreach ($incoming as $group => $item) {
                $row = $existing->get($group);
                if ($row === null) {
                    PrefixCatalog::query()->create($this->newAutoAttributes($serverId, $item, $now));
                    $created++;

                    continue;
                }

                $this->applyAutoUpdate($row, $item, $now);
                $row->save();
                $updated++;
            }

            $missing = 0;
            if ($full && $groups !== []) {
                foreach ($wouldDisable as $row) {
                    if ($row->missing_since === null) {
                        $row->missing_since = $now;
                    }
                    if (! $row->override_enabled) {
                        $row->enabled = false;
                    }
                    $row->save();
                    $missing++;
                }
            }

            PrefixCatalogImport::query()->updateOrCreate(
                ['server_id' => $serverId],
                [
                    'schema' => $schema,
                    'hash' => $hash,
                    'full' => $full,
                    'group_count' => count($groups),
                    'imported_at' => $now,
                ]
            );

            return [
                'unchanged' => false,
                'hash' => $hash,
                'created' => $created,
                'updated' => $updated,
                'missing' => $missing,
                'imported_at' => $now->toIso8601String(),
            ];
        });
    }

    /**
     * @param  \Illuminate\Support\Collection<string, PrefixCatalog>  $existing
     * @param  \Illuminate\Support\Collection<string, PrefixCatalog>  $wouldDisable
     * @param  list<array<string, mixed>>  $groups
     */
    private function assertMissingGuard($existing, $wouldDisable, array $groups, bool $full, bool $force): void
    {
        if (! $full || $groups === []) {
            return;
        }

        $present = $existing->filter(fn (PrefixCatalog $row) => $row->missing_since === null)->count();
        if ($present === 0) {
            return;
        }

        if (($wouldDisable->count() / $present) > 0.5 && ! $force) {
            throw new RuntimeException('prefix_catalog_force_required');
        }
    }

    /**
     * @param  array<string, mixed>  $item
     * @return array<string, mixed>
     */
    private function newAutoAttributes(int $serverId, array $item, Carbon $now): array
    {
        $display = $item['display_name'] !== '' ? $item['display_name'] : $item['group'];
        $sort = $item['track_index'];

        return [
            'server_id' => $serverId,
            'group' => $item['group'],
            'display_name' => $display,
            'auto_display_name' => $display,
            'prefix_raw' => $item['prefix_raw'] !== '' ? $item['prefix_raw'] : null,
            'auto_prefix_raw' => $item['prefix_raw'] !== '' ? $item['prefix_raw'] : null,
            'category' => $item['category'],
            'sort' => $sort,
            'auto_sort' => $sort,
            'priority' => $item['priority'],
            'enabled' => true,
            'source' => PrefixCatalog::SOURCE_AUTO,
            'override_display_name' => false,
            'override_sort' => false,
            'override_enabled' => false,
            'last_seen_at' => $now,
            'missing_since' => null,
        ];
    }

    /**
     * @param  array<string, mixed>  $item
     */
    private function applyAutoUpdate(PrefixCatalog $row, array $item, Carbon $now): void
    {
        $display = $item['display_name'] !== '' ? $item['display_name'] : $item['group'];
        $sort = $item['track_index'];
        $raw = $item['prefix_raw'] !== '' ? $item['prefix_raw'] : null;

        $row->auto_display_name = $display;
        $row->auto_prefix_raw = $raw;
        $row->auto_sort = $sort;
        $row->priority = $item['priority'];
        $row->category = $item['category'];
        $row->last_seen_at = $now;
        $row->missing_since = null;

        if (! $row->override_display_name) {
            $row->display_name = $display;
        }
        if (! $row->override_sort) {
            $row->sort = $sort;
        }
        $row->prefix_raw = $raw;

        if (! $row->override_enabled) {
            $row->enabled = true;
        }
    }

    /**
     * @param  mixed  $groups
     * @return list<array{group: string, prefix_raw: string, priority: int, track_index: int, display_name: string, category: string}>
     */
    public function normalizeGroups(mixed $groups): array
    {
        if (! is_array($groups)) {
            return [];
        }

        $out = [];
        $seen = [];
        foreach ($groups as $item) {
            if (! is_array($item) || ! filled($item['group'] ?? null)) {
                continue;
            }

            $group = substr((string) $item['group'], 0, 64);
            if ($group === '' || isset($seen[$group])) {
                continue;
            }
            $seen[$group] = true;

            $out[] = [
                'group' => $group,
                'prefix_raw' => substr((string) ($item['prefix_raw'] ?? ''), 0, 255),
                'priority' => (int) ($item['priority'] ?? 0),
                'track_index' => (int) ($item['track_index'] ?? 0),
                'display_name' => substr((string) ($item['display_name'] ?? ''), 0, 120),
                'category' => $this->normalizeCategory($item['category'] ?? null),
            ];

            if (count($out) >= self::MAX_GROUPS) {
                break;
            }
        }

        return $out;
    }

    private function normalizeCategory(mixed $category): string
    {
        $value = substr(trim((string) ($category ?? '')), 0, 16);

        return $value !== '' ? $value : 'other';
    }

    private function truthy(mixed $value): bool
    {
        return filter_var($value, FILTER_VALIDATE_BOOLEAN);
    }
}
