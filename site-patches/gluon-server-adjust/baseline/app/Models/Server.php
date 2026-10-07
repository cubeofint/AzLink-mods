<?php

namespace Azuriom\Models;

use Azuriom\Games\FallbackServerBridge;
use Azuriom\Games\ServerBridge;
use Azuriom\Models\Traits\Loggable;
use Azuriom\Plugin\Shop\Models\Category;
use Azuriom\Plugin\Shop\Models\Kit;
use Azuriom\Plugin\Shop\Models\PrivilegeAddon;
use Azuriom\Plugin\Shop\Models\PrivilegeServerConfig;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

/**
 * @property int $id
 * @property string $name
 * @property string $address
 * @property int|null $port
 * @property int $position
 * @property string|null $minecraft_version
 * @property string|null $sborka_version
 * @property string|null $type_sborki
 * @property string|null $data_wipe
 * @property string|null $slug
 * @property bool $page_enabled
 * @property string|null $page_description
 * @property array|null $page_data
 * @property string $type
 * @property string|null $token
 * @property string|null $join_url
 * @property array $data
 * @property bool $home_display
 * @property \Carbon\Carbon $created_at
 * @property \Carbon\Carbon $updated_at
 * @property \Azuriom\Models\ServerStat $stat
 * @property \Illuminate\Support\Collection|\Azuriom\Models\ServerStat[] $stats
 * @property \Illuminate\Support\Collection|\Azuriom\Models\ServerCommand[] $commands
 *
 * @method static \Illuminate\Database\Eloquent\Builder executable()
 * @method static \Illuminate\Database\Eloquent\Builder pingable()
 */
class Server extends Model
{
    use Loggable;

    /**
     * The attributes that are mass assignable.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'name', 'slug', 'address', 'port', 'position', 'token', 'join_url', 'type', 'data', 'home_display', 'shop_enabled',
        'minecraft_version', 'sborka_version', 'type_sborki', 'data_wipe', 'page_enabled', 'page_description', 'page_data',
    ];

    /**
     * The attributes that should be hidden for arrays.
     *
     * @var array<int, string>
     */
    protected $hidden = [
        'token',
    ];

    /**
     * The attributes that should be cast to native types.
     *
     * @var array<string, string>
     */
    protected $casts = [
        'data' => 'array',
        'home_display' => 'bool',
        'shop_enabled' => 'bool',
        'page_enabled' => 'bool',
        'page_data' => 'array',
    ];

    protected static function booted(): void
    {
        static::created(function (self $server) {
            if (! Schema::hasTable('shop_categories')) {
                return;
            }

            $slug = Str::slug($server->name);
            $base = $slug;
            $i = 1;

            while (\DB::table('shop_categories')->where('slug', $slug)->exists()) {
                $slug = $base.'-'.$i++;
            }

            \DB::table('shop_categories')->insert([
                'name' => $server->name,
                'slug' => $slug,
                'position' => \DB::table('shop_categories')->whereNull('parent_id')->max('position') + 1,
                'parent_id' => null,
                'server_id' => $server->id,
                'is_enabled' => false,
                'cumulate_purchases' => false,
                'created_at' => now(),
                'updated_at' => now(),
            ]);
        });

        static::deleted(function (self $server) {
            if (((int) setting('servers.default')) === $server->id) {
                Setting::updateSettings(['servers.default' => null]);
            }
        });

        static::saving(function (self $server) {
            if (blank($server->slug) && filled($server->name)) {
                $server->slug = static::makeUniqueSlug($server->name, $server->id);
            }
        });
    }

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    /**
     * @return array<int, array{label: string, value: string, icon: string, highlight: bool}>
     */
    public function pageCharacteristics(): array
    {
        $custom = Arr::get($this->page_data, 'characteristics', []);

        if (! empty($custom)) {
            return $custom;
        }

        return $this->defaultPageCharacteristics();
    }

    /**
     * Mod list configured for the public server page.
     *
     * @return array<int, array{name: string, version: string, category: string}>
     */
    public function pageMods(): array
    {
        $mods = Arr::get($this->page_data, 'mods', []);

        if (! is_array($mods)) {
            return [];
        }

        return collect($mods)
            ->map(function ($mod) {
                if (! is_array($mod)) {
                    return null;
                }

                $name = trim((string) ($mod['name'] ?? ''));

                if ($name === '') {
                    return null;
                }

                return [
                    'name' => $name,
                    'version' => trim((string) ($mod['version'] ?? '')),
                    'category' => trim((string) ($mod['category'] ?? '')),
                ];
            })
            ->filter()
            ->values()
            ->all();
    }

