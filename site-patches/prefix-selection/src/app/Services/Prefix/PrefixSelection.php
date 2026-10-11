<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixOperation;
use Azuriom\Models\Server;
use Azuriom\Models\User;
use Azuriom\Models\UserServerPrefix;
use Illuminate\Support\Collection;
use RuntimeException;

/**
 * Cabinet / admin view-model and the rules for creating a prefix_select op.
 *
 * Only quest-category groups are selectable. Donate / staff / media stay in game
 * and are not part of the cabinet list.
 */
class PrefixSelection
{
    public function __construct(
        private PrefixFormatter $formatter,
        private PrefixOperationQueue $queue,
    ) {
    }

    public static function enabled(): bool
    {
        return (bool) setting('prefixes.enabled', false);
    }

    /**
     * @return Collection<int, array<string, mixed>>
     */
    public function cabinetServers(User $user): Collection
    {
        $rows = UserServerPrefix::query()
            ->where('user_id', $user->id)
            ->with('server:id,name,type,position')
            ->get()
            ->keyBy('server_id');

        $serverIds = $rows->keys();
        $azlinkIds = Server::query()
            ->where('type', 'mc-azlink')
            ->orderBy('position')
            ->orderBy('name')
            ->pluck('id');

        $ids = $azlinkIds->merge($serverIds)->unique()->values();
        $servers = Server::query()
            ->whereIn('id', $ids)
            ->orderBy('position')
            ->orderBy('name')
            ->get();

        $catalogRows = PrefixCatalog::query()
            ->where(function ($query) use ($ids) {
                $query->whereNull('server_id')->orWhereIn('server_id', $ids);
            })
            ->get();

        $pending = PrefixOperation::query()
            ->where('user_id', $user->id)
            ->where('status', PrefixOperation::PENDING)
            ->get()
            ->keyBy('server_id');
        $latest = PrefixOperation::query()
            ->where('user_id', $user->id)
            ->orderByDesc('created_at')
            ->get()
            ->unique('server_id')
            ->keyBy('server_id');

        return $servers->map(function (Server $server) use ($rows, $catalogRows, $pending, $latest) {
            $row = $rows->get($server->id);
            $catalog = PrefixCatalog::mapForServer($server->id, $catalogRows);

            return [
                'server' => $server,
                'row' => $row,
                'locked_staff' => (bool) ($row?->locked_staff),
                'selected' => $row?->selected,
                'options' => $row === null ? collect() : $this->visibleOptions($row, $catalog),
                'pending' => $pending->get($server->id),
                'latest' => $latest->get($server->id),
                'synced_at' => $row?->synced_at,
            ];
        });
    }

    /**
     * Quest groups only. Server catalog, then server_id NULL, then the player report.
     *
     * @param  Collection<string, PrefixCatalog>  $catalog
     * @return Collection<int, array<string, mixed>>
     */
    public function visibleOptions(UserServerPrefix $row, Collection $catalog): Collection
    {
        $options = collect();

        foreach ($row->available ?? [] as $item) {
            $group = (string) ($item['group'] ?? '');
            if ($group === '') {
                continue;
            }

            $entry = $catalog->get($group);
            $category = $entry?->category ?: (string) ($item['category'] ?? 'other');
            if (! PrefixCatalog::isQuestCategory($category)) {
                continue;
            }

            if ($entry !== null && ! $entry->enabled) {
                continue;
            }

            $raw = $entry !== null
                ? (string) ($entry->visiblePrefixRaw() ?: ($item['prefix_raw'] ?? ''))
                : (string) ($item['prefix_raw'] ?? '');

            $options->push([
                'group' => $group,
                'display_name' => $entry !== null ? $entry->visibleDisplayName() : $group,
                'prefix_raw' => $raw,
                'preview' => $this->formatter->toHtml($raw),
                'category' => $category,
                'category_label' => PrefixCatalog::categoryLabel($category),
                'sort' => $entry !== null ? $entry->visibleSort() : 1000,
                'in_catalog' => $entry !== null,
            ]);
        }

        return $options->sortBy([
            ['sort', 'asc'],
            ['display_name', 'asc'],
        ])->values();
    }

    public function select(User $user, int $serverId, ?string $group, string $source = 'profile'): PrefixOperation
    {
        $row = UserServerPrefix::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->first();

        if ($row === null) {
            throw new RuntimeException('prefix_not_synced');
        }

        if ($group !== null && $group !== '') {
            $available = collect($row->available ?? []);
            $reported = $available->firstWhere('group', $group);
            if ($reported === null) {
                throw new RuntimeException('prefix_not_available');
            }

            $catalog = PrefixCatalog::resolveForServer($serverId, $group);
            $category = $catalog?->category ?: (string) ($reported['category'] ?? 'other');
            if (! PrefixCatalog::isQuestCategory($category)) {
                throw new RuntimeException('prefix_not_available');
            }

            if ($catalog !== null && ! $catalog->enabled) {
                throw new RuntimeException('prefix_disabled');
            }
        } else {
            $group = null;
        }

        if ($row->selected === $group && ! PrefixOperation::query()
            ->where('user_id', $user->id)
            ->where('server_id', $serverId)
            ->where('status', PrefixOperation::PENDING)
            ->exists()) {
            throw new RuntimeException('prefix_already_selected');
        }

        return $this->queue->enqueue($user, $serverId, $group, ['source' => $source]);
    }

    public function preview(?string $raw): string
    {
        return $this->formatter->toHtml($raw);
    }
}
