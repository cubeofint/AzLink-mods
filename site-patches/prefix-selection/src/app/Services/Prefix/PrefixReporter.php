<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\UserServerPrefix;
use Illuminate\Support\Carbon;

/**
 * Game → site snapshot. Last write wins per (user, server_id).
 * Categories are stored as given; the cabinet only offers quest.
 */
class PrefixReporter
{
    public function __construct(private PrefixPlayerLinker $linker)
    {
    }

    /**
     * @param  array<int, array<string, mixed>>  $players
     * @return array{stored: int, skipped: int}
     */
    public function report(int $serverId, array $players): array
    {
        $stored = 0;
        $skipped = 0;
        $now = Carbon::now();

        foreach ($players as $player) {
            $user = $this->linker->find(
                isset($player['uuid']) ? (string) $player['uuid'] : null,
                isset($player['name']) ? (string) $player['name'] : null
            );

            if ($user === null) {
                $skipped++;

                continue;
            }

            $available = $this->normalizeAvailable($player['available'] ?? []);
            $selected = $player['selected'] ?? null;
            $selected = is_string($selected) && $selected !== '' ? $selected : null;

            UserServerPrefix::query()->updateOrCreate(
                [
                    'user_id' => $user->id,
                    'server_id' => $serverId,
                ],
                [
                    'available' => $available,
                    'selected' => $selected,
                    'locked_staff' => (bool) ($player['locked_staff'] ?? false),
                    'synced_at' => $now,
                ]
            );

            $stored++;
        }

        return ['stored' => $stored, 'skipped' => $skipped];
    }

    /**
     * @param  mixed  $available
     * @return list<array{group: string, prefix_raw: string, category: string}>
     */
    public function normalizeAvailable(mixed $available): array
    {
        if (! is_array($available)) {
            return [];
        }

        $out = [];
        foreach ($available as $item) {
            if (! is_array($item) || ! filled($item['group'] ?? null)) {
                continue;
            }

            $category = substr(trim((string) ($item['category'] ?? '')), 0, 16);
            if ($category === '') {
                $category = 'other';
            }

            $out[] = [
                'group' => substr((string) $item['group'], 0, 64),
                'prefix_raw' => substr((string) ($item['prefix_raw'] ?? ''), 0, 255),
                'category' => $category,
            ];
        }

        return $out;
    }
}
