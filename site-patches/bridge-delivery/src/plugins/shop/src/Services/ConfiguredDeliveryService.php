<?php

namespace Azuriom\Plugin\Shop\Services;

use Azuriom\Plugin\Shop\Data\KitRedeemRequest;
use Azuriom\Plugin\Shop\Data\PrivilegeReconcileRequest;
use Azuriom\Plugin\Shop\Data\PrivilegeRevokeRequest;
use Azuriom\Plugin\Shop\Enums\ConfiguredDeliveryStatus;
use Azuriom\Plugin\Shop\Enums\ConfiguredPurchaseStatus;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationStatus;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;
use Azuriom\Plugin\Shop\Enums\UserPrivilegeEntitlementStatus;
use Azuriom\Plugin\Shop\Models\ConfiguredPrivilegePurchase;
use Azuriom\Plugin\Shop\Models\ConfiguredPurchaseKit;
use Azuriom\Plugin\Shop\Models\MinecraftOperation;
use Azuriom\Plugin\Shop\Models\UserPrivilegeEntitlement;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;
use Throwable;

/**
 * In-game delivery of configured privilege purchases through the AzLink semantic executor.
 *
 * Only servers listed in shop.configured_delivery.servers are touched.
 * Off unless setting shop.configured_delivery.enabled = 1.
 */
class ConfiguredDeliveryService
{
    public function __construct(
        private MinecraftOperationDispatcher $dispatcher,
        private EntitlementLifecycleService $entitlements,
    ) {
    }

    public static function enabled(): bool
    {
        return (bool) setting('shop.configured_delivery.enabled', false);
    }

    /** @return array{dispatched: int, refreshed: int, expired: int, revoked: int} */
    public function tick(): array
    {
        $stats = ['dispatched' => 0, 'refreshed' => 0, 'expired' => 0, 'revoked' => 0];

        if (! self::enabled()) {
            return $stats;
        }

        $servers = ConfiguredDeliveryServers::ids();
        if ($servers === []) {
            return $stats;
        }

        ConfiguredPrivilegePurchase::query()
            ->whereIn('status', [
                ConfiguredPurchaseStatus::PendingActivation,
                ConfiguredPurchaseStatus::Scheduled,
            ])
            ->where('delivery_status', ConfiguredDeliveryStatus::Pending)
            ->where('starts_at', '<=', now())
            ->whereIn('server_id', $servers)
            ->orderBy('id')->limit(100)->get()
            ->each(function (ConfiguredPrivilegePurchase $p) use (&$stats) {
                $this->guard($p, fn () => $this->dispatch($p)) && $stats['dispatched']++;
            });

        ConfiguredPrivilegePurchase::query()
            ->whereIn('delivery_status', [
                ConfiguredDeliveryStatus::Processing, ConfiguredDeliveryStatus::Retrying,
                ConfiguredDeliveryStatus::Failed, ConfiguredDeliveryStatus::Uncertain,
            ])
            ->whereIn('status', [ConfiguredPurchaseStatus::PendingActivation, ConfiguredPurchaseStatus::DeliveryFailed])
            ->whereIn('server_id', $servers)
            ->orderBy('id')->limit(200)->get()
            ->each(function (ConfiguredPrivilegePurchase $p) use (&$stats) {
                $this->guard($p, fn () => $this->refresh($p)) && $stats['refreshed']++;
            });

        UserPrivilegeEntitlement::query()
            ->where('status', UserPrivilegeEntitlementStatus::Active)
            ->where('expires_at', '<=', now())
            ->whereIn('server_id', $servers)
            ->orderBy('id')->limit(100)->get()
            ->each(function (UserPrivilegeEntitlement $e) use (&$stats) {
                $this->guard($e, fn () => $this->expire($e)) && $stats['expired']++;
            });

        ConfiguredPrivilegePurchase::query()
            ->whereIn('status', [
                ConfiguredPurchaseStatus::Refunded,
                ConfiguredPurchaseStatus::Revoked,
            ])
            ->whereIn('server_id', $servers)
            ->orderBy('id')->limit(100)->get()
            ->each(function (ConfiguredPrivilegePurchase $p) use (&$stats) {
                $already = MinecraftOperation::query()
                    ->where('purchase_id', $p->id)
                    ->where('type', MinecraftOperationType::PrivilegeRevoke)
                    ->exists();
                if ($already) {
                    return;
                }
                $this->guard($p, fn () => $this->revokePurchase($p)) && $stats['revoked']++;
            });

        return $stats;
    }

    public function dispatch(ConfiguredPrivilegePurchase $purchase): void
    {
        if (! ConfiguredDeliveryServers::allows((int) $purchase->server_id)) {
            Log::info('[shop] configured delivery skip server '.$purchase->server_id);

            return;
        }

        $purchase->loadMissing(['user', 'serverConfiguration', 'kits.kit', 'entitlement']);
        $uuid = $this->playerUuid($purchase);
        $entitlement = $purchase->entitlement
            ?? UserPrivilegeEntitlement::where('current_purchase_id', $purchase->id)->firstOrFail();

        $this->dispatcher->reconcile(new PrivilegeReconcileRequest(
            $entitlement->id,
            $entitlement->version,
            $purchase->id,
            $purchase->server_id,
            $uuid,
            $this->entitlementKey($purchase),
            $purchase->starts_at,
            $purchase->expires_at,
            $this->capabilities($purchase),
        ));

        foreach ($purchase->kits as $line) {
            $this->dispatcher->redeem(new KitRedeemRequest(
                $line->id,
                $purchase->id,
                $purchase->server_id,
                $uuid,
                $this->kitKey($line),
            ));
        }

        $purchase->update(['delivery_status' => ConfiguredDeliveryStatus::Processing]);
    }

