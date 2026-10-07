<?php

namespace Azuriom\Services\Currency;

use Azuriom\Models\CurrencyServerOperation as Op;
use Azuriom\Models\CurrencyTransaction;
use Azuriom\Models\User;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use InvalidArgumentException;

/**
 * Site-side queue of gluon transfers site <-> server.
 *
 * to_server:   site wallet is debited at enqueue (ledger key op:{id}:debit); server credits on pull;
 *              a failed ack refunds the site wallet (op:{id}:refund).
 * from_server: nothing moves at enqueue; server debits its wallet on pull and acks applied,
 *              then the site wallet is credited (op:{id}:credit). A failed ack changes nothing.
 *
 * Every ledger write carries a unique idempotency key, so replays/double acks never apply twice.
 * The server applies operations for offline players as well (wallet is keyed by UUID).
 */
class ServerOperationQueue
{
    public function __construct(private CurrencyLedger $ledger)
    {
    }

    public static function enabled(): bool
    {
        return (bool) setting('currency.server_queue_enabled', false);
    }

    public function enqueue(User $user, int $serverId, string $direction, int $amount, array $meta = [], ?string $id = null): Op
    {
        if ($amount <= 0) {
            throw new InvalidArgumentException('Amount must be a positive integer.');
        }

        if (! in_array($direction, [Op::TO_SERVER, Op::FROM_SERVER], true)) {
            throw new InvalidArgumentException('Unknown direction.');
        }

        $id ??= (string) Str::uuid();

        if ($existing = Op::find($id)) {
            return $existing;
        }

        return DB::transaction(function () use ($user, $serverId, $direction, $amount, $meta, $id) {
            if ($direction === Op::TO_SERVER) {
                $this->ledger->adjustCoins($user, -$amount, CurrencyTransaction::TYPE_WITHDRAW, [
                    'direction' => 'to_server', 'operation_id' => $id,
                ], "op:{$id}:debit", $serverId);
            }

            return Op::create([
                'id' => $id,
                'user_id' => $user->id,
                'server_id' => $serverId,
                'direction' => $direction,
                'amount' => $amount,
                'status' => Op::PENDING,
                'meta' => $meta === [] ? null : $meta,
            ]);
        });
    }

    /**
     * Pending operations for one server. Does not depend on the player being online.
     */
    public function pending(int $serverId, int $limit = 50): Collection
    {
        $ops = Op::with('user')
            ->where('server_id', $serverId)
            ->where('status', Op::PENDING)
            ->orderBy('created_at')
            ->limit(max(1, min($limit, 200)))
            ->get();

        if ($ops->isNotEmpty()) {
            Op::whereIn('id', $ops->modelKeys())->increment('deliveries');
        }

        return $ops->map(fn (Op $op) => [
            'id' => $op->id,
            'user_id' => $op->user_id,
            'game_id' => $op->user?->game_id,
            'name' => $op->user?->name,
            'amount' => (string) $op->amount,
            'direction' => $op->direction,
            'created_at' => $op->created_at?->toIso8601String(),
        ]);
    }

    /**
     * Idempotent: an already final operation is returned unchanged.
     */
    public function ack(int $serverId, string $id, string $status, ?string $error = null): ?Op
    {
        if (! in_array($status, [Op::APPLIED, Op::FAILED], true)) {
            throw new InvalidArgumentException('Unknown status.');
        }

        return DB::transaction(function () use ($serverId, $id, $status, $error) {
            $op = Op::query()->whereKey($id)->where('server_id', $serverId)->lockForUpdate()->first();

            if ($op === null || $op->status !== Op::PENDING) {
                return $op;
            }

            $user = User::findOrFail($op->user_id);
            $amount = (float) $op->amount;

            if ($status === Op::APPLIED && $op->direction === Op::FROM_SERVER) {
                $this->ledger->adjustCoins($user, $amount, CurrencyTransaction::TYPE_DEPOSIT, [
                    'direction' => 'from_server', 'operation_id' => $op->id,
                ], "op:{$op->id}:credit", $serverId);
            }

            if ($status === Op::FAILED && $op->direction === Op::TO_SERVER) {
                $this->ledger->adjustCoins($user, $amount, CurrencyTransaction::TYPE_WITHDRAW, [
                    'direction' => 'refund', 'operation_id' => $op->id, 'reason' => $error,
                ], "op:{$op->id}:refund", $serverId);
            }

            $op->forceFill([
                'status' => $status,
                'error' => $error !== null ? Str::limit($error, 180) : null,
                'applied_at' => $status === Op::APPLIED ? now() : null,
            ])->save();

            return $op;
        });
    }
}
