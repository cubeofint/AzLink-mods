<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A gluon movement inside a game server wallet (pay, trader, admin, site transfer).
 * Display-only: never affects site balances.
 */
class CurrencyServerMovement extends Model
{
    public const UPDATED_AT = null;

    public const TYPES = [
        'pay' => 'Перевод игроку',
        'trader_buy' => 'Покупка у торговца',
        'trader_sell' => 'Продажа торговцу',
        'admin_set' => 'Установка администратором',
        'admin_add' => 'Начисление администратором',
        'site_to_server' => 'С сайта на сервер',
        'server_to_site' => 'С сервера на сайт',
    ];

    protected $fillable = [
        'server_id', 'movement_id', 'type', 'amount', 'from_uuid', 'from_name', 'from_user_id',
        'to_uuid', 'to_name', 'to_user_id', 'note', 'occurred_at',
    ];

    protected $casts = [
        'amount' => 'integer',
        'movement_id' => 'integer',
        'occurred_at' => 'datetime',
    ];

    public function server(): BelongsTo
    {
        return $this->belongsTo(Server::class);
    }

    public function fromUser(): BelongsTo
    {
        return $this->belongsTo(User::class, 'from_user_id');
    }

    public function toUser(): BelongsTo
    {
        return $this->belongsTo(User::class, 'to_user_id');
    }

    public function scopeForUser(Builder $query, User $user): void
    {
        $query->where(fn (Builder $q) => $q->where('from_user_id', $user->id)->orWhere('to_user_id', $user->id));
    }

    public function typeLabel(): string
    {
        return self::TYPES[$this->type] ?? $this->type;
    }

    /** Signed amount from the point of view of the given user. */
    public function signedFor(User $user): int
    {
        if ($this->to_user_id === $user->id && $this->from_user_id !== $user->id) {
            return $this->amount;
        }

        return $this->from_user_id === $user->id ? -$this->amount : $this->amount;
    }

    public static function enabled(): bool
    {
        return (bool) setting('currency.server_movements_enabled', false);
    }
}
