<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Collection;

/**
 * Technical catalog of LuckPerms prefix groups for one AzLink server.
 * Filled only by the game auto-export. Existing server_id NULL rows are leftovers.
 *
 * @property int $id
 * @property int|null $server_id
 * @property string $group
 * @property string $display_name
 * @property string|null $auto_display_name
 * @property string|null $prefix_raw
 * @property string|null $auto_prefix_raw
 * @property string $category
 * @property int $sort
 * @property int $auto_sort
 * @property int $priority
 * @property bool $enabled
 * @property \Carbon\Carbon|null $last_seen_at
 * @property \Carbon\Carbon|null $missing_since
 */
class PrefixCatalog extends Model
{
    public const CATEGORIES = ['quest', 'donate', 'staff', 'media', 'other'];

    public const CATEGORY_QUEST = 'quest';

    protected $table = 'prefix_catalog';

    protected $fillable = [
        'server_id', 'group', 'display_name', 'auto_display_name', 'prefix_raw',
        'auto_prefix_raw', 'category', 'sort', 'auto_sort', 'priority', 'enabled',
        'last_seen_at', 'missing_since',
    ];

    protected $casts = [
        'sort' => 'integer',
        'auto_sort' => 'integer',
        'priority' => 'integer',
        'enabled' => 'boolean',
        'last_seen_at' => 'datetime',
        'missing_since' => 'datetime',
    ];

    public function server(): BelongsTo
    {
        return $this->belongsTo(Server::class);
    }

    public function scopeEnabled(Builder $query): Builder
    {
        return $query->where('enabled', true);
    }

    public function visibleDisplayName(): string
    {
        return (string) ($this->auto_display_name ?: $this->display_name ?: $this->group);
    }

    public function visiblePrefixRaw(): ?string
    {
        return $this->auto_prefix_raw !== null && $this->auto_prefix_raw !== ''
            ? $this->auto_prefix_raw
            : $this->prefix_raw;
    }

    public function visibleSort(): int
    {
        return (int) ($this->auto_sort ?: $this->sort);
    }

    public static function categoryLabel(string $category): string
    {
        $key = 'prefixes.categories.'.$category;
        $label = trans($key);

        return $label === $key ? $category : $label;
    }

    public static function isQuestCategory(?string $category): bool
    {
        return strtolower((string) $category) === self::CATEGORY_QUEST;
    }

    /**
     * Server catalog only. NULL leftovers are ignored once that server has rows.
     *
     * @param  Collection<int, self>|null  $rows
     */
    public static function resolveForServer(int $serverId, string $group, ?Collection $rows = null): ?self
    {
        return static::mapForServer($serverId, $rows)->get($group);
    }

    /**
     * @return Collection<string, self> keyed by group
     */
    public static function mapForServer(int $serverId, ?Collection $all = null): Collection
    {
        $rows = $all ?? static::query()->where('server_id', $serverId)->get();
        $local = $rows->where('server_id', $serverId)->values();

        if ($local->isEmpty()) {
            return collect();
        }

        return $local->keyBy('group');
    }
}
