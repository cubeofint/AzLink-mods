<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\UserServerPrefix;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\DB;

/**
 * Game → site LuckPerms track export. A full payload is authoritative (wipe-safe).
 */
class PrefixCatalogImporter
{
    public const MAX_GROUPS = 256;

    /**
     * @param  array{schema?: mixed, hash: string, full?: mixed, groups?: mixed}  $payload
     * @return array<string, mixed>
     */
    public function import(int $serverId, array $payload): array
    {
        $hash = (string) ($payload['hash'] ?? '');
        $schema = isset($payload['schema']) ? substr((string) $payload['schema'], 0, 32) : null;
        $full = $this->truthy($payload['full'] ?? false);
        $groups = $this->normalizeGroups($payload['groups'] ?? []);

        $previous = PrefixCatalogImport::query()->where('server_id', $serverId)->first();
        if ($previous !== null && hash_equals($previous->hash, $hash)) {
            return [
                'unchanged' => true,
                'hash' => $hash,
                'imported_at' => $previous->imported_at?->toIso8601String(),
            ];
        }

        return DB::transaction(function () use ($serverId, $hash, $schema, $full, $groups) {
            $now = Carbon::now();
            $incoming = collect($groups)->keyBy('group');
            $existing = PrefixCatalog::query()
                ->where('server_id', $serverId)
                ->lockForUpdate()
                ->get()
                ->keyBy('group');

            $created = 0;
            $updated = 0;

            foreach ($incoming as $item) {
                $row = $existing->get($item['group']);
                if ($row === null) {
                    PrefixCatalog::query()->create($this->autoAttributes($serverId, $item, $now));
                    $created++;

                    continue;
                }

                $row->fill($this->autoAttributes($serverId, $item, $now));
                $row->save();
                $updated++;
            }

            $deleted = 0;
            if ($full) {
                $gone = $existing->filter(fn (PrefixCatalog $row) => ! $incoming->has($row->group));
                $deletedGroups = $gone->keys()->all();
                foreach ($gone as $row) {
                    $row->delete();
                    $deleted++;
                }
                $this->forgetPlayerGroups($serverId, $deletedGroups);
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
                'deleted' => $deleted,
                'imported_at' => $now->toIso8601String(),
            ];
        });
    }

    /**
     * @param  list<string>  $groups
     */
    private function forgetPlayerGroups(int $serverId, array $groups): void
    {
        if ($groups === []) {
            return;
        }

        $drop = array_fill_keys($groups, true);
        $rows = UserServerPrefix::query()
            ->where('server_id', $serverId)
            ->lockForUpdate()
            ->get();

        foreach ($rows as $row) {
            $available = [];
            foreach ($row->available ?? [] as $item) {
                $group = (string) ($item['group'] ?? '');
                if ($group !== '' && isset($drop[$group])) {
                    continue;
                }
                $available[] = $item;
            }

            $selected = $row->selected;
            if (is_string($selected) && isset($drop[$selected])) {
                $selected = null;
            }

            $row->available = $available;
            $row->selected = $selected;
            $row->save();
        }
    }

    /**
     * @param  array<string, mixed>  $item
     * @return array<string, mixed>
     */
    private function autoAttributes(int $serverId, array $item, Carbon $now): array
    {
        $display = $item['display_name'] !== '' ? $item['display_name'] : $item['group'];
        $sort = $item['track_index'];
        $raw = $item['prefix_raw'] !== '' ? $item['prefix_raw'] : null;

        return [
            'server_id' => $serverId,
            'group' => $item['group'],
            'display_name' => $display,
            'auto_display_name' => $display,
            'prefix_raw' => $raw,
            'auto_prefix_raw' => $raw,
            'category' => $item['category'],
            'sort' => $sort,
            'auto_sort' => $sort,
            'priority' => $item['priority'],
            'enabled' => true,
            'last_seen_at' => $now,
            'missing_since' => null,
        ];
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
