<?php

namespace Azuriom\Plugin\Shop\Services;

use Azuriom\Plugin\Shop\Data\GroupGrantRequest;
use Azuriom\Plugin\Shop\Data\GroupRevokeRequest;
use Azuriom\Plugin\Shop\Data\KitCreditRequest;
use Azuriom\Plugin\Shop\Data\KitRedeemRequest;
use Azuriom\Plugin\Shop\Data\PrivilegeReconcileRequest;
use Azuriom\Plugin\Shop\Data\PrivilegeRevokeRequest;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationStatus;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;
use Azuriom\Plugin\Shop\Models\ConfiguredPrivilegePurchase;
use Azuriom\Plugin\Shop\Models\MinecraftOperation;
use Azuriom\Plugin\Shop\Models\UserPrivilegeEntitlement;
use Azuriom\Plugin\Shop\Support\CanonicalJson;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Database\QueryException;
use Illuminate\Support\Str;
use InvalidArgumentException;
use LogicException;

class MinecraftOperationDispatcher
{
    public const PROTOCOL_VERSION = 1;

    public function reconcile(PrivilegeReconcileRequest $request): MinecraftOperation
    {
        $this->assertEntitlementIdentity(
            $request->entitlementId,
            $request->purchaseId,
            $request->serverId
        );
        $this->assertSemanticKey($request->entitlementKey);
        $capabilities = $request->capabilities;
        ksort($capabilities, SORT_STRING);

        return $this->dispatch(
            MinecraftOperationType::PrivilegeReconcile,
            "entitlement:{$request->entitlementId}:version:{$request->entitlementVersion}:reconcile",
            $request->serverId,
            $request->playerUuid,
            [
                'schema_version' => 1,
                'type' => MinecraftOperationType::PrivilegeReconcile->value,
                'user' => ['uuid' => $request->playerUuid],
                'entitlement' => [
                    'id' => $request->entitlementId,
                    'version' => $request->entitlementVersion,
                    'purchase_id' => $request->purchaseId,
                    'key' => $request->entitlementKey,
                    'starts_at' => $request->startsAt->format(DATE_ATOM),
                    'expires_at' => $request->expiresAt->format(DATE_ATOM),
                ],
                'capabilities' => $capabilities,
            ],
            $request->entitlementId,
            $request->entitlementVersion,
            $request->purchaseId,
        );
    }

    public function revoke(PrivilegeRevokeRequest $request): MinecraftOperation
    {
        $this->assertEntitlementIdentity(
            $request->entitlementId,
            $request->purchaseId,
            $request->serverId
        );
        $this->assertSemanticKey($request->entitlementKey);

        return $this->dispatch(
            MinecraftOperationType::PrivilegeRevoke,
            "entitlement:{$request->entitlementId}:version:{$request->entitlementVersion}:revoke",
            $request->serverId,
            $request->playerUuid,
            [
                'schema_version' => 1,
                'type' => MinecraftOperationType::PrivilegeRevoke->value,
                'user' => ['uuid' => $request->playerUuid],
                'entitlement' => [
                    'id' => $request->entitlementId,
                    'version' => $request->entitlementVersion,
                    'purchase_id' => $request->purchaseId,
                    'key' => $request->entitlementKey,
                ],
            ],
            $request->entitlementId,
            $request->entitlementVersion,
            $request->purchaseId,
        );
    }

    public function redeem(KitRedeemRequest $request): MinecraftOperation
    {
        if ($request->redemptionId < 1) {
            throw new InvalidArgumentException('redemption_id is required.');
        }
        $this->assertPurchaseIdentity($request->purchaseId, $request->serverId);
        $this->assertSemanticKey($request->deliveryKey);

        return $this->dispatch(
            MinecraftOperationType::KitRedeem,
            "redemption:{$request->redemptionId}:redeem",
            $request->serverId,
            $request->playerUuid,
            [
                'schema_version' => 1,
                'type' => MinecraftOperationType::KitRedeem->value,
                'redemption_id' => $request->redemptionId,
                'purchase_id' => $request->purchaseId,
                'player_uuid' => $request->playerUuid,
                'kit_key' => $request->deliveryKey,
            ],
            purchaseId: $request->purchaseId,
            redemptionId: $request->redemptionId,
        );
    }

    public function grantGroup(GroupGrantRequest $request): MinecraftOperation
    {
        $this->assertSemanticKey($request->entitlementKey);

        return $this->dispatch(
            MinecraftOperationType::GroupGrant,
            $request->operationKey,
            $request->serverId,
            $request->playerUuid,
            BridgeOperationPayloads::groupGrant(
                $request->playerUuid,
                $request->group,
                $request->expiresAt,
                $request->entitlementKey,
                $request->version,
            ),
            entitlementVersion: $request->version,
            purchaseId: $request->purchaseId,
        );
    }

    public function revokeGroup(GroupRevokeRequest $request): MinecraftOperation
    {
        $this->assertSemanticKey($request->entitlementKey);

        return $this->dispatch(
            MinecraftOperationType::GroupRevoke,
            $request->operationKey,
            $request->serverId,
            $request->playerUuid,
            BridgeOperationPayloads::groupRevoke(
                $request->playerUuid,
                $request->group,
                $request->entitlementKey,
                $request->version,
            ),
            entitlementVersion: $request->version,
            purchaseId: $request->purchaseId,
        );
    }