    /**
     * Unique mod categories in display order (first appearance).
     *
     * @return array<int, string>
     */
    public function pageModCategories(): array
    {
        return collect($this->pageMods())
            ->pluck('category')
            ->map(fn ($category) => trim((string) $category))
            ->filter()
            ->unique(fn (string $category) => mb_strtolower($category))
            ->values()
            ->all();
    }

    /**
     * Bootstrap icon for a mod category chip.
     */
    public static function modCategoryIcon(string $category): string
    {
        $key = mb_strtolower(trim($category));

        $map = [
            'технологии' => 'bi-cpu',
            'technology' => 'bi-cpu',
            'tech' => 'bi-cpu',
            'магия' => 'bi-stars',
            'magic' => 'bi-stars',
            'приключения' => 'bi-controller',
            'adventure' => 'bi-controller',
            'rpg' => 'bi-controller',
            'хранение' => 'bi-box-seam',
            'storage' => 'bi-box-seam',
            'мир' => 'bi-globe2',
            'world gen' => 'bi-globe2',
            'генерация' => 'bi-globe2',
            'косметика' => 'bi-palette',
            'cosmetic' => 'bi-palette',
            'утилиты' => 'bi-tools',
            'utility' => 'bi-tools',
            'библиотеки' => 'bi-collection',
            'library' => 'bi-collection',
            'библиотека' => 'bi-collection',
            'аддоны' => 'bi-puzzle',
            'addons' => 'bi-puzzle',
            'еда' => 'bi-egg-fried',
            'food' => 'bi-egg-fried',
            'мобы' => 'bi-bug',
            'mobs' => 'bi-bug',
            'транспорт' => 'bi-truck',
            'transport' => 'bi-truck',
        ];

        foreach ($map as $needle => $icon) {
            if ($key === $needle || str_contains($key, $needle)) {
                return $icon;
            }
        }

        return 'bi-tag';
    }

    /**
     * Staff members assigned to this server for the public team tab.
     *
     * @return \Illuminate\Support\Collection<int, \Azuriom\Models\User>
     */
    public function staffMembers()
    {
        return User::query()
            ->with('role')
            ->whereNull('users.deleted_at')
            ->where('users.role_id', '!=', Role::defaultRoleId())
            ->whereExists(function ($query) {
                $query->selectRaw('1')
                    ->from('user_staff_servers')
                    ->whereColumn('user_staff_servers.user_id', 'users.id')
                    ->where('user_staff_servers.server_id', $this->id);
            })
            ->join('roles', 'roles.id', '=', 'users.role_id')
            ->orderByDesc('roles.power')
            ->orderBy('users.name')
            ->select('users.*')
            ->get();
    }

    /**
     * @return array<int, array{label: string, value: string, icon: string, highlight: bool}>
     */
    public function defaultPageCharacteristics(): array
    {
        $items = [];

        if (filled($this->minecraft_version)) {
            $items[] = [
                'label' => 'Версия игры',
                'value' => $this->minecraft_version,
                'icon' => 'bi-sliders',
                'highlight' => false,
            ];
        }

        if (filled($this->type_sborki)) {
            $items[] = [
                'label' => 'Тип сборки',
                'value' => $this->type_sborki,
                'icon' => 'bi-tags',
                'highlight' => false,
            ];
        }

        if (filled($this->sborka_version)) {
            $items[] = [
                'label' => 'Версия сборки',
                'value' => $this->sborka_version,
                'icon' => 'bi-box-seam',
                'highlight' => false,
            ];
        }

        if (filled($this->data_wipe)) {
            $items[] = [
                'label' => 'Дата вайпа',
                'value' => $this->data_wipe,
                'icon' => 'bi-arrow-repeat',
                'highlight' => true,
            ];
        }

        return $items;
    }

