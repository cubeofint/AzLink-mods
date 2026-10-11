<?php

namespace Tests\Feature;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixOperation as Op;
use Azuriom\Models\Server;
use Azuriom\Models\Setting;
use Azuriom\Models\User;
use Azuriom\Models\UserServerPrefix;
use Azuriom\Services\Prefix\PrefixFormatter;
use Azuriom\Services\Prefix\PrefixOperationQueue;
use Azuriom\Services\Prefix\PrefixReporter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Foundation\Testing\RefreshDatabase;
use RuntimeException;
use Tests\TestCase;

/**
 * Requires a booted Azuriom app (phpunit.xml + sqlite).
 *
 *   cd /var/www/main-site
 *   php artisan test --filter=PrefixSelectionTest
 */
class PrefixSelectionTest extends TestCase
{
    use RefreshDatabase;

    private User $user;

    private Server $server;

    protected function setUp(): void
    {
        parent::setUp();

        $this->user = User::factory()->create([
            'name' => 'Nick',
            'game_id' => 'f3fc162dd34432fa8c9a0b987b0791cf',
        ]);
        $this->server = Server::query()->create([
            'name' => 'ATM10-Test',
            'address' => '127.0.0.1',
            'type' => 'mc-azlink',
            'port' => 25565,
            'data' => [],
        ]);

        Setting::updateSettings(['prefixes.enabled' => '1', 'prefixes.lease_seconds' => '30']);
    }

    public function test_report_upserts_by_uuid_and_name(): void
    {
        $reporter = app(PrefixReporter::class);

        $first = $reporter->report($this->server->id, [[
            'uuid' => 'f3fc162d-d344-32fa-8c9a-0b987b0791cf',
            'name' => 'Nick',
            'available' => [
                ['group' => 'miner', 'prefix_raw' => '&a[Шахтёр]', 'category' => 'quest'],
                ['group' => 'vip', 'prefix_raw' => '<gold>[VIP]</gold>', 'category' => 'donate'],
                ['group' => 'hidden', 'prefix_raw' => '&8[X]', 'category' => 'other'],
                ['group' => 'media', 'prefix_raw' => '&b[YT]', 'category' => 'media'],
            ],
            'selected' => 'miner',
            'locked_staff' => false,
        ]]);

        $this->assertSame(1, $first['stored']);
        $this->assertSame(0, $first['skipped']);

        $row = UserServerPrefix::query()->where('user_id', $this->user->id)->first();
        $this->assertSame('miner', $row->selected);
        $this->assertCount(4, $row->available);
        $this->assertSame('media', $row->available[3]['category']);

        $byName = User::factory()->create(['name' => 'OnlyName', 'game_id' => null]);
        $second = $reporter->report($this->server->id, [[
            'uuid' => '00000000-0000-0000-0000-000000000099',
            'name' => 'OnlyName',
            'available' => [],
            'selected' => null,
            'locked_staff' => true,
        ]]);
        $this->assertSame(1, $second['stored']);
        $this->assertTrue(UserServerPrefix::query()->where('user_id', $byName->id)->first()->locked_staff);
    }

    public function test_unknown_player_is_skipped(): void
    {
        $result = app(PrefixReporter::class)->report($this->server->id, [[
            'uuid' => 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
            'name' => 'Missing',
            'available' => [],
            'selected' => null,
            'locked_staff' => false,
        ]]);

        $this->assertSame(0, $result['stored']);
        $this->assertSame(1, $result['skipped']);
    }

    public function test_select_creates_prefix_select_operation(): void
    {
        $this->seedSnapshot();

        $op = app(PrefixSelection::class)->select($this->user, $this->server->id, 'miner', 'profile');

        $this->assertSame(Op::TYPE_SELECT, $op->type);
        $this->assertSame('miner', $op->group);
        $this->assertSame(Op::PENDING, $op->status);
        $this->assertSame('miner', UserServerPrefix::query()->where('user_id', $this->user->id)->value('selected'));
    }

    public function test_staff_can_select_quest_prefix(): void
    {
        $this->seedSnapshot(locked: true);

        $op = app(PrefixSelection::class)->select($this->user, $this->server->id, 'miner', 'profile');
        $this->assertSame('miner', $op->group);
    }

    public function test_cabinet_shows_only_quest_and_keeps_uncatalogued_quest(): void
    {
        $this->seedSnapshot();
        PrefixCatalog::query()->create([
            'server_id' => $this->server->id,
            'group' => 'miner',
            'display_name' => 'Шахтёр',
            'prefix_raw' => '&a[Шахтёр]',
            'category' => 'quest',
            'sort' => 0,
            'enabled' => true,
        ]);

        $row = UserServerPrefix::query()->where('user_id', $this->user->id)->first();
        $row->available = array_merge($row->available, [
            ['group' => 'builder', 'prefix_raw' => '&b[Строитель]', 'category' => 'quest'],
        ]);
        $row->save();

        $options = app(PrefixSelection::class)->visibleOptions(
            $row,
            PrefixCatalog::mapForServer($this->server->id)
        );

        $this->assertTrue($options->contains('group', 'miner'));
        $this->assertTrue($options->contains('group', 'builder'));
        $this->assertFalse($options->contains('group', 'vip'));
        $this->assertFalse($options->contains('group', 'hidden'));
        $this->assertFalse($options->contains('group', 'media'));
        $this->assertSame('&b[Строитель]', $options->firstWhere('group', 'builder')['prefix_raw']);
        $this->assertFalse($options->firstWhere('group', 'builder')['in_catalog']);
    }

