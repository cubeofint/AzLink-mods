<?php

namespace Azuriom\Plugin\Shop\Commands;

use Azuriom\Plugin\Shop\Services\ConfiguredDeliveryService;
use Azuriom\Plugin\Shop\Support\ConfiguredDeliveryServers;
use Illuminate\Console\Command;

class ConfiguredDeliveryCommand extends Command
{
    protected $signature = 'shop:configured-delivery';

    protected $description = 'Dispatch, track and expire in-game delivery of configured privilege purchases';

    public function handle(ConfiguredDeliveryService $delivery): int
    {
        if (! ConfiguredDeliveryService::enabled()) {
            $this->line('shop.configured_delivery.enabled is off');

            return self::SUCCESS;
        }

        if (ConfiguredDeliveryServers::ids() === []) {
            $this->line('shop.configured_delivery.servers is empty — nobody');

            return self::SUCCESS;
        }

        $stats = $delivery->tick();
        $this->info("dispatched={$stats['dispatched']} refreshed={$stats['refreshed']} expired={$stats['expired']} revoked={$stats['revoked']}");

        return self::SUCCESS;
    }
}
