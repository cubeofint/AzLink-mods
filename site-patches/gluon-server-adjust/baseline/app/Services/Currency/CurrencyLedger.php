<?php

namespace Azuriom\Services\Currency;

use Azuriom\Models\CurrencyTransaction;
use Azuriom\Models\User;
use Azuriom\Models\UserServerBalance;
use Brick\Math\BigDecimal;
use Brick\Math\RoundingMode;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Support\Facades\DB;
use InvalidArgumentException;
use RuntimeException;

class CurrencyLedger
{
    /**
     * Adjust the user's donate money balance and write a ledger entry.
     */
    public function adjustMoney(User $user, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null, ?int $serverId = null): CurrencyTransaction
    {
        if ($existing = $this->findByIdempotency($idempotencyKey)) {
            return $existing;
        }

        return $this->onceByKey($idempotencyKey, fn () => DB::transaction(function () use ($user, $amount, $type, $meta, $idempotencyKey, $serverId) {
            return $this->mutateMoney($user, $amount, $type, $meta, $idempotencyKey, $serverId);
        }));
    }

    /**
     * Adjust the donate balance without converting DECIMAL values through float.
     */
    public function adjustMoneyDecimal(
        User $user,
        BigDecimal $amount,
        string $type,
        array $meta = [],
        ?string $idempotencyKey = null,
        ?int $serverId = null
    ): CurrencyTransaction {
        if ($existing = $this->findByIdempotency($idempotencyKey)) {
            return $existing;
        }

        return DB::transaction(function () use (
            $user,
            $amount,
            $type,
            $meta,
            $idempotencyKey,
            $serverId
        ) {
            $locked = User::query()->whereKey($user->id)->lockForUpdate()->firstOrFail();
            $delta = $amount->toScale(2, RoundingMode::UNNECESSARY);
            $balance = BigDecimal::of((string) $locked->getRawOriginal('money'))->toScale(2);
            $newBalance = $balance->plus($delta)->toScale(2);

            if ($newBalance->isNegative()) {
                throw new RuntimeException('insufficient_money');
            }

            $balanceString = (string) $newBalance;
            $amountString = (string) $delta;
            $locked->forceFill(['money' => $balanceString])->save();
            $user->setAttribute('money', $balanceString);

            return CurrencyTransaction::query()->create([
                'user_id' => $locked->id,
                'currency' => CurrencyTransaction::CURRENCY_MONEY,
                'amount' => $amountString,
                'balance_after' => $balanceString,
                'type' => $type,
                'server_id' => $serverId,
                'idempotency_key' => $idempotencyKey,
                'meta' => $meta === [] ? null : $meta,
            ]);
        });
    }

    /**
     * Adjust the user's global coins wallet and write a ledger entry.
     */
    public function adjustCoins(User $user, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null, ?int $serverId = null): CurrencyTransaction
    {
        if ($existing = $this->findByIdempotency($idempotencyKey)) {
            return $existing;
        }

        return $this->onceByKey($idempotencyKey, fn () => DB::transaction(function () use ($user, $amount, $type, $meta, $idempotencyKey, $serverId) {
            return $this->mutateCoins($user, $amount, $type, $meta, $idempotencyKey, $serverId);
        }));
    }

    /**
     * Adjust a per-server coins balance.
     */
    public function adjustServerBalance(User $user, int $serverId, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null): CurrencyTransaction
    {
        if ($existing = $this->findByIdempotency($idempotencyKey)) {
            return $existing;
        }

        return $this->onceByKey($idempotencyKey, fn () => DB::transaction(function () use ($user, $serverId, $amount, $type, $meta, $idempotencyKey) {
            if ($amount > 0 && $type === CurrencyTransaction::TYPE_SERVER_EARN) {
                $this->assertDailyServerEarnCap($user, $serverId, $amount);
            }

            return $this->mutateServerBalance($user, $serverId, $amount, $type, $meta, $idempotencyKey);
        }));
    }

