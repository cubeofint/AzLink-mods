<?php

namespace Tests\Feature;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\Server;
use Azuriom\Models\Setting;
use Azuriom\Models\User;
use Azuriom\Models\UserServerPrefix;
use Azuriom\Services\Prefix\PrefixCatalogImporter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Requires a booted Azuriom app (phpunit.xml + sqlite).
 *
 *   cd /var/www/main-site
 *   php artisan test --filter=PrefixCatalogImportTest
 */
class PrefixCatalogImportTest extends TestCase
{
    use RefreshDatabase;

    private Server $server;

    protected function setUp(): void
    {
        parent::setUp();

        $this->server = Server::query()->create([
            'name' => 'ATM10-Test',
            'address' => '127.0.0.1',
            'type' => 'mc-azlink',
            'port' => 25565,
            'data' => [],
        ]);

        Setting::updateSettings(['prefixes.enabled' => '1']);
    }

    public function test_import_overwrites_visible_fields_from_luckperms(): void
    {
        PrefixCatalog::query()->create([
            'server_id' => $this->server->id,
            'group' => 'miner',
            'display_name' => 'Шахтёр (старое)',
            'auto_display_name' => 'old',
            'prefix_raw' => '&a[old]',
            'auto_prefix_raw' => '&a[old]',
            'category' => 'quest',
            'sort' => 7,
            'auto_sort' => 1,
            'priority' => 0,
            'enabled' => false,
        ]);

        $result = $this->importer()->import($this->server->id, $this->payload('h1', [
            $this->group('miner', '&a[Miner]', 3, 10, 'Miner', 'quest'),
        ]));

        $this->assertFalse($result['unchanged']);
        $row = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first();
        $this->assertSame('Miner', $row->display_name);
        $this->assertSame('Miner', $row->auto_display_name);
        $this->assertSame(10, $row->sort);
        $this->assertSame(10, $row->auto_sort);
        $this->assertTrue($row->enabled);
        $this->assertSame('&a[Miner]', $row->prefix_raw);
        $this->assertSame('&a[Miner]', $row->auto_prefix_raw);
        $this->assertSame('Miner', $row->visibleDisplayName());
        $this->assertSame(10, $row->visibleSort());
    }

