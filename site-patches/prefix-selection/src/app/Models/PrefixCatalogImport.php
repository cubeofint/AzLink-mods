<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Last accepted catalog export for one AzLink server.
 *
 * @property int $id
 * @property int $server_id
 * @property string|null $schema
 * @property string $hash
 * @property bool $full
 * @property int $group_count
 * @property \Carbon\Carbon $imported_at
 */
class PrefixCatalogImport extends Model
{
    protected $fillable = [
        'server_id', 'schema', 'hash', 'full', 'group_count', 'imported_at',
    ];

    protected $casts = [
        'full' => 'boolean',
        'group_count' => 'integer',
        'imported_at' => 'datetime',
    ];

    public function server(): BelongsTo
    {
        return $this->belongsTo(Server::class);
    }
}