    public function refresh(ConfiguredPrivilegePurchase $purchase): void
    {
        $ops = MinecraftOperation::query()->where('purchase_id', $purchase->id)
            ->whereIn('type', [MinecraftOperationType::PrivilegeReconcile, MinecraftOperationType::KitRedeem])
            ->get();

        if ($ops->isEmpty()) {
            return;
        }

        $statuses = $ops->map(fn (MinecraftOperation $op) => $op->status);
        $done = fn (MinecraftOperationStatus $s) => in_array($s, [MinecraftOperationStatus::Succeeded, MinecraftOperationStatus::Superseded], true);

        if ($statuses->every($done)) {
            $entitlement = UserPrivilegeEntitlement::where('current_purchase_id', $purchase->id)->first();
            if ($entitlement !== null && $entitlement->status !== UserPrivilegeEntitlementStatus::Active) {
                $this->entitlements->activatePurchase($purchase->user, $purchase->server_id, $purchase->id, $entitlement->version);
            }
            $purchase->update(['delivery_status' => ConfiguredDeliveryStatus::Delivered]);

            return;
        }

        $next = match (true) {
            $statuses->contains(MinecraftOperationStatus::Uncertain) => ConfiguredDeliveryStatus::Uncertain,
            $statuses->contains(MinecraftOperationStatus::Failed) => ConfiguredDeliveryStatus::Failed,
            $ops->contains(fn (MinecraftOperation $op) => $op->attempts_count > 1) => ConfiguredDeliveryStatus::Retrying,
            default => ConfiguredDeliveryStatus::Processing,
        };

        if ($purchase->delivery_status !== $next) {
            $purchase->update(['delivery_status' => $next]);
        }
    }

    public function expire(UserPrivilegeEntitlement $entitlement): void
    {
        $purchase = ConfiguredPrivilegePurchase::with('user')->findOrFail($entitlement->current_purchase_id);
        if (! ConfiguredDeliveryServers::allows((int) $purchase->server_id)) {
            return;
        }

        $updated = $this->entitlements->expireCurrent($purchase->user, $entitlement->server_id, $purchase->id, $entitlement->version);

        if ($updated->status === UserPrivilegeEntitlementStatus::PendingActivation) {
            $successor = ConfiguredPrivilegePurchase::query()->find($updated->current_purchase_id);
            if ($successor !== null && $successor->delivery_status === ConfiguredDeliveryStatus::Pending) {
                $this->dispatch($successor);
            }

            return;
        }

        $this->dispatcher->revoke(new PrivilegeRevokeRequest(
            $updated->id,
            $updated->version,
            $purchase->id,
            $purchase->server_id,
            $this->playerUuid($purchase),
            $this->entitlementKey($purchase),
        ));
    }

    public function revokePurchase(ConfiguredPrivilegePurchase $purchase): void
    {
        if (! ConfiguredDeliveryServers::allows((int) $purchase->server_id)) {
            return;
        }

        $purchase->loadMissing(['user', 'entitlement']);
        $entitlement = $purchase->entitlement
            ?? UserPrivilegeEntitlement::where('current_purchase_id', $purchase->id)->first();
        if ($entitlement === null) {
            return;
        }

        if (! in_array($entitlement->status, [
            UserPrivilegeEntitlementStatus::Revoked,
            UserPrivilegeEntitlementStatus::Expired,
        ], true)) {
            $entitlement = $this->entitlements->revoke(
                $purchase->user,
                $purchase->server_id,
                $purchase->id,
                $entitlement->version,
            );
        }

        $this->dispatcher->revoke(new PrivilegeRevokeRequest(
            $entitlement->id,
            $entitlement->version,
            $purchase->id,
            $purchase->server_id,
            $this->playerUuid($purchase),
            $this->entitlementKey($purchase),
        ));
    }

    private function guard(object $model, callable $fn): bool
    {
        try {
            $fn();

            return true;
        } catch (Throwable $e) {
            Log::warning('[shop] configured delivery step failed for '.class_basename($model).' #'.$model->getKey().': '.$e->getMessage());

            return false;
        }
    }

    private function playerUuid(ConfiguredPrivilegePurchase $purchase): string
    {
        $raw = strtolower(str_replace('-', '', (string) $purchase->user?->game_id));
        if (! preg_match('/^[0-9a-f]{32}$/', $raw)) {
            throw new \RuntimeException('user has no Minecraft UUID');
        }

        return substr($raw, 0, 8).'-'.substr($raw, 8, 4).'-'.substr($raw, 12, 4).'-'.substr($raw, 16, 4).'-'.substr($raw, 20);
    }

    private function entitlementKey(ConfiguredPrivilegePurchase $purchase): string
    {
        $key = $purchase->serverConfiguration?->entitlement_key;

        return $key !== null && $key !== '' ? $key : (Str::slug((string) $purchase->privilege_name_snapshot) ?: 'privilege-'.$purchase->privilege_id);
    }

    private function kitKey(ConfiguredPurchaseKit $line): string
    {
        $key = $line->kit?->delivery_key;

        return $key !== null && $key !== '' ? $key : (string) $line->kit?->slug;
    }

    /** @return array<string, bool|int|string> */
    private function capabilities(ConfiguredPrivilegePurchase $purchase): array
    {
        $final = data_get($purchase->configuration_snapshot, 'quote.capabilities.final', []);
        $result = [];

        foreach ((array) $final as $mapKey => $cap) {
            $key = is_array($cap) ? ($cap['key'] ?? $mapKey) : $mapKey;
            $value = is_array($cap) ? ($cap['value'] ?? null) : $cap;
            if (! is_string($key) || $value === null || $value === false) {
                continue;
            }
            $result[$key] = is_float($value) ? (string) $value : $value;
        }

        return $result;
    }
}