    /**
     * Live / recent AzLink metrics for the public server page.
     *
     * @return array<int, array{label: string, value: string, icon: string, highlight: bool}>
     */
    public function publicStatusMetrics(): array
    {
        $live = $this->getData();
        $stat = $this->stats()->latest('id')->first();
        $statMeta = is_array($stat?->data) ? $stat->data : [];

        if ($live === null && $stat === null) {
            return [];
        }

        $live = is_array($live) ? $live : [];
        $online = $live !== [];
        $players = (int) ($live['players'] ?? $stat?->players ?? 0);
        $maxPlayers = (int) ($live['max_players'] ?? 0);
        // Prefer the latest value received from the server (cache), not a delayed DB snapshot.
        $tps = array_key_exists('tps', $live)
            ? $live['tps']
            : ($statMeta['tps'] ?? null);

        $items = [
            [
                'label' => trans('messages.server.status_block.status'),
                'value' => $online
                    ? trans('messages.server.status_block.online')
                    : trans('messages.server.status_block.offline'),
                'icon' => $online ? 'bi-broadcast' : 'bi-broadcast-pin',
                'highlight' => $online,
            ],
        ];

        if ($maxPlayers > 0 || $players > 0) {
            $playersItem = [
                'label' => trans('messages.server.status_block.players'),
                'value' => $maxPlayers > 0
                    ? $players.'/'.$maxPlayers
                    : (string) $players,
                'icon' => 'bi-people',
                'highlight' => false,
            ];

            if ($this->page_enabled && filled($this->slug)) {
                $playersItem['href'] = route('servers.players', $this);
            }

            $items[] = $playersItem;
        }

        if ($tps !== null && $tps !== '') {
            $items[] = [
                'label' => trans('messages.server.status_block.tps'),
                'value' => number_format((float) $tps, 2, '.', ' '),
                'icon' => 'bi-speedometer2',
                'highlight' => (float) $tps >= 19.5,
            ];
        }

        if ($stat?->created_at !== null && ! $online) {
            $items[] = [
                'label' => trans('messages.server.status_block.updated_at'),
                'value' => format_date_compact($stat->created_at),
                'icon' => 'bi-clock-history',
                'highlight' => false,
            ];
        }

        return $items;
    }

    /**
     * @return array<string, array{title: string, icon: string, content: string, enabled: bool, href?: string|null}>
     */
    public function pageSections(bool $onlyEnabled = true): array
    {
        $defaults = [
            'economy' => ['title' => 'Экономика', 'icon' => 'bi-currency-dollar'],
            'commands' => ['title' => 'Команды', 'icon' => 'bi-terminal'],
            'restrictions' => ['title' => 'Ограничения', 'icon' => 'bi-exclamation-diamond'],
            'kits' => ['title' => 'Донат', 'icon' => 'bi-cart3'],
        ];

        $stored = Arr::get($this->page_data, 'sections', []);
        $sections = [];

        foreach ($defaults as $key => $meta) {
            $content = trim((string) Arr::get($stored, "{$key}.content", ''));
            $hasContent = $this->hasMeaningfulHtml($content);
            $enabled = Arr::has($stored, "{$key}.enabled")
                ? (bool) Arr::get($stored, "{$key}.enabled")
                : $hasContent;

            // Donate (legacy key "kits") always follows the server shop — no manual toggle.
            $isDonateLink = $key === 'kits';
            $shopUrl = $isDonateLink ? $this->shopUrl() : null;

            if ($isDonateLink) {
                $enabled = $shopUrl !== null;
            }

            if ($onlyEnabled) {
                if (! $enabled) {
                    continue;
                }

                if ($isDonateLink) {
                    // already requires $shopUrl above
                } elseif (! $hasContent) {
                    continue;
                }
            }

            $section = [
                'title' => $isDonateLink
                    ? 'Донат'
                    : Arr::get($stored, "{$key}.title", $meta['title']),
                'icon' => $isDonateLink
                    ? 'bi-cart3'
                    : Arr::get($stored, "{$key}.icon", $meta['icon']),
                'content' => $isDonateLink ? '' : $content,
                'enabled' => $enabled,
            ];

            if ($isDonateLink) {
                $section['href'] = $shopUrl;
            }

            $sections[$key] = $section;
        }

        return $sections;
    }

