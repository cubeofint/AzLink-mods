<?php

namespace Tests\Feature;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\Server;
use Azuriom\Models\Setting;
use Azuriom\Services\Prefix\PrefixCatalogImporter;
use Illuminate\Foundation\Testing\RefreshDatabase;
use RuntimeException;
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

    public function test_overrides_are_preserved_on_upsert(): void
    {
        PrefixCatalog::query()->create([
            'server_id' => $this->server->id,
            'group' => 'miner',
            'display_name' => 'Шахтёр (ручной)',
            'auto_display_name' => 'miner',
            'prefix_raw' => '&a[old]',
            'auto_prefix_raw' => '&a[old]',
            'category' => 'quest',
            'sort' => 7,
            'auto_sort' => 1,
            'priority' => 0,
            'enabled' => false,
            'source' => PrefixCatalog::SOURCE_AUTO,
            'override_display_name' => true,
            'override_sort' => true,
            'override_enabled' => true,
        ]);

        $result = $this->importer()->import($this->server->id, $this->payload('h1', [
            $this->group('miner', '&a[Miner]', 3, 10, 'Miner', 'quest'),
        ]));

        $this->assertFalse($result['unchanged']);
        $row = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first();
        $this->assertSame('Шахтёр (ручной)', $row->display_name);
        $this->assertSame('Miner', $row->auto_display_name);
        $this->assertSame(7, $row->sort);
        $this->assertSame(10, $row->auto_sort);
        $this->assertFalse($row->enabled);
        $this->assertTrue($row->override_display_name);
        $this->assertTrue($row->override_sort);
        $this->assertTrue($row->override_enabled);
        $this->assertSame('&a[Miner]', $row->prefix_raw);
        $this->assertSame('&a[Miner]', $row->auto_prefix_raw);
        $this->assertNull($row->missing_since);
        $this->assertSame('Шахтёр (ручной)', $row->visibleDisplayName());
        $this->assertSame(7, $row->visibleSort());
    }

    public function test_missing_groups_from_full_export_are_disabled(): void
    {
        $this->importer()->import($this->server->id, $this->payload('h-full', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        $second = $this->importer()->import($this->server->id, $this->payload('h-full-2', [
            $this->group('miner', '&a[M2]', 1, 1, 'Miner', 'quest'),
        ]));

        $this->assertSame(1, $second['missing']);
        $farmer = PrefixCatalog::query()->where('group', 'farmer')->first();
        $this->assertNotNull($farmer->missing_since);
        $this->assertFalse($farmer->enabled);
        $miner = PrefixCatalog::query()->where('group', 'miner')->first();
        $this->assertNull($miner->missing_since);
        $this->assertTrue($miner->enabled);
        $this->assertSame('&a[M2]', $miner->prefix_raw);
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
            'override_display_name' => true,
        ]);

        $second = $this->importer()->import($this->server->id, $payload);
        $this->assertTrue($second['unchanged']);
        $this->assertSame(1, PrefixCatalog::query()->count());
        $this->assertSame('touched', PrefixCatalog::query()->where('group', 'miner')->value('display_name'));
        $this->assertSame(1, PrefixCatalogImport::query()->where('server_id', $this->server->id)->count());
    }

    public function test_empty_groups_or_partial_export_do_not_disable(): void
    {
        $this->importer()->import($this->server->id, $this->payload('seed', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('farmer', '&e[F]', 2, 2, 'Farmer', 'quest'),
        ]));

        $empty = $this->importer()->import($this->server->id, [
            'schema' => 1,
            'hash' => 'empty-full',
            'full' => true,
            'groups' => [],
        ]);
        $this->assertFalse($empty['unchanged']);
        $this->assertSame(0, $empty['missing']);
        $this->assertTrue(PrefixCatalog::query()->where('group', 'miner')->value('enabled'));
        $this->assertTrue(PrefixCatalog::query()->where('group', 'farmer')->value('enabled'));
        $this->assertNull(PrefixCatalog::query()->where('group', 'miner')->value('missing_since'));

        $partial = $this->importer()->import($this->server->id, [
            'schema' => 1,
            'hash' => 'partial',
            'full' => false,
            'groups' => [$this->group('miner', '&a[M3]', 1, 3, 'Miner', 'quest')],
        ]);
        $this->assertSame(0, $partial['missing']);
        $this->assertTrue(PrefixCatalog::query()->where('group', 'farmer')->value('enabled'));
        $this->assertNull(PrefixCatalog::query()->where('group', 'farmer')->value('missing_since'));
        $this->assertSame('&a[M3]', PrefixCatalog::query()->where('group', 'miner')->value('prefix_raw'));
    }

    public function test_majority_disappear_requires_force(): void
    {
        $this->importer()->import($this->server->id, $this->payload('base', [
            $this->group('a', '&a[A]', 1, 1, 'A', 'quest'),
            $this->group('b', '&a[B]', 2, 2, 'B', 'quest'),
            $this->group('c', '&a[C]', 3, 3, 'C', 'quest'),
        ]));

        try {
            $this->importer()->import($this->server->id, $this->payload('drop', [
                $this->group('a', '&a[A]', 1, 1, 'A', 'quest'),
            ]));
            $this->fail('expected force_required');
        } catch (RuntimeException $e) {
            $this->assertSame('prefix_catalog_force_required', $e->getMessage());
        }

        $this->assertSame(3, PrefixCatalog::query()->where('server_id', $this->server->id)->where('enabled', true)->count());
        $this->assertSame('base', PrefixCatalogImport::query()->where('server_id', $this->server->id)->value('hash'));

        $forced = $this->importer()->import($this->server->id, $this->payload('drop', [
            $this->group('a', '&a[A]', 1, 1, 'A', 'quest'),
        ], force: true));
        $this->assertSame(2, $forced['missing']);
        $this->assertFalse(PrefixCatalog::query()->where('group', 'b')->value('enabled'));
    }

    public function test_returning_group_is_reenabled_unless_manually_disabled(): void
    {
        $this->importer()->import($this->server->id, $this->payload('all', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ]));
        $this->importer()->import($this->server->id, $this->payload('gone', [
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ], force: true));

        PrefixCatalog::query()->where('group', 'staff')->update([
            'enabled' => false,
            'override_enabled' => true,
        ]);

        $this->importer()->import($this->server->id, $this->payload('back', [
            $this->group('miner', '&a[M]', 1, 1, 'Miner', 'quest'),
            $this->group('staff', '&c[S]', 2, 2, 'Staff', 'staff'),
        ]));

        $miner = PrefixCatalog::query()->where('group', 'miner')->first();
        $this->assertTrue($miner->enabled);
        $this->assertNull($miner->missing_since);
        $staff = PrefixCatalog::query()->where('group', 'staff')->first();
        $this->assertFalse($staff->enabled);
        $this->assertTrue($staff->override_enabled);
        $this->assertNull($staff->missing_since);
    }

    public function test_existing_global_manual_row_is_not_rewritten(): void
    {
        PrefixCatalog::query()->create([
            'server_id' => null,
            'group' => 'miner',
            'display_name' => 'Global miner',
            'prefix_raw' => '&7[G]',
            'category' => 'quest',
            'sort' => 0,
            'source' => PrefixCatalog::SOURCE_MANUAL,
            'enabled' => true,
        ]);

        $this->importer()->import($this->server->id, $this->payload('srv', [
            $this->group('miner', '&a[M]', 1, 4, 'Miner', 'quest'),
        ]));

        $this->assertSame(2, PrefixCatalog::query()->where('group', 'miner')->count());
        $global = PrefixCatalog::query()->whereNull('server_id')->where('group', 'miner')->first();
        $this->assertSame('Global miner', $global->display_name);
        $this->assertSame(PrefixCatalog::SOURCE_MANUAL, $global->source);
        $local = PrefixCatalog::query()->where('server_id', $this->server->id)->where('group', 'miner')->first();
        $this->assertSame(PrefixCatalog::SOURCE_AUTO, $local->source);
        $this->assertSame(4, $local->auto_sort);
    }

    private function importer(): PrefixCatalogImporter
    {
        return app(PrefixCatalogImporter::class);
    }

    /**
     * @param  list<array<string, mixed>>  $groups
     * @return array<string, mixed>
     */
    private function payload(string $hash, array $groups, bool $force = false): array
    {
        return [
            'schema' => 1,
            'hash' => $hash,
            'full' => true,
            'force' => $force,
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
