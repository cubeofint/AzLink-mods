<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * @property int $id
 * @property int $user_id
 * @property int $server_id
 * @property float $balance
 * @property \Carbon\Carbon $created_at
 * @property \Carbon\Carbon $updated_at
 * @property \Azuriom\Models\User $user
 * @property \Azuriom\Models\Server $server
 */
class UserServerBalance extends Model
{
    /**
     * @var array<int, string>
     */
    protected $fillable = [
        'user_id', 'server_id', 'balance',
    ];

    /**
     * @var array<string, string>
     */
    protected $casts = [
        'balance' => 'float',
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