    /**
     * Public shop URL for this server's root category, if any.
     */
    public function shopUrl(): ?string
    {
        if (! $this->shop_enabled || ! Schema::hasTable('shop_categories')) {
            return null;
        }

        $category = Category::query()
            ->where('server_id', $this->id)
            ->whereNull('parent_id')
            ->orderBy('position')
            ->first();

        if ($category === null) {
            return null;
        }

        return route('shop.categories.show', $category);
    }

    public function pageFooterNote(bool $onlyEnabled = true): ?string
    {
        $footer = Arr::get($this->page_data, 'footer_note');

        if (is_array($footer)) {
            $note = trim((string) Arr::get($footer, 'content', ''));
            $enabled = Arr::has($footer, 'enabled')
                ? (bool) Arr::get($footer, 'enabled')
                : $this->hasMeaningfulHtml($note);
        } else {
            $note = trim((string) ($footer ?? ''));
            $enabled = $this->hasMeaningfulHtml($note);
        }

        if ($onlyEnabled && (! $enabled || ! $this->hasMeaningfulHtml($note))) {
            return null;
        }

        return $this->hasMeaningfulHtml($note) ? $note : null;
    }

    public function isPageFooterNoteEnabled(): bool
    {
        $footer = Arr::get($this->page_data, 'footer_note');

        if (is_array($footer)) {
            $note = trim((string) Arr::get($footer, 'content', ''));

            return Arr::has($footer, 'enabled')
                ? (bool) Arr::get($footer, 'enabled')
                : $this->hasMeaningfulHtml($note);
        }

        return $this->hasMeaningfulHtml(trim((string) ($footer ?? '')));
    }

    protected function hasMeaningfulHtml(string $content): bool
    {
        $plain = html_entity_decode(strip_tags($content), ENT_QUOTES | ENT_HTML5, 'UTF-8');
        $plain = str_replace("\xc2\xa0", ' ', $plain);

        return trim($plain) !== '';
    }

    public static function makeUniqueSlug(string $name, ?int $ignoreId = null): string
    {
        $base = Str::slug($name) ?: 'server';
        $slug = $base;
        $i = 1;

        while (static::query()
            ->where('slug', $slug)
            ->when($ignoreId, fn (Builder $query) => $query->where('id', '!=', $ignoreId))
            ->exists()) {
            $slug = $base.'-'.$i++;
        }

        return $slug;
    }

    public function stat()
    {
        return $this->hasOne(ServerStat::class)
            ->latest()
            ->where('created_at', '>', now()->subSeconds(65));
    }

    public function stats()
    {
        return $this->hasMany(ServerStat::class);
    }

    public function privilegeConfigurations()
    {
        return $this->hasMany(PrivilegeServerConfig::class);
    }

    public function kits()
    {
        return $this->hasMany(Kit::class);
    }

    public function privilegeAddons()
    {
        return $this->hasMany(PrivilegeAddon::class);
    }

    /**
     * Get the commands waiting to be dispatch on this server.
     *
     * Currently, this should only be use for servers using AzLink.
     */
    public function commands()
    {
        return $this->hasMany(ServerCommand::class);
    }

    public function fullAddress(): string
    {
        if ($this->port === null || $this->port === $this->bridge()->getDefaultPort()) {
            return $this->address;
        }

        return $this->address.':'.$this->port;
    }

    public function isOnline(): bool
    {
        return $this->getData() !== null;
    }

    public function getOnlinePlayers(): int
    {
        return $this->getData('players', 0);
    }

    /**
     * Online player names from the latest AzLink heartbeat.
     *
     * @return array<int, string>
     */
    public function onlinePlayerList(): array
    {
        $list = $this->getData('player_list', []);

        if (! is_array($list)) {
            return [];
        }

        return collect($list)
            ->map(fn ($name) => trim((string) $name))
            ->filter()
            ->unique()
            ->values()
            ->all();
    }

    public function getMaxPlayers(): int
    {
        return $this->getData('max_players', 0);
    }

    public function getPlayersPercents(): float
    {
        $max = $this->getMaxPlayers();

        if ($max <= 0) {
            return 100;
        }

        return min(($this->getOnlinePlayers() / $max) * 100, 100);
    }

    public function joinUrl(): ?string
    {
        return $this->join_url;
    }

