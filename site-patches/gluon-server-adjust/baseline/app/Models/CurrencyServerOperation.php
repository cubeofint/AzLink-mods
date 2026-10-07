<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Queued gluon transfer between the shared site wallet and one game server wallet.
 *
 * @property string $id
 * @property int $user_id
 * @property int $server_id
 * @property string $direction
 * @property string $amount
 * @property string $status
 * @property int $deliveries
 * @property string|null $error
 * @property array|null $meta
 * @property \Carbon\Carbon|null $applied_at
 */
class CurrencyServerOperation extends Model
{
    public const TO_SERVER = 'to_server';

    public const FROM_SERVER = 'from_server';

    public const PENDING = 'pending';

    public const APPLIED = 'applied';

    public const FAILED = 'failed';

    public $incrementing = false;

    protected $keyType = 'string';

    protected $fillable = [
        'id', 'user_id', 'server_id', 'direction', 'amount', 'status', 'deliveries', 'error', 'meta', 'applied_at',
    ];

    protected $casts = [
        'meta' => 'array',
        'applied_at' => 'datetime',
    ];

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function server(): BelongsTo
    {
        return $this->belongsTo(Server::class);
    }
}