    public function test_missing_groups_from_full_export_are_deleted(): void
    {
        $this->importer()->import($this->server->id, $this->payload('h-full', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        $second = $this->importer()->import($this->server->id, $this->payload('h-full-2', [
            $this->group('miner', '&a[M2]', 1, 1, 'Miner', 'quest'),
        ]));

        $this->assertSame(1, $second['deleted']);
        $this->assertNull(PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'farmer')->first());
        $miner = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first();
        $this->assertNotNull($miner);
        $this->assertTrue($miner->enabled);
        $this->assertSame('&a[M2]', $miner->prefix_raw);
        $this->assertSame(1, PrefixCatalog::query()->where('server_id', $this->server->id)->count());
    }

    public function test_full_export_clears_player_selected_and_available(): void
    {
        $other = Server::query()->create([
            'name' => 'Other',
            'address' => '127.0.0.1',
            'type' => 'mc-azlink',
            'port' => 25566,
            'data' => [],
        ]);
        $user = User::factory()->create([
            'name' => 'Nick',
            'game_id' => 'f3fc162dd34432fa8c9a0b987b0791cf',
        ]);

        $this->importer()->import($this->server->id, $this->payload('seed', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        UserServerPrefix::query()->create([
            'user_id' => $user->id,
            'server_id' => $this->server->id,
            'available' => [
                ['group' => 'miner', 'prefix_raw' => '&a[M]', 'category' => 'quest'],
                ['group' => 'farmer', 'prefix_raw' => '&e[F]', 'category' => 'quest'],
            ],
            'selected' => 'farmer',
            'synced_at' => now(),
        ]);
        UserServerPrefix::query()->create([
            'user_id' => $user->id,
            'server_id' => $other->id,
            'available' => [
                ['group' => 'farmer', 'prefix_raw' => '&e[F]', 'category' => 'quest'],
            ],
            'selected' => 'farmer',
            'synced_at' => now(),
        ]);

        $this->importer()->import($this->server->id, $this->payload('wipe-farmer', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
        ]));

        $row = UserServerPrefix::query()
            ->where('user_id', $user->id)
            ->where('server_id', $this->server->id)
            ->first();
        $this->assertNull($row->selected);
        $this->assertSame(['miner'], collect($row->available)->pluck('group')->all());

        $otherRow = UserServerPrefix::query()
            ->where('user_id', $user->id)
            ->where('server_id', $other->id)
            ->first();
        $this->assertSame('farmer', $otherRow->selected);
        $this->assertSame(['farmer'], collect($otherRow->available)->pluck('group')->all());
    }

    public function test_repeat_with_same_hash_is_idempotent(): void
    {
        $payload = $this->payload('same-hash', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
        ]);

        $first = $this->importer()->import($this->server->id, $payload);
        $this->assertFalse($first['unchanged']);
        $this->assertSame(1, $first['created']);

        PrefixCatalog::query()->where('group', 'miner')->update([
            'display_name' => 'touched',
        ]);

        $second = $this->importer()->import($this->server->id, $payload);
        $this->assertTrue($second['unchanged']);
        $this->assertSame(1, PrefixCatalog::query()->count());
        $this->assertSame('touched', PrefixCatalog::query()->where('group', 'miner')->value('display_name'));
        $this->assertSame(1, PrefixCatalogImport::query()->where('server_id', $this->server->id)->count());
    }

    public function test_empty_full_export_wipes_server_catalog(): void
    {
        $this->importer()->import($this->server->id, $this->payload('seed', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        $user = User::factory()->create([
            'name' => 'Nick',
            'game_id' => 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        ]);
        UserServerPrefix::query()->create([
            'user_id' => $user->id,
            'server_id' => $this->server->id,
            'available' => [
                ['group' => 'miner', 'prefix_raw' => '&a[M]', 'category' => 'quest'],
                ['group' => 'farmer', 'prefix_raw' => '&e[F]', 'category' => 'quest'],
            ],
            'selected' => 'miner',
            'synced_at' => now(),
        ]);

        $empty = $this->importer()->import($this->server->id, [
            'schema' => 1,
            'hash' => 'empty-full',
            'full' => true,
            'groups' => [],
        ]);
        $this->assertFalse($empty['unchanged']);
        $this->assertSame(2, $empty['deleted']);
        $this->assertSame(0, PrefixCatalog::query()->where('server_id', $this->server->id)->count());

        $row = UserServerPrefix::query()->where('user_id', $user->id)->first();
        $this->assertNull($row->selected);
        $this->assertSame([], $row->available);
    }

    public function test_partial_export_does_not_delete(): void
    {
        $this->importer()->import($this->server->id, $this->payload('seed', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        $partial = $this->importer()->import($this->server->id, [
            'schema' => 1,
            'hash' => 'partial',
            'full' => false,
            'groups' => [$this->group('miner', '&a[M3]', 1, 3, 'Miner', 'quest')],
        ]);
        $this->assertSame(0, $partial['deleted']);
        $this->assertNotNull(PrefixCatalog::query()->where('group', 'farmer')->first());
        $this->assertTrue(PrefixCatalog::query()->where('group', 'farmer')->value('enabled'));
        $this->assertSame('&a[M3]', PrefixCatalog::query()->where('group', 'miner')->value('prefix_raw'));
    }

    public function test_full_export_may_delete_majority_without_force(): void
    {
        $this->importer()->import($this->server->id, $this->payload('base', [
            $this->group('a', '&a[A]', 1, 1, 'A', 'quest'),
            $this->group('b', '&a[B]', 2, 2, 'B', 'quest'),
            $this->group('c', '&a[C]', 3, 3, 'C', 'quest'),
        ]));

        $result = $this->importer()->import($this->server->id, $this->payload('drop', [
            $this->group('a', '&a[A]', 1, 1, 'A', 'quest'),
        ]));

        $this->assertSame(2, $result['deleted']);
        $this->assertSame(1, PrefixCatalog::query()->where('server_id', $this->server->id)->count());
        $this->assertSame('a', PrefixCatalog::query()->where('server_id', $this->server->id)->value('group'));
        $this->assertSame('drop', PrefixCatalogImport::query()->where('server_id', $this->server->id)->value('hash'));
    }

    public function test_returning_group_is_recreated(): void
    {
        $this->importer()->import($this->server->id, $this->payload('all', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ]));
        $this->importer()->import($this->server->id, $this->payload('gone', [
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ]));

        $this->assertNull(PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first());

        $this->importer()->import($this->server->id, $this->payload('back', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ]));

        $miner = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first();
        $this->assertNotNull($miner);
        $this->assertTrue($miner->enabled);
        $staff = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'staff')->first();
        $this->assertNotNull($staff);
        $this->assertTrue($staff->enabled);
    }

    public function test_null_catalog_is_ignored_once_server_catalog_exists(): void
    {
        $user = User::factory()->create([
            'name' => 'Nick',
            'game_id' => 'f3fc162dd34432fa8c9a0b987b0791cf',
        ]);
        PrefixCatalog::query()->create([
            'server_id' => null,
            'group' => 'miner',
            'display_name' => 'Global miner',
            'prefix_raw' => '&7[G]',
            'category' => 'quest',
            'sort' => 0,
            'enabled' => true,
        ]);

        $this->importer()->import($this->server->id, $this->payload('srv', [
            $this->group('farmer', '&e[F]', 1, 4, 'Farmer', 'quest'),
        ]));

        $this->assertSame(2, PrefixCatalog::query()->where('group', 'miner')->count() + PrefixCatalog::query()->where('group', 'farmer')->count());
        $global = PrefixCatalog::query()->whereNull('server_id')->where('group', 'miner')->first();
        $this->assertSame('Global miner', $global->display_name);

        UserServerPrefix::query()->create([
            'user_id' => $user->id,
            'server_id' => $this->server->id,
            'available' => [
                ['group' => 'miner', 'prefix_raw' => '&a[Шахтёр]', 'category' => 'quest'],
                ['group' => 'farmer', 'prefix_raw' => '&e[F]', 'category' => 'quest'],
            ],
            'selected' => null,
            'synced_at' => now(),
        ]);

        $row = UserServerPrefix::query()->where('user_id', $user->id)->first();
        $options = app(PrefixSelection::class)->visibleOptions($row, PrefixCatalog::mapForServer($this->server->id));

        $this->assertTrue($options->contains('group', 'miner'));
        $this->assertTrue($options->contains('group', 'farmer'));
        $this->assertSame('&a[Шахтёр]', $options->firstWhere('group', 'miner')['prefix_raw']);
        $this->assertFalse($options->firstWhere('group', 'miner')['in_catalog']);
        $this->assertTrue($options->firstWhere('group', 'farmer')['in_catalog']);
        $this->assertSame('Farmer', $options->firstWhere('group', 'farmer')['display_name']);
    }

    private function importer(): PrefixCatalogImporter
    {
        return app(PrefixCatalogImporter::class);
    }

    /**
     * @param  list<array<string, mixed>>  $groups
     * @return array<string, mixed>
     */
    private function payload(string $hash, array $groups): array
    {
        return [
            'schema' => 1,
            'hash' => $hash,
            'full' => true,
            'groups' => $groups,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    private function group(string $group, string $raw, int $priority, int $track, string $name, string $category): array
    {
        return [
            'group' => $group,
            'prefix_raw' => $raw,
            'priority' => $priority,
            'track_index' => $track,
            'display_name' => $name,
            'category' => $category,
        ];
    }
}
