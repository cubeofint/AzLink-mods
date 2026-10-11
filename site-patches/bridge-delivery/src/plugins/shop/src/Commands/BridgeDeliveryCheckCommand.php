<?php

namespace Azuriom\Plugin\Shop\Commands;

use Azuriom\Models\Server;
use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Services\BridgeExpiry;
use Azuriom\Plugin\Shop\Services\ConfiguredDeliveryService;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Schema;

/**
 * Read-only dry-run of the bridge-delivery site patch. Does not write.
 *
 *   php artisan shop:bridge-delivery-check
 */
class BridgeDeliveryCheckCommand extends Command
{
    protected $signature = 'shop:bridge-delivery-check';

    protected $description = 'Dry-run check of shop privilege/kit bridge delivery (no writes)';

    public function handle(): int
    {
        $ok = true;

        $this->line('[info] shop.configured_delivery.enabled = '.(ConfiguredDeliveryService::enabled() ? '1' : '0'));
        $raw = ConfiguredDeliveryServers::raw();
        $ids = ConfiguredDeliveryServers::ids();
        $this->line('[info] shop.configured_delivery.servers = '.($raw === '' ? '(empty — nobody)' : $raw));

        if ($ids === []) {
            $this->line('[WARN] no servers listed; tick() and dispatcher will no-op');
        }

        foreach ($ids as $id) {
            $server = Server::query()->find($id);
            $this->line(($server ? '[ok]  ' : '[MISS] ').'server #'.$id.($server ? ' '.$server->name : ''));
            $ok = $ok && $server !== null;
        }

        foreach (['shop_minecraft_operations', 'shop_subscriptions'] as $table) {
            $exists = Schema::hasTable($table);
            $this->line(($exists ? '[ok]  ' : '[MISS] ').'table '.$table);
            $ok = $ok && ($table === 'shop_subscriptions' || $exists);
        }
        $this->line((Schema::hasTable('user_package_states') ? '[ok]  ' : '[info] ').'table user_package_states'
            .(Schema::hasTable('user_package_states') ? '' : ' (optional)'));

        $routes = [
            'shop.admin.deliveries.index' => Route::has('shop.admin.deliveries.index'),
            'shop.admin.deliveries.retry' => Route::has('shop.admin.deliveries.retry'),
            'shop.admin.deliveries.mark' => Route::has('shop.admin.deliveries.mark'),
        ];
        foreach ($routes as $name => $present) {
            $this->line(($present ? '[ok]  ' : '[MISS] ').'route '.$name);
            $ok = $ok && $present;
        }

        $bridgeGroups = 0;
        $packages = Package::query()->get(['id', 'name', 'commands', 'billing_period']);
        foreach ($packages as $package) {
            foreach ((array) $package->commands as $group) {
                if (! is_array($group) || ($group['mode'] ?? '') !== 'bridge') {
                    continue;
                }
                $bridgeGroups++;
                $serverId = (int) ($group['server'] ?? 0);
                $listed = ConfiguredDeliveryServers::allows($serverId);
                $this->line(($listed ? '[ok]  ' : '[WARN] ').'package #'.$package->id.' '.$package->name
                    .' bridge server='.$serverId
                    .($listed ? '' : ' not in configured_delivery.servers (will skip)')
                    .' months='.BridgeExpiry::monthsFromPackage($package));
            }
        }
        $this->line('[info] bridge command groups: '.$bridgeGroups);

        $this->line('[info] cointcore required API: KitCreditService.addCredits(UUID, String, int) — kit credits never expire');
        $this->line('[info] group_grant expires_at is absolute ISO-8601; kit_credit has no valid_until');

        $this->info($ok ? 'bridge-delivery check passed' : 'bridge-delivery check found problems');

        return $ok ? self::SUCCESS : self::FAILURE;
    }
}