    /**
     * Move coins from the global wallet to a server balance.
     *
     * @return array{global_balance: float, server_balance: float, replayed?: bool}
     */
    public function withdrawToServer(User $user, int $serverId, float $amount, ?string $idempotencyKey = null): array
    {
        if ($amount <= 0) {
            throw new InvalidArgumentException('Amount must be positive.');
        }

        $globalKey = $idempotencyKey !== null ? $idempotencyKey.':withdraw:global' : null;
        $serverKey = $idempotencyKey !== null ? $idempotencyKey.':withdraw:server' : null;

        if ($idempotencyKey !== null) {
            $existingGlobal = $this->findByIdempotency($globalKey);
            $existingServer = $this->findByIdempotency($serverKey);

            if ($existingGlobal !== null && $existingServer !== null) {
                return [
                    'global_balance' => (float) $user->fresh()->coins,
                    'server_balance' => $this->serverBalance($user, $serverId),
                    'replayed' => true,
                ];
            }
        }

        return DB::transaction(function () use ($user, $serverId, $amount, $globalKey, $serverKey) {
            $this->mutateCoins(
                $user,
                -$amount,
                CurrencyTransaction::TYPE_WITHDRAW,
                ['direction' => 'to_server', 'server_id' => $serverId],
                $globalKey,
                $serverId,
            );

            $serverTx = $this->mutateServerBalance(
                $user,
                $serverId,
                $amount,
                CurrencyTransaction::TYPE_WITHDRAW,
                ['direction' => 'from_global'],
                $serverKey,
            );

            return [
                'global_balance' => (float) $user->coins,
                'server_balance' => (float) $serverTx->balance_after,
            ];
        });
    }

    /**
     * Move coins from a server balance to the global wallet.
     *
     * @return array{global_balance: float, server_balance: float, replayed?: bool}
     */
    public function depositFromServer(User $user, int $serverId, float $amount, ?string $idempotencyKey = null): array
    {
        if ($amount <= 0) {
            throw new InvalidArgumentException('Amount must be positive.');
        }

        $serverKey = $idempotencyKey !== null ? $idempotencyKey.':deposit:server' : null;
        $globalKey = $idempotencyKey !== null ? $idempotencyKey.':deposit:global' : null;

        if ($idempotencyKey !== null) {
            $existingServer = $this->findByIdempotency($serverKey);
            $existingGlobal = $this->findByIdempotency($globalKey);

            if ($existingServer !== null && $existingGlobal !== null) {
                return [
                    'global_balance' => (float) $user->fresh()->coins,
                    'server_balance' => $this->serverBalance($user, $serverId),
                    'replayed' => true,
                ];
            }
        }

        return DB::transaction(function () use ($user, $serverId, $amount, $serverKey, $globalKey) {
            $serverTx = $this->mutateServerBalance(
                $user,
                $serverId,
                -$amount,
                CurrencyTransaction::TYPE_DEPOSIT,
                ['direction' => 'to_global'],
                $serverKey,
            );

            $this->mutateCoins(
                $user,
                $amount,
                CurrencyTransaction::TYPE_DEPOSIT,
                ['direction' => 'from_server', 'server_id' => $serverId],
                $globalKey,
                $serverId,
            );

            return [
                'global_balance' => (float) $user->coins,
                'server_balance' => (float) $serverTx->balance_after,
            ];
        });
    }

    /**
     * Convert donate money into global coins (one-way).
     *
     * @return array{money_spent: float, fee: float, coins_received: float, money_balance: float, coins_balance: float}
     */
    public function convertMoneyToCoins(User $user, float $moneyAmount): array
    {
        if ($moneyAmount <= 0) {
            throw new InvalidArgumentException('Amount must be positive.');
        }

        $rate = max(0.0, (float) setting('currency.convert_rate', 100));
        $feePercent = max(0.0, min(100.0, (float) setting('currency.convert_fee', 0)));
        $fee = round($moneyAmount * ($feePercent / 100), 2);
        $netMoney = round($moneyAmount - $fee, 2);
        $coins = round($netMoney * $rate, 2);

        if ($coins <= 0) {
            throw new InvalidArgumentException('Converted amount must be positive.');
        }

        return DB::transaction(function () use ($user, $moneyAmount, $fee, $netMoney, $coins, $rate, $feePercent) {
            $this->mutateMoney(
                $user,
                -$moneyAmount,
                CurrencyTransaction::TYPE_CONVERT,
                [
                    'to' => 'coins',
                    'rate' => $rate,
                    'fee' => $fee,
                    'fee_percent' => $feePercent,
                    'net_money' => $netMoney,
                    'coins' => $coins,
                ],
            );

            $this->mutateCoins(
                $user,
                $coins,
                CurrencyTransaction::TYPE_CONVERT,
                [
                    'from' => 'money',
                    'rate' => $rate,
                    'fee' => $fee,
                    'money_spent' => $moneyAmount,
                ],
            );

            return [
                'money_spent' => $moneyAmount,
                'fee' => $fee,
                'coins_received' => $coins,
                'money_balance' => (float) $user->money,
                'coins_balance' => (float) $user->coins,
            ];
        });
    }