    public function updateData(?array $data = null, bool $full = false): void
    {
        if ($data !== null) {
            $data = $this->mergeLiveServerData($data);

            // Keep the last known TPS/etc. in a longer-lived side cache so lightweight
            // heartbeats and page views cannot wipe metrics between full AzLink syncs.
            $metrics = Arr::only($data, ['tps', 'cpu', 'ram', 'chunks', 'entities']);
            if ($metrics !== []) {
                Cache::put('servers.'.$this->id.'.metrics', $metrics, now()->addHour());
            }

            if (array_key_exists('top_players', $data) && is_array($data['top_players'])) {
                Cache::put('servers.'.$this->id.'.top_players', [
                    'top_players' => $data['top_players'],
                    'top_players_label' => $data['top_players_label'] ?? null,
                ], now()->addHour());
            }
        }

        Cache::put('servers.'.$this->id, $data, now()->addMinutes(5));

        if ($data === null || ! $full) {
            return;
        }

        if ($this->stats()->where('created_at', '>=', now()->subMinutes(5))->exists()) {
            return;
        }

        $statsData = Arr::except($data, [
            'players', 'max_players', 'cpu', 'ram', 'player_list', 'top_players', 'top_players_label',
        ]);

        if (is_numeric($tps = Arr::get($statsData, 'tps'))) {
            $statsData['tps'] = round($tps, 2);
        }

        $this->stats()->create([
            ...Arr::only($data, ['players', 'cpu', 'ram']),
            'data' => array_filter($statsData),
        ]);

        // Invalidate record caches so the UI/API reflects new highs immediately.
        Cache::forget('servers.record.'.$this->id);
        Cache::forget('servers.daily_record.'.$this->id);
        Cache::forget('servers.total_record');
        Cache::forget('servers.total_daily_record');
    }

    /**
     * Merge a new AzLink/ping payload with the previous live cache and last known metrics.
     *
     * @param  array<string, mixed>  $data
     * @return array<string, mixed>
     */
    protected function mergeLiveServerData(array $data): array
    {
        $previous = Cache::get('servers.'.$this->id);
        $previous = is_array($previous) ? $previous : [];

        $metrics = Cache::get('servers.'.$this->id.'.metrics');
        if (is_array($metrics)) {
            $previous = array_merge($metrics, $previous);
        }

        $top = Cache::get('servers.'.$this->id.'.top_players');
        if (is_array($top)) {
            if (array_key_exists('top_players', $top) && $top['top_players'] !== null) {
                $previous['top_players'] = $top['top_players'];
            }
            if (! empty($top['top_players_label'])) {
                $previous['top_players_label'] = $top['top_players_label'];
            }
        }

        return array_merge($previous, $data);
    }

    /**
     * Leaderboard pushed by AzLink.
     *
     * @return array<int, array{name: string, uuid: ?string, score: mixed}>
     */
    public function topPlayers(): array
    {
        $list = $this->getData('top_players');

        if (! is_array($list) || $list === []) {
            $side = Cache::get('servers.'.$this->id.'.top_players');
            $list = is_array($side) ? ($side['top_players'] ?? []) : [];
        }

        if (! is_array($list)) {
            return [];
        }

        return collect($list)
            ->map(function ($item) {
                if (! is_array($item)) {
                    return null;
                }

                $name = trim((string) ($item['name'] ?? ''));

                if ($name === '') {
                    return null;
                }

                if (! array_key_exists('score', $item) || $item['score'] === null || $item['score'] === '') {
                    return null;
                }

                $uuid = isset($item['uuid']) ? trim((string) $item['uuid']) : '';

                return [
                    'name' => $name,
                    'uuid' => $uuid !== '' ? $uuid : null,
                    'score' => $item['score'],
                ];
            })
            ->filter()
            ->values()
            ->all();
    }

    public function hasPlayerStats(): bool
    {
        return ServerPlayerStat::query()
            ->where('server_id', $this->id)
            ->exists();
    }

    public function playerStats()
    {
        return $this->hasMany(ServerPlayerStat::class);
    }