    public function creditKit(KitCreditRequest $request): MinecraftOperation
    {
        if ($request->qty < 1) {
            throw new InvalidArgumentException('qty must be >= 1.');
        }

        return $this->dispatch(
            MinecraftOperationType::KitCredit,
            $request->operationKey,
            $request->serverId,
            $request->playerUuid,
            BridgeOperationPayloads::kitCredit($request->playerUuid, $request->kit, $request->qty),
            purchaseId: $request->purchaseId,
        );
    }

    public function failNoUuid(
        MinecraftOperationType $type,
        string $operationKey,
        int $serverId,
        array $payload,
        ?int $purchaseId = null,
    ): MinecraftOperation {
        $existing = MinecraftOperation::where('server_id', $serverId)
            ->where('operation_key', $operationKey)->first();
        if ($existing !== null) {
            return $existing;
        }

        $payload = CanonicalJson::normalize($payload);
        $hash = CanonicalJson::hash($payload);

        return MinecraftOperation::create([
            'operation_id' => (string) Str::uuid(),
            'operation_key' => $operationKey,
            'server_id' => $serverId,
            'type' => $type,
            'status' => MinecraftOperationStatus::Failed,
            'purchase_id' => $purchaseId,
            'player_uuid' => '00000000-0000-0000-0000-000000000000',
            'protocol_version' => self::PROTOCOL_VERSION,
            'payload' => $payload,
            'payload_hash' => $hash,
            'available_at' => now(),
            'result_code' => 'failed:no_uuid',
            'last_error' => 'user has no Minecraft UUID (users.game_id)',
            'completed_at' => now(),
        ]);
    }

    public function nextGrantVersion(string $playerUuid, string $entitlementKey): int
    {
        $max = MinecraftOperation::query()
            ->where('player_uuid', $playerUuid)
            ->whereIn('type', [MinecraftOperationType::GroupGrant, MinecraftOperationType::GroupRevoke])
            ->get()
            ->map(function (MinecraftOperation $op) use ($entitlementKey) {
                $key = data_get($op->payload, 'entitlement_key');

                return $key === $entitlementKey ? (int) ($op->entitlement_version ?? data_get($op->payload, 'version')) : 0;
            })
            ->max();

        return max(1, (int) $max + 1);
    }

    private function dispatch(
        MinecraftOperationType $type,
        string $operationKey,
        int $serverId,
        string $playerUuid,
        array $payload,
        ?int $entitlementId = null,
        ?int $entitlementVersion = null,
        ?int $purchaseId = null,
        ?int $redemptionId = null,
    ): MinecraftOperation {
        if (! ConfiguredDeliveryServers::allows($serverId)) {
            throw new InvalidArgumentException("Server {$serverId} is not in shop.configured_delivery.servers");
        }
        if (! Str::isUuid($playerUuid)) {
            throw new InvalidArgumentException('player_uuid must be a UUID.');
        }
        $payload = CanonicalJson::normalize($payload);
        $hash = CanonicalJson::hash($payload);

        $existing = MinecraftOperation::where('server_id', $serverId)
            ->where('operation_key', $operationKey)->first();
        if ($existing !== null) {
            return $this->assertReplay($existing, $hash);
        }

        try {
            return MinecraftOperation::create([
                'operation_id' => (string) Str::uuid(),
                'operation_key' => $operationKey,
                'server_id' => $serverId,
                'type' => $type,
                'status' => MinecraftOperationStatus::Pending,
                'entitlement_id' => $entitlementId,
                'entitlement_version' => $entitlementVersion,
                'purchase_id' => $purchaseId,
                'redemption_id' => $redemptionId,
                'player_uuid' => $playerUuid,
                'protocol_version' => self::PROTOCOL_VERSION,
                'payload' => $payload,
                'payload_hash' => $hash,
                'available_at' => now(),
            ]);
        } catch (QueryException $exception) {
            $existing = MinecraftOperation::where('server_id', $serverId)
                ->where('operation_key', $operationKey)->first();
            if ($existing === null) {
                throw $exception;
            }

            return $this->assertReplay($existing, $hash);
        }
    }

    private function assertReplay(MinecraftOperation $operation, string $hash): MinecraftOperation
    {
        if (! hash_equals($operation->payload_hash, $hash)) {
            throw new LogicException('Operation key was reused with a different payload.');
        }

        return $operation;
    }

    private function assertEntitlementIdentity(int $id, int $purchaseId, int $serverId): void
    {
        $exists = UserPrivilegeEntitlement::whereKey($id)
            ->where('current_purchase_id', $purchaseId)->where('server_id', $serverId)->exists();
        if (! $exists) {
            throw new LogicException('Entitlement operation identity is invalid.');
        }
    }

    private function assertPurchaseIdentity(int $id, int $serverId): void
    {
        if (! ConfiguredPrivilegePurchase::whereKey($id)->where('server_id', $serverId)->exists()) {
            throw new LogicException('Purchase operation identity is invalid.');
        }
    }

    private function assertSemanticKey(string $key): void
    {
        if (! preg_match('/^[a-z0-9][a-z0-9._-]{0,63}$/', $key)) {
            throw new InvalidArgumentException('A configured semantic key is required.');
        }
    }
}
