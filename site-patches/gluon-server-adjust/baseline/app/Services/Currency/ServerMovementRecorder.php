<?php

namespace Azuriom\Services\Currency;

use Azuriom\Models\CurrencyServerMovement;
use Azuriom\Models\User;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;

/**
 * Stores batches of server-wallet movements. Idempotent by (server_id, movement_id):
 * re-sent movements are ignored. Never changes site balances.
 */
class ServerMovementRecorder
{
    /**
     * @param  array<int, array<string, mixed>>  $movements
     * @return array{stored: int, duplicates: int, accepted_up_to: int}
     */
    public function record(int $serverId, array $movements): array
    {
        $stored = 0;
        $duplicates = 0;
        $maxId = 0;
        $uuids = [];

        foreach ($movements as $m) {
            foreach (['from_id', 'to_id'] as $key) {
                if (! empty($m[$key])) {
                    $uuids[] = $this->normalizeUuid((string) $m[$key]);
                }
            }
        }

        $users = $this->usersByUuid(array_unique(array_filter($uuids)));

        foreach ($movements as $m) {
            $id = (int) $m['id'];
            $maxId = max($maxId, $id);
            $from = ! empty($m['from_id']) ? $this->normalizeUuid((string) $m['from_id']) : null;
            $to = ! empty($m['to_id']) ? $this->normalizeUuid((string) $m['to_id']) : null;

            $row = CurrencyServerMovement::query()->firstOrCreate(
                ['server_id' => $serverId, 'movement_id' => $id],
                [
                    'type' => Str::limit((string) $m['type'], 32, ''),
                    'amount' => max(0, (int) $m['amount']),
                    'from_uuid' => $from,
                    'from_name' => isset($m['from_name']) ? Str::limit((string) $m['from_name'], 64, '') : null,
                    'from_user_id' => $from !== null ? ($users[$from] ?? null) : null,
                    'to_uuid' => $to,
                    'to_name' => isset($m['to_name']) ? Str::limit((string) $m['to_name'], 64, '') : null,
                    'to_user_id' => $to !== null ? ($users[$to] ?? null) : null,
                    'note' => isset($m['note']) ? Str::limit((string) $m['note'], 180) : null,
                    'occurred_at' => Carbon::createFromTimestampMs((int) $m['timestamp']),
                ]
            );

            $row->wasRecentlyCreated ? $stored++ : $duplicates++;
        }

        return ['stored' => $stored, 'duplicates' => $duplicates, 'accepted_up_to' => $maxId];
    }

    private function normalizeUuid(string $value): ?string
    {
        $hex = strtolower(str_replace('-', '', trim($value)));

        if (! preg_match('/^[0-9a-f]{32}$/', $hex)) {
            return null;
        }

        return substr($hex, 0, 8).'-'.substr($hex, 8, 4).'-'.substr($hex, 12, 4).'-'.substr($hex, 16, 4).'-'.substr($hex, 20);
    }

    /**
     * @param  array<int, string>  $uuids
     * @return array<string, int>
     */
    private function usersByUuid(array $uuids): array
    {
        if ($uuids === []) {
            return [];
        }

        $candidates = array_merge($uuids, array_map(fn ($u) => str_replace('-', '', $u), $uuids));
        $map = [];

        foreach (User::query()->whereIn('game_id', $candidates)->get(['id', 'game_id']) as $user) {
            $map[$this->normalizeUuid((string) $user->game_id)] = $user->id;
        }

        return $map;
    }
}
