<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Collection;

/**
 * Display metadata for a chat prefix group key, optionally scoped to a server.
 *
 * Visible display_name / sort / enabled follow an admin override when the
 * matching override_* flag is set; otherwise they track the last auto import.
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
 * @property string $source
 * @property bool $override_display_name
 * @property bool $override_sort
 * @property bool $override_enabled
 * @property \Carbon\Carbon|null $last_seen_at
 * @property \Carbon\Carbon|null $missing_since
 */
class PrefixCatalog extends Model
{
    public const CATEGORIES = ['quest', 'donate', 'staff', 'media', 'other'];

    public const CATEGORY_QUEST = 'quest';

    public const SOURCE_AUTO = 'auto';

    public const SOURCE_MANUAL = 'manual';

    protected $table = 'prefix_catalog';

    protected $fillable = [
        'server_id', 'group', 'display_name', 'auto_display_name', 'prefix_raw',
        'auto_prefix_raw', 'category', 'sort', 'auto_sort', 'priority', 'enabled',
        'source', 'override_display_name', 'override_sort', 'override_enabled',
        'last_seen_at', 'missing_since',
    ];

    protected $casts = [
        'sort' => 'integer',
        'auto_sort' => 'integer',
        'priority' => 'integer',
        'enabled' => 'boolean',
        'override_display_name' => 'boolean',
        'override_sort' => 'boolean',
        'override_enabled' => 'boolean',
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

    public function isAuto(): bool
    {
        return $this->source === self::SOURCE_AUTO;
    }

    public function visibleDisplayName(): string
    {
        if ($this->override_display_name && filled($this->display_name)) {
            return (string) $this->display_name;
        }

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
        return $this->override_sort ? (int) $this->sort : (int) ($this->auto_sort ?: $this->sort);
    }

    public function visibleEnabled(): bool
    {
        return $this->override_enabled ? (bool) $this->enabled : (bool) $this->enabled;
    }

    public function resetToAuto(): void
    {
        $this->override_display_name = false;
        $this->override_sort = false;
        $this->override_enabled = false;
        $this->display_name = $this->auto_display_name ?: $this->display_name;
        $this->prefix_raw = $this->auto_prefix_raw ?: $this->prefix_raw;
        $this->sort = (int) $this->auto_sort;
        $this->enabled = $this->missing_since === null;
        $this->save();
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
     * Server-specific row wins; fall back to the global (server_id NULL) row.
     *
     * @param  Collection<int, self>|null  $rows
     */
    public static function resolveForServer(int $serverId, string $group, ?Collection $rows = null): ?self
    {
        $pool = $rows ?? static::query()->where('group', $group)
            ->where(function (Builder $query) use ($serverId) {
                $query->where('server_id', $serverId)->orWhereNull('server_id');
            })
            ->get();

        return $pool->first(fn (self $row) => (int) $row->server_id === $serverId && $row->group === $group)
            ?? $pool->first(fn (self $row) => $row->server_id === null && $row->group === $group);
    }

    /**
     * @return Collection<string, self> keyed by group
     */
    public static function mapForServer(int $serverId, ?Collection $all = null): Collection
    {
        $rows = $all ?? static::query()
            ->where(function (Builder $query) use ($serverId) {
                $query->where('server_id', $serverId)->orWhereNull('server_id');
            })
            ->get();

        $map = collect();
        foreach ($rows->whereNull('server_id') as $row) {
            $map->put($row->group, $row);
        }
        foreach ($rows->where('server_id', $serverId) as $row) {
            $map->put($row->group, $row);
        }

        return $map;
    }
}