    public function serverBalance(User $user, int $serverId): float
    {
        return (float) (UserServerBalance::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->value('balance') ?? 0);
    }

    protected function mutateMoney(User $user, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null, ?int $serverId = null): CurrencyTransaction
    {
        $locked = User::query()->whereKey($user->id)->lockForUpdate()->firstOrFail();
        $newBalance = round(((float) $locked->money) + $amount, 2);

        if ($newBalance < 0) {
            throw new RuntimeException('insufficient_money');
        }

        $locked->forceFill(['money' => $newBalance])->save();
        $user->setAttribute('money', $newBalance);

        return $this->write($locked->id, CurrencyTransaction::CURRENCY_MONEY, $amount, $newBalance, $type, $serverId, $meta, $idempotencyKey);
    }

    protected function mutateCoins(User $user, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null, ?int $serverId = null): CurrencyTransaction
    {
        $locked = User::query()->whereKey($user->id)->lockForUpdate()->firstOrFail();
        $newBalance = round(((float) $locked->coins) + $amount, 2);

        if ($newBalance < 0) {
            throw new RuntimeException('insufficient_coins');
        }

        $locked->forceFill(['coins' => $newBalance])->save();
        $user->setAttribute('coins', $newBalance);

        return $this->write($locked->id, CurrencyTransaction::CURRENCY_COINS, $amount, $newBalance, $type, $serverId, $meta, $idempotencyKey);
    }

    protected function mutateServerBalance(User $user, int $serverId, float $amount, string $type, array $meta = [], ?string $idempotencyKey = null): CurrencyTransaction
    {
        $balance = UserServerBalance::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->lockForUpdate()
            ->first();

        if ($balance === null) {
            UserServerBalance::create([
                'user_id' => $user->id,
                'server_id' => $serverId,
                'balance' => 0,
            ]);

            $balance = UserServerBalance::query()
                ->where('user_id', $user->id)
                ->where('server_id', $serverId)
                ->lockForUpdate()
                ->firstOrFail();
        }

        $newBalance = round(((float) $balance->balance) + $amount, 2);

        if ($newBalance < 0) {
            throw new RuntimeException('insufficient_server_coins');
        }

        $balance->update(['balance' => $newBalance]);

        return $this->write($user->id, CurrencyTransaction::CURRENCY_COINS, $amount, $newBalance, $type, $serverId, [
            ...$meta,
            'scope' => 'server',
        ], $idempotencyKey);
    }

    protected function write(
        int $userId,
        string $currency,
        float $amount,
        float $balanceAfter,
        string $type,
        ?int $serverId,
        array $meta,
        ?string $idempotencyKey,
    ): CurrencyTransaction {
        return CurrencyTransaction::create([
            'user_id' => $userId,
            'currency' => $currency,
            'amount' => $amount,
            'balance_after' => $balanceAfter,
            'type' => $type,
            'server_id' => $serverId,
            'idempotency_key' => $idempotencyKey,
            'meta' => $meta === [] ? null : $meta,
        ]);
    }

    /**
     * Run a keyed mutation; if a concurrent request with the same key won the race the unique
     * index rejects ours (whole transaction rolled back) and we return the stored entry instead.
     */
    protected function onceByKey(?string $idempotencyKey, callable $mutation): CurrencyTransaction
    {
        try {
            return $mutation();
        } catch (UniqueConstraintViolationException $e) {
            if ($existing = $this->findByIdempotency($idempotencyKey)) {
                return $existing;
            }

            throw $e;
        }
    }

    protected function findByIdempotency(?string $idempotencyKey): ?CurrencyTransaction
    {
        if ($idempotencyKey === null || $idempotencyKey === '') {
            return null;
        }

        return CurrencyTransaction::query()
            ->where('idempotency_key', $idempotencyKey)
            ->first();
    }

    protected function assertDailyServerEarnCap(User $user, int $serverId, float $incoming): void
    {
        $cap = max(1, (int) setting('currency.daily_server_earn_cap', 10000));

        $earnedToday = (float) CurrencyTransaction::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->where('type', CurrencyTransaction::TYPE_SERVER_EARN)
            ->whereDate('created_at', today())
            ->sum('amount');

        if (($earnedToday + $incoming) > $cap) {
            throw new RuntimeException('daily_server_earn_cap');
        }
    }
}
