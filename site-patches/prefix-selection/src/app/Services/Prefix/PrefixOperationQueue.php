<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\PrefixOperation as Op;
use Azuriom\Models\User;
use Azuriom\Models\UserServerPrefix;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use InvalidArgumentException;
use RuntimeException;

/**
 * Site → game prefix_select queue. Separate from ServerOperationQueue (gluons).
 *
 * GET leases pending rows for this server until leased_until (default 30s).
 * POST ack is idempotent: a final row is returned unchanged.
 */
class PrefixOperationQueue
{
    public const TYPE_SELECT = Op::TYPE_SELECT;

    public function __construct(private PrefixPlayerLinker $linker)
    {
    }

    public function enqueue(User $user, int $serverId, ?string $group, array $meta = [], ?string $id = null): Op
    {
        $id ??= (string) Str::uuid();

        if ($existing = Op::query()->find($id)) {
            return $existing;
        }

        $pending = Op::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->where('status', Op::PENDING)
            ->first();

        if ($pending !== null) {
            throw new RuntimeException('prefix_op_pending');
        }

        return Op::query()->create([
            'id' => $id,
            'user_id' => $user->id,
            'server_id' => $serverId,
            'type' => self::TYPE_SELECT,
            'group' => $group,
            'status' => Op::PENDING,
            'source' => $meta['source'] ?? 'profile',
        ]);
    }

    /**
     * Pending prefix_select rows for one server. Marks a lease so another poll
     * does not re-deliver until leased_until passes.
     *
     * @return Collection<int, array{op_id: string, type: string, uuid: string|null, group: string|null}>
     */
    public function pending(int $serverId, int $limit = 50): Collection
    {
        $limit = max(1, min($limit, 200));
        $leaseSeconds = max(5, (int) setting('prefixes.lease_seconds', 30));
        $leaseUntil = now()->addSeconds($leaseSeconds);

        return DB::transaction(function () use ($serverId, $limit, $leaseUntil) {
            $ops = Op::query()
                ->with('user')
                ->where('server_id', $serverId)
                ->where('type', self::TYPE_SELECT)
                ->where('status', Op::PENDING)
                ->where(function ($query) {
                    $query->whereNull('leased_until')
                        ->orWhere('leased_until', '<=', now());
                })
                ->orderBy('created_at')
                ->limit($limit)
                ->lockForUpdate()
                ->get();

            if ($ops->isNotEmpty()) {
                Op::query()->whereIn('id', $ops->modelKeys())->update([
                    'leased_until' => $leaseUntil,
                    'deliveries' => DB::raw('deliveries + 1'),
                ]);
            }

            return $ops->map(fn (Op $op) => [
                'op_id' => $op->id,
                'type' => self::TYPE_SELECT,
                'uuid' => $this->linker->normalizeUuid((string) ($op->user?->game_id ?? ''))
                    ?? $op->user?->game_id,
                'group' => $op->group,
            ]);
        });
    }

    /**
     * Idempotent. status = applied | rejected.
     */
    public function ack(int $serverId, string $opId, string $status, ?string $reason, ?string $selected): ?Op
    {
        if (! in_array($status, [Op::APPLIED, Op::REJECTED], true)) {
            throw new InvalidArgumentException('Unknown status.');
        }

        return DB::transaction(function () use ($serverId, $opId, $status, $reason, $selected) {
            $op = Op::query()
                ->whereKey($opId)
                ->where('server_id', $serverId)
                ->lockForUpdate()
                ->first();

            if ($op === null) {
                return null;
            }

            if ($op->status !== Op::PENDING) {
                return $op;
            }

            $finalSelected = $status === Op::APPLIED
                ? ($selected !== null && $selected !== '' ? $selected : $op->group)
                : $selected;

            $op->forceFill([
                'status' => $status,
                'reason' => $reason !== null ? Str::limit($reason, 180) : null,
                'selected' => $finalSelected !== null && $finalSelected !== '' ? $finalSelected : null,
                'applied_at' => $status === Op::APPLIED ? now() : null,
                'leased_until' => null,
            ])->save();

            if ($status === Op::APPLIED) {
                $row = UserServerPrefix::query()
                    ->where('user_id', $op->user_id)
                    ->where('server_id', $serverId)
                    ->lockForUpdate()
                    ->first();

                if ($row !== null) {
                    $row->forceFill([
                        'selected' => $op->selected,
                        'synced_at' => now(),
                    ])->save();
                }
            }

            return $op;
        });
    }
}