    public function test_disabled_quest_catalog_entry_hidden_and_not_selectable(): void
    {
        $this->seedSnapshot();
        PrefixCatalog::query()->create([
            'server_id' => $this->server->id,
            'group' => 'hidden',
            'display_name' => 'Hidden',
            'prefix_raw' => '&8[X]',
            'category' => 'quest',
            'sort' => 1,
            'enabled' => false,
        ]);
        PrefixCatalog::query()->create([
            'group' => 'miner',
            'display_name' => 'Шахтёр',
            'prefix_raw' => '&a[Шахтёр]',
            'category' => 'quest',
            'sort' => 0,
            'enabled' => true,
        ]);

        $row = UserServerPrefix::query()->where('user_id', $this->user->id)->first();
        $row->available = array_merge($row->available, [
            ['group' => 'hidden', 'prefix_raw' => '&8[X]', 'category' => 'quest'],
        ]);
        $row->save();

        $options = app(PrefixSelection::class)->visibleOptions($row, PrefixCatalog::mapForServer($this->server->id));
        $this->assertTrue($options->contains('group', 'miner'));
        $this->assertFalse($options->contains('group', 'hidden'));

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('prefix_disabled');
        app(PrefixSelection::class)->select($this->user, $this->server->id, 'hidden', 'profile');
    }

    public function test_reporter_stores_category_as_given(): void
    {
        $available = app(PrefixReporter::class)->normalizeAvailable([
            ['group' => 'yt', 'prefix_raw' => '&b[YT]', 'category' => 'media'],
            ['group' => 'custom', 'prefix_raw' => '&f[X]', 'category' => 'event'],
        ]);

        $this->assertSame('media', $available[0]['category']);
        $this->assertSame('event', $available[1]['category']);
    }

    public function test_donate_prefix_is_not_selectable(): void
    {
        $this->seedSnapshot();

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('prefix_not_available');
        app(PrefixSelection::class)->select($this->user, $this->server->id, 'vip', 'profile');
    }

    public function test_pending_lease_and_idempotent_ack(): void
    {
        $this->seedSnapshot();
        $queue = app(PrefixOperationQueue::class);
        $op = $queue->enqueue($this->user, $this->server->id, 'miner', ['source' => 'profile']);

        $first = $queue->pending($this->server->id, 50);
        $this->assertCount(1, $first);
        $this->assertSame($op->id, $first[0]['op_id']);
        $this->assertSame('prefix_select', $first[0]['type']);
        $this->assertSame('miner', $first[0]['group']);

        $leased = $queue->pending($this->server->id, 50);
        $this->assertCount(0, $leased);

        $ack = $queue->ack($this->server->id, $op->id, Op::APPLIED, null, 'miner');
        $this->assertSame(Op::APPLIED, $ack->status);
        $this->assertSame('miner', UserServerPrefix::query()->where('user_id', $this->user->id)->value('selected'));

        $again = $queue->ack($this->server->id, $op->id, Op::REJECTED, 'ignored', null);
        $this->assertSame(Op::APPLIED, $again->status);
        $this->assertSame('miner', UserServerPrefix::query()->where('user_id', $this->user->id)->value('selected'));
    }

    public function test_ack_rejected_keeps_previous_selection(): void
    {
        $this->seedSnapshot();
        $queue = app(PrefixOperationQueue::class);
        $op = $queue->enqueue($this->user, $this->server->id, null, ['source' => 'admin_reset']);
        $queue->ack($this->server->id, $op->id, Op::REJECTED, 'staff_locked', 'miner');

        $this->assertSame('miner', UserServerPrefix::query()->where('user_id', $this->user->id)->value('selected'));
        $this->assertSame(Op::REJECTED, $op->fresh()->status);
    }

    public function test_formatter_legacy_and_minimessage(): void
    {
        $formatter = new PrefixFormatter();

        $legacy = $formatter->toHtml('&a[VIP]');
        $this->assertStringContainsString('#55FF55', $legacy);
        $this->assertStringContainsString('[VIP]', $legacy);
        $this->assertStringNotContainsString('&a', $legacy);

        $mini = $formatter->toHtml('<gold>[VIP]</gold>');
        $this->assertStringContainsString('#FFAA00', $mini);
        $this->assertStringContainsString('[VIP]', $mini);

        $escaped = $formatter->toHtml('<red>a<script>alert(1)</script>b</red>');
        $this->assertStringNotContainsString('<script>', $escaped);
        $this->assertStringContainsString('alert(1)', $escaped);
    }

    public function test_auto_select_enqueues_null_group(): void
    {
        $this->seedSnapshot();
        $op = app(PrefixSelection::class)->select($this->user, $this->server->id, null, 'profile');
        $this->assertNull($op->group);
        $this->assertSame(Op::TYPE_SELECT, $op->type);
    }

    private function seedSnapshot(bool $locked = false): void
    {
        UserServerPrefix::query()->create([
            'user_id' => $this->user->id,
            'server_id' => $this->server->id,
            'available' => [
                ['group' => 'miner', 'prefix_raw' => '&a[Шахтёр]', 'category' => 'quest'],
                ['group' => 'vip', 'prefix_raw' => '<gold>[VIP]</gold>', 'category' => 'donate'],
                ['group' => 'hidden', 'prefix_raw' => '&8[X]', 'category' => 'other'],
            ],
            'selected' => 'miner',
            'locked_staff' => $locked,
            'synced_at' => now(),
        ]);
    }
}
