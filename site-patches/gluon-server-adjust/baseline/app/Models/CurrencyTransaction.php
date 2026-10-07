<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * @property int $id
 * @property int $user_id
 * @property string $currency
 * @property float $amount
 * @property float $balance_after
 * @property string $type
 * @property int|null $server_id
 * @property string|null $idempotency_key
 * @property array|null $meta
 * @property \Carbon\Carbon $created_at
 * @property \Azuriom\Models\User $user
 * @property \Azuriom\Models\Server|null $server
 * @property \Azuriom\Models\User|null $admin
 * @property \Azuriom\Models\User|null $referralDetailUser
 */
class CurrencyTransaction extends Model
{
    public const UPDATED_AT = null;

    public ?string $resolvedPurchaseLabel = null;

    public const CURRENCY_MONEY = 'money';

    public const CURRENCY_COINS = 'coins';

    public const TYPE_PURCHASE = 'purchase';

    public const TYPE_TOPUP = 'topup';

    public const TYPE_CONVERT = 'convert';

    public const TYPE_TRANSFER = 'transfer';

    public const TYPE_SERVER_EARN = 'server_earn';

    public const TYPE_SERVER_SPEND = 'server_spend';

    public const TYPE_WITHDRAW = 'withdraw';

    public const TYPE_DEPOSIT = 'deposit';

    public const TYPE_ADMIN = 'admin';

    /**
     * @var array<int, string>
     */
    protected $fillable = [
        'user_id', 'currency', 'amount', 'balance_after', 'type',
        'server_id', 'idempotency_key', 'meta',
    ];

    /**
     * @var array<string, string>
     */
    protected $casts = [
        'amount' => 'float',
        'balance_after' => 'float',
        'meta' => 'array',
        'created_at' => 'datetime',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function server(): BelongsTo
    {
        return $this->belongsTo(Server::class);
    }

    public function typeLabel(): string
    {
        $source = $this->meta['source'] ?? null;

        if ($this->type === self::TYPE_ADMIN && $source === 'referral') {
            return trans('admin.currency_transactions.source_types.referral');
        }

        if ($this->type === self::TYPE_ADMIN && $source === 'battlepass') {
            return trans('admin.currency_transactions.source_types.battlepass_reward');
        }

        $key = 'admin.currency_transactions.types.'.$this->type;
        $label = trans($key);

        return $label === $key ? $this->type : $label;
    }

    public function currencyLabel(): string
    {
        return match ($this->currency) {
            self::CURRENCY_MONEY => money_name(2),
            self::CURRENCY_COINS => coins_name(2),
            default => $this->currency,
        };
    }

    public function adminId(): ?int
    {
        $id = $this->meta['admin_id'] ?? null;

        return $id !== null && $id !== '' ? (int) $id : null;
    }

    public function shopPaymentId(): ?int
    {
        $id = $this->meta['payment_id'] ?? null;

        return $id !== null && $id !== '' ? (int) $id : null;
    }

    public function adminReason(): ?string
    {
        $reason = $this->meta['reason'] ?? null;

        if (! is_string($reason)) {
            return null;
        }

        $reason = trim($reason);

        return $reason === '' ? null : $reason;
    }

    public function isManualAdminGrant(): bool
    {
        if ($this->type !== self::TYPE_ADMIN) {
            return false;
        }

        $source = $this->meta['source'] ?? null;

        return $source === null
            || $source === 'admin_grant'
            || $source === 'admin_user_edit'
            || $this->adminId() !== null;
    }

    public function detailsLabel(): ?string
    {
        if ($this->type === self::TYPE_PURCHASE) {
            return $this->purchaseLabel();
        }

        if ($this->type !== self::TYPE_ADMIN) {
            return null;
        }

        $meta = $this->meta ?? [];
        $source = $meta['source'] ?? null;

        if ($source === 'referral') {
            if ($this->relationLoaded('referralDetailUser') && $this->referralDetailUser) {
                return $this->referralDetailUser->name;
            }

            return null;
        }

        if ($source === 'battlepass') {
            return trans('admin.currency_transactions.battlepass');
        }

        return $this->adminReason();
    }

    public function referralPayoutId(): ?int
    {
        if (($this->meta['source'] ?? null) !== 'referral') {
            return null;
        }

        $id = $this->meta['payout_id'] ?? null;

        return $id !== null && $id !== '' ? (int) $id : null;
    }

    public function referralDetailUserId(): ?int
    {
        if (($this->meta['source'] ?? null) !== 'referral') {
            return null;
        }

        $meta = $this->meta ?? [];
        $role = (string) ($meta['role'] ?? '');

        // Percent to inviter → show invitee; bonus to invitee → show inviter.
        $id = $role === 'referrer_percent'
            ? ($meta['referred_id'] ?? null)
            : ($meta['referrer_id'] ?? null);

        return $id !== null && $id !== '' ? (int) $id : null;
    }

    public function needsPurchaseFallback(): bool
    {
        if ($this->type !== self::TYPE_PURCHASE) {
            return false;
        }

        $meta = $this->meta ?? [];

        return $this->formatPurchaseItems($meta) === null
            && empty($meta['package_name'])
            && ($meta['source'] ?? '') !== 'battlepass';
    }

    public function purchaseLabel(): ?string
    {
        if ($this->type !== self::TYPE_PURCHASE) {
            return null;
        }

        $meta = $this->meta ?? [];
        $parts = [];

        if (! empty($meta['reversal'])) {
            $parts[] = trans('admin.currency_transactions.reversal');
        }

        $items = $this->formatPurchaseItems($meta);

        if ($items !== null) {
            $parts[] = $items;
        } elseif (is_string($this->resolvedPurchaseLabel) && $this->resolvedPurchaseLabel !== '') {
            $parts[] = $this->resolvedPurchaseLabel;
        } elseif (! empty($meta['package_name'])) {
            $label = (string) $meta['package_name'];

            if (($meta['action'] ?? '') === 'subscription_renew') {
                $label .= ' ('.trans('admin.currency_transactions.subscription_renew').')';
            }

            $parts[] = $label;
        } elseif (($meta['source'] ?? '') === 'battlepass') {
            $parts[] = trans('admin.currency_transactions.battlepass');
        }

        return $parts === [] ? null : implode(' · ', $parts);
    }

    /**
     * @param  array<string, mixed>  $meta
     */
    private function formatPurchaseItems(array $meta): ?string
    {
        $items = $meta['items'] ?? null;

        if (! is_array($items) || $items === []) {
            return null;
        }

        $labels = [];

        foreach ($items as $item) {
            if (is_string($item) && trim($item) !== '') {
                $labels[] = trim($item);

                continue;
            }

            if (! is_array($item)) {
                continue;
            }

            $name = trim((string) ($item['name'] ?? ''));

            if ($name === '') {
                continue;
            }

            $quantity = (int) ($item['quantity'] ?? 1);
            $labels[] = $quantity > 1 ? $name.' ×'.$quantity : $name;
        }

        return $labels === [] ? null : implode(', ', $labels);
    }
}
