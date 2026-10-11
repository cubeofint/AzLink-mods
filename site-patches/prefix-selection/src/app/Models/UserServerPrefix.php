<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Per-(user, server) prefix snapshot reported by the game.
 *
 * @property int $id
 * @property int $user_id
 * @property int $server_id
 * @property array<int, array{group: string, prefix_raw?: string, category?: string}>|null $available
 * @property string|null $selected
 * @property bool $locked_staff
 * @property \Carbon\Carbon|null $synced_at
 */
class UserServerPrefix extends Model
{
    protected $fillable = [
        'user_id', 'server_id', 'available', 'selected', 'locked_staff', 'synced_at',
    ];

    protected $casts = [
        'available' => 'array',
        'locked_staff' => 'boolean',
        'synced_at' => 'datetime',
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
