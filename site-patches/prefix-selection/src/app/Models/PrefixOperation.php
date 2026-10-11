<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Site → game prefix change. Not the gluon queue.
 *
 * @property string $id
 * @property int $user_id
 * @property int $server_id
 * @property string $type
 * @property string|null $group
 * @property string $status
 * @property int $deliveries
 * @property \Carbon\Carbon|null $leased_until
 * @property string|null $reason
 * @property string|null $selected
 * @property string|null $source
 * @property \Carbon\Carbon|null $applied_at
 */
class PrefixOperation extends Model
{
    public const TYPE_SELECT = 'prefix_select';

    public const PENDING = 'pending';

    public const APPLIED = 'applied';

    public const REJECTED = 'rejected';

    public $incrementing = false;

    protected $keyType = 'string';

    protected $fillable = [
        'id', 'user_id', 'server_id', 'type', 'group', 'status', 'deliveries',
        'leased_until', 'reason', 'selected', 'source', 'applied_at',
    ];

    protected $casts = [
        'deliveries' => 'integer',
        'leased_until' => 'datetime',
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

    public function isPending(): bool
    {
        return $this->status === self::PENDING;
    }

    public function statusLabel(): string
    {
        $key = 'prefixes.status.'.$this->status;
        $label = trans($key);

        return $label === $key ? $this->status : $label;
    }
}
