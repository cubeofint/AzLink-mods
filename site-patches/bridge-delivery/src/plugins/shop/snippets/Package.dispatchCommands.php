<?php

// Merge into Azuriom\Plugin\Shop\Models\Package::dispatchCommands
// Existing console-command groups keep sendCommands unchanged.

use Azuriom\Plugin\Shop\Services\BridgePackageDelivery;
use Illuminate\Support\Arr;

public function dispatchCommands(string $trigger, PaymentItem $item): void
{
    $user = $item->payment->user;

    $serverCommands = collect($this->commands)
        ->merge(($json = setting('shop.commands')) ? json_decode($json, true) : [])
        ->filter(fn (mixed $command) => is_array($command))
        ->filter(fn (array $command) => $command['trigger'] === $trigger)
        ->map(function (array $command) use ($item) {
            $server = $command['server'];

            if (! is_numeric($server)) {
                $command['server'] = Arr::get($item->variables ?? [], $server);
            }

            return $command;
        })
        ->filter(fn (array $command) => $command['server'] !== null);

    $bridge = $serverCommands->filter(fn (array $command) => ($command['mode'] ?? '') === 'bridge');
    $classic = $serverCommands->reject(fn (array $command) => ($command['mode'] ?? '') === 'bridge');

    if ($bridge->isNotEmpty()) {
        app(BridgePackageDelivery::class)->dispatch($this, $item, $trigger, $bridge);
    }

    $servers = \Azuriom\Models\Server::findMany($classic->pluck('server')->unique());

    foreach ($servers as $server) {
        $commands = $classic->where('server', $server->id);

        $onlineCommands = $this->mapCommands($commands, true, $item);
        $offlineCommands = $this->mapCommands($commands, false, $item);

        if (! empty($onlineCommands)) {
            $server->bridge()->sendCommands($onlineCommands, $user, true);
        }

        if (! empty($offlineCommands)) {
            $server->bridge()->sendCommands($offlineCommands, $user, false);
        }
    }
}
