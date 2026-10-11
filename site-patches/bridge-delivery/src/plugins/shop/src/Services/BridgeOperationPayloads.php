<?php

namespace Azuriom\Plugin\Shop\Services;

use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;

/**
 * Wire payloads for group_grant / group_revoke / kit_credit.
 * Kit credits are paid items and never expire (no valid_until).
 */
final class BridgeOperationPayloads
{
    /**
     * @return array<string, mixed>
     */
    public static function groupGrant(
        string $uuid,
        string $group,
        ?string $expiresAt,
        string $entitlementKey,
        int $version,
    ): array {
        return [
            'schema_version' => 1,
            'type' => MinecraftOperationType::GroupGrant->value,
            'uuid' => $uuid,
            'group' => $group,
            'expires_at' => $expiresAt,
            'entitlement_key' => $entitlementKey,
            'version' => $version,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    public static function groupRevoke(
        string $uuid,
        string $group,
        string $entitlementKey,
        int $version,
    ): array {
        return [
            'schema_version' => 1,
            'type' => MinecraftOperationType::GroupRevoke->value,
            'uuid' => $uuid,
            'group' => $group,
            'entitlement_key' => $entitlementKey,
            'version' => $version,
        ];
    }

    /**
     * @return array<string, mixed>
     */
    public static function kitCredit(string $uuid, string $kit, int $qty): array
    {
        return [
            'schema_version' => 1,
            'type' => MinecraftOperationType::KitCredit->value,
            'uuid' => $uuid,
            'kit' => $kit,
            'qty' => $qty,
        ];
    }
}
