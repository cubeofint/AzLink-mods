<?php

namespace Azuriom\Plugin\Shop\Services;

use Azuriom\Models\User;
use Azuriom\Plugin\Shop\Data\GroupGrantRequest;
use Azuriom\Plugin\Shop\Data\GroupRevokeRequest;
use Azuriom\Plugin\Shop\Data\KitCreditRequest;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;
use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Models\PaymentItem;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;

/**
 * Turns package command groups with mode=bridge into shop_minecraft_operations.
 * Console-command groups are left to Package::dispatchCommands.
 */
class BridgePackageDelivery
{
    public function __construct(private MinecraftOperationDispatcher $dispatcher)
    {
    }

    /**
     * @param  Collection<int, array<string, mixed>>  $groups
     */
    public function dispatch(Package $package, PaymentItem $item, string $trigger, Collection $groups): void
    {
        $user = $item->payment?->user;
        $index = 0;

        foreach ($groups as $group) {
            $serverId = (int) ($group['server'] ?? 0);
            $actions = $group['actions'] ?? [];
            if ($serverId < 1 || ! is_array($actions)) {
                $index++;

                continue;
            }

            if (! ConfiguredDeliveryServers::allows($serverId)) {
                Log::info('[shop] bridge skip package group: server '.$serverId.' not in shop.configured_delivery.servers');
                $index++;

                continue;
            }

            foreach ($actions as $action) {
                if (! is_array($action) || empty($action['op'])) {
                    $index++;

                    continue;
                }
                $this->dispatchAction($package, $item, $user, $serverId, $trigger, $action, $index);
                $index++;
            }
        }
    }

    /**
     * @param  array<string, mixed>  $action
     */
    private function dispatchAction(
        Package $package,
        PaymentItem $item,
        ?User $user,
        int $serverId,
        string $trigger,
        array $action,
        int $index,
    ): void {
        $operationKey = 'pkg:'.$item->id.':'.$index;
        $op = (string) $action['op'];
        $uuid = $this->playerUuid($user);

        if ($uuid === null) {
            $type = $op === 'add_kit_credits'
                ? MinecraftOperationType::KitCredit
                : ($this->isRevokeTrigger($trigger) ? MinecraftOperationType::GroupRevoke : MinecraftOperationType::GroupGrant);
            $this->dispatcher->failNoUuid($type, $operationKey, $serverId, [
                'schema_version' => 1,
                'type' => $type->value,
                'reason' => 'failed:no_uuid',
                'package_id' => $package->id,
                'payment_item_id' => $item->id,
            ]);

            return;
        }

        if ($op === 'add_kit_credits') {
            $kit = $this->replaceTokens((string) ($action['kit'] ?? ''), $package, $item);
            $qty = BridgeExpiry::resolveQty($package, $item, (string) ($action['qty'] ?? '{quantity}'));
            $this->dispatcher->creditKit(new KitCreditRequest(
                $operationKey,
                $serverId,
                $uuid,
                $kit,
                $qty,
                paymentItemId: $item->id,
            ));

            return;
        }

        if ($op !== 'grant_group') {
            Log::warning('[shop] bridge unknown action '.$op.' item='.$item->id);

            return;
        }

        $group = self::normalizeGroup($this->replaceTokens((string) ($action['group'] ?? '{rank_name}'), $package, $item));
        if ($group === null) {
            Log::warning('[shop] bridge invalid group for item='.$item->id);

            return;
        }

        $entitlementKey = $group;
        $version = $this->dispatcher->nextGrantVersion($uuid, $entitlementKey);

        if ($this->isRevokeTrigger($trigger)) {
            $this->dispatcher->revokeGroup(new GroupRevokeRequest(
                $operationKey,
                $serverId,
                $uuid,
                $group,
                $entitlementKey,
                $version,
                paymentItemId: $item->id,
            ));

            return;
        }

        $months = BridgeExpiry::resolveMonths($package, $item, (string) ($action['duration'] ?? 'package'));
        $expires = BridgeExpiry::absoluteEnd(BridgeExpiry::currentEnd($user, $package), $months);

        $this->dispatcher->grantGroup(new GroupGrantRequest(
            $operationKey,
            $serverId,
            $uuid,
            $group,
            $expires->toAtomString(),
            $entitlementKey,
            $version,
            paymentItemId: $item->id,
        ));
    }

    private function isRevokeTrigger(string $trigger): bool
    {
        return in_array($trigger, ['expiration', 'refund', 'chargeback', 'revoke'], true);
    }

    private function playerUuid(?User $user): ?string
    {
        $raw = strtolower(str_replace('-', '', (string) $user?->game_id));
        if (! preg_match('/^[0-9a-f]{32}$/', $raw)) {
            return null;
        }

        return substr($raw, 0, 8).'-'.substr($raw, 8, 4).'-'.substr($raw, 12, 4).'-'.substr($raw, 16, 4).'-'.substr($raw, 20);
    }

    private function replaceTokens(string $value, Package $package, PaymentItem $item): string
    {
        $replaced = str_replace([
            '{quantity}', '{package_id}', '{package_name}', '{price}', '{transaction_id}', '{rank_name}',
        ], [
            $item->quantity, $package->id, Str::slug((string) $package->name) ?: 'package-'.$package->id,
            $item->price, $item->payment?->transaction_id, Str::slug((string) $package->name) ?: 'package',
        ], $value);

        return method_exists($item, 'replaceVariables') ? $item->replaceVariables($replaced) : $replaced;
    }

    public static function normalizeGroup(string $group): ?string
    {
        $normalized = strtolower(trim($group));
        if ($normalized === '' || strlen($normalized) > 36) {
            return null;
        }
        if (! preg_match('/^[a-z0-9._-]+$/', $normalized)) {
            return null;
        }

        return $normalized;
    }
}