    /**
     * Build ranked leaderboards from synced AzLink player stats.
     *
     * @return array<string, array{label: string, icon: string, players: list<array{rank: int, name: string, uuid: ?string, value: int, display: string, avatar: string}>}>
     */
    public function playerStatLeaderboards(int $limit = 50): array
    {
        $metrics = ServerPlayerStat::leaderboardMetrics();
        $stats = ServerPlayerStat::query()
            ->where('server_id', $this->id)
            ->with('user:id,name,avatar,game_id')
            ->get();

        if ($stats->isEmpty()) {
            return [];
        }

        $boards = [];

        foreach ($metrics as $key => $meta) {
            $ranked = $stats
                ->map(function (ServerPlayerStat $stat) use ($key) {
                    return [
                        'stat' => $stat,
                        'value' => $stat->rawValueForMetric($key),
                    ];
                })
                ->filter(fn (array $row) => $row['value'] > 0)
                ->sortByDesc('value')
                ->take($limit)
                ->values()
                ->map(function (array $row, int $index) use ($key) {
                    /** @var \Azuriom\Models\ServerPlayerStat $stat */
                    $stat = $row['stat'];

                    $avatar = $stat->user?->getAvatar(64)
                        ?? url('/api/skin-api/avatars/face/'.rawurlencode($stat->name));

                    return [
                        'rank' => $index + 1,
                        'name' => $stat->name,
                        'uuid' => $stat->uuid,
                        'value' => $row['value'],
                        'display' => $stat->displayValueForMetric($key),
                        'avatar' => $avatar,
                    ];
                })
                ->all();

            $boards[$key] = [
                'label' => $meta['label'],
                'icon' => $meta['icon'],
                'players' => $ranked,
            ];
        }

        return $boards;
    }

    public function topPlayersLabel(): string
    {
        $label = $this->getData('top_players_label');

        if (! is_string($label) || trim($label) === '') {
            $side = Cache::get('servers.'.$this->id.'.top_players');
            $label = is_array($side) ? ($side['top_players_label'] ?? null) : null;
        }

        $label = is_string($label) ? trim($label) : '';

        return $label !== '' ? $label : 'Топ';
    }

    public function getData(?string $key = null, mixed $default = null): mixed
    {
        $cacheKey = 'servers.'.$this->id;

        if (Cache::has($cacheKey)) {
            $data = Cache::get($cacheKey);
        } else {
            $data = $this->bridge()->getServerData();

            // AzLink is push-based and returns null here. Caching that null would
            // block mergeLiveServerData() until the next full payload.
            if ($data !== null) {
                Cache::put($cacheKey, $data, now()->addMinute());
            } else {
                $metrics = Cache::get('servers.'.$this->id.'.metrics');
                $data = is_array($metrics) ? $metrics : null;

                $top = Cache::get('servers.'.$this->id.'.top_players');
                if (is_array($top)) {
                    $data = is_array($data) ? $data : [];
                    if (array_key_exists('top_players', $top) && $top['top_players'] !== null) {
                        $data['top_players'] = $top['top_players'];
                    }
                    if (! empty($top['top_players_label'])) {
                        $data['top_players_label'] = $top['top_players_label'];
                    }
                }
            }
        }

        if ($key === null) {
            return $data;
        }

        return is_array($data) ? ($data[$key] ?? $default) : $default;
    }

    public function bridge(): ServerBridge
    {
        $games = game()->getSupportedServers();

        if (! array_key_exists($this->type, $games)) {
            return new FallbackServerBridge($this);
        }

        return app($games[$this->type], ['server' => $this]);
    }

    public function getLinkCommand(): string
    {
        $token = $this->plainLinkToken();

        if ($this->type === 'mc-azlink') {
            if ($token === null) {
                return '/azlink setup '.url('/');
            }

            return '/azlink setup '.url('/').' '.$token;
        }

        $base = match (game()->id()) {
            'gmod' => 'azlink:setup '.str_replace([':', '/'], ['!', '|'], url('/')),
            'csgo' => 'azlink_setup "'.url('/').'"',
            'rust', '7dtd' => 'azlink.setup '.url('/'),
            default => 'azlink setup '.url('/'),
        };

        return $token === null ? $base : $base.' '.$token;
    }

    public static function hashLinkToken(string $token): string
    {
        return hash_hmac('sha256', $token, (string) config('app.key'));
    }

    public static function tokenLooksHashed(?string $token): bool
    {
        return is_string($token) && preg_match('/^[a-f0-9]{64}$/', $token) === 1;
    }

