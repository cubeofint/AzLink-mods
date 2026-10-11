<?php

namespace Azuriom\Plugin\Shop\Tests\Unit;

use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;
use Azuriom\Plugin\Shop\Services\BridgeExpiry;
use Azuriom\Plugin\Shop\Services\BridgeOperationPayloads;
use Azuriom\Plugin\Shop\Services\BridgePackageDelivery;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Support\Carbon;
use PHPUnit\Framework\TestCase;

class BridgeDeliveryTest extends TestCase
{
    public function test_server_csv_parses_unique_ids(): void
    {
        $this->assertSame([9, 12], ConfiguredDeliveryServers::ids('9, 12,9'));
        $this->assertSame([], ConfiguredDeliveryServers::ids(''));
        $this->assertTrue(ConfiguredDeliveryServers::allows(9, '9,12'));
        $this->assertFalse(ConfiguredDeliveryServers::allows(3, '9,12'));
        $this->assertFalse(ConfiguredDeliveryServers::allows(9, ''));
    }

    public function test_expiry_is_max_current_end_plus_calendar_months(): void
    {
        Carbon::setTestNow('2026-10-11 12:00:00');
        $end = BridgeExpiry::absoluteEnd(Carbon::parse('2026-09-01 00:00:00'), 2);
        $this->assertSame('2026-12-11 12:00:00', $end->format('Y-m-d H:i:s'));

        $later = BridgeExpiry::absoluteEnd(Carbon::parse('2027-01-15 00:00:00'), 1);
        $this->assertSame('2027-02-15 00:00:00', $later->format('Y-m-d H:i:s'));
        Carbon::setTestNow();
    }

    public function test_group_grant_payload_has_absolute_expiry(): void
    {
        $payload = BridgeOperationPayloads::groupGrant(
            '11111111-1111-1111-1111-111111111111',
            'vip',
            '2027-01-01T00:00:00+00:00',
            'vip',
            3,
        );

        $this->assertSame(MinecraftOperationType::GroupGrant->value, $payload['type']);
        $this->assertSame('vip', $payload['group']);
        $this->assertSame('2027-01-01T00:00:00+00:00', $payload['expires_at']);
        $this->assertSame(3, $payload['version']);
        $this->assertArrayNotHasKey('valid_until', $payload);
    }

    public function test_kit_credit_payload_has_no_valid_until(): void
    {
        $payload = BridgeOperationPayloads::kitCredit(
            '11111111-1111-1111-1111-111111111111',
            'starter',
            2,
        );

        $this->assertSame(MinecraftOperationType::KitCredit->value, $payload['type']);
        $this->assertSame('starter', $payload['kit']);
        $this->assertSame(2, $payload['qty']);
        $this->assertArrayNotHasKey('valid_until', $payload);
        $this->assertArrayNotHasKey('expires_at', $payload);
    }

    public function test_group_name_normalization(): void
    {
        $this->assertSame('vip', BridgePackageDelivery::normalizeGroup('VIP'));
        $this->assertNull(BridgePackageDelivery::normalizeGroup('bad group'));
        $this->assertNull(BridgePackageDelivery::normalizeGroup(''));
    }
}