    public static function findByLinkToken(?string $token): ?self
    {
        if ($token === null || $token === '') {
            return null;
        }

        $hashed = self::hashLinkToken($token);
        $server = self::query()->where('token', $hashed)->first();

        if ($server !== null) {
            return $server;
        }

        $legacy = self::query()->where('token', $token)->first();

        if ($legacy === null || self::tokenLooksHashed($legacy->token)) {
            return null;
        }

        $legacy->forceFill(['token' => $hashed])->save();

        return $legacy;
    }

    public function issueLinkToken(): string
    {
        $plain = Str::random(32);
        $this->forceFill(['token' => self::hashLinkToken($plain)])->save();
        Cache::put('azlink_plain_token.'.$this->id, $plain, now()->addMinutes(30));

        return $plain;
    }

    public function hasHashedLinkToken(): bool
    {
        return self::tokenLooksHashed($this->token);
    }

    public function plainLinkToken(): ?string
    {
        $cached = Cache::get('azlink_plain_token.'.$this->id);

        if (is_string($cached) && $cached !== '') {
            return $cached;
        }

        if ($this->token === null || $this->token === '' || $this->hasHashedLinkToken()) {
            return null;
        }

        return $this->token;
    }

    public function playersRecord(bool $force = false): int
    {
        $stored = $force
            ? (int) ($this->stats()->max('players') ?? 0)
            : Cache::remember('servers.record.'.$this->id, now()->addHour(), function () {
                return (int) ($this->stats()->max('players') ?? 0);
            });

        return $this->effectivePlayersRecord($stored);
    }

    /**
     * Get the maximum online players for today (since midnight) for this server.
     */
    public function dailyPlayersRecord(bool $force = false): int
    {
        $stored = $force
            ? (int) ($this->stats()
                ->whereDate('created_at', today())
                ->max('players') ?? 0)
            : Cache::remember('servers.daily_record.'.$this->id, now()->addMinutes(5), function () {
                return (int) ($this->stats()
                    ->whereDate('created_at', today())
                    ->max('players') ?? 0);
            });

        return $this->effectivePlayersRecord($stored);
    }

    /**
     * Record cannot be lower than the current live online count.
     */
    protected function effectivePlayersRecord(int $storedRecord): int
    {
        if (! $this->isOnline()) {
            return $storedRecord;
        }

        return max($storedRecord, $this->getOnlinePlayers());
    }

    /**
     * Get the sum of all-time player records across all servers.
     */
    public static function totalPlayersRecord(): int
    {
        return (int) Cache::remember('servers.total_record', now()->addHour(), function () {
            $stored = ServerStat::selectRaw('server_id, MAX(players) as max_players')
                ->groupBy('server_id')
                ->pluck('max_players', 'server_id');

            return Server::query()->get()->sum(function (Server $server) use ($stored) {
                return $server->effectivePlayersRecord((int) ($stored[$server->id] ?? 0));
            });
        });
    }

    /**
     * Get the sum of today's player records across all servers.
     */
    public static function totalDailyPlayersRecord(): int
    {
        return (int) Cache::remember('servers.total_daily_record', now()->addMinutes(5), function () {
            $stored = ServerStat::selectRaw('server_id, MAX(players) as max_players')
                ->whereDate('created_at', today())
                ->groupBy('server_id')
                ->pluck('max_players', 'server_id');

            return Server::query()->get()->sum(function (Server $server) use ($stored) {
                return $server->effectivePlayersRecord((int) ($stored[$server->id] ?? 0));
            });
        });
    }

    public static function types(): array
    {
        return array_keys(game()->getSupportedServers());
    }

    /**
     * Scope a query to only include servers which can execute commands.
     */
    #[Scope]
    protected function executable(Builder $query): void
    {
        $servers = collect(game()->getSupportedServers())->filter(function (string $bridge) {
            return (new $bridge($this))->canExecuteCommand();
        });

        $query->whereIn('type', $servers->keys());
    }

    /**
     * Scope a query to only include servers which can be pinged.
     */
    #[Scope]
    protected function pingable(Builder $query): void
    {
        $query->whereNotIn('type', ['mc-azlink', 'steam-azlink']);
    }
}
