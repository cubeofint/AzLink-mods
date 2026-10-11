<?php

// Add to ShopServiceProvider::boot() commands list:
\Azuriom\Plugin\Shop\Commands\BridgeDeliveryCheckCommand::class,

// Refund / chargeback → privilege_revoke for configured purchases that carry payment_id.
\Illuminate\Support\Facades\Event::listen(
    'eloquent.updated: '.\Azuriom\Plugin\Shop\Models\Payment::class,
    function (\Azuriom\Plugin\Shop\Models\Payment $payment): void {
        if (! in_array($payment->status, ['refunded', 'chargeback'], true)) {
            return;
        }
        if (! \Azuriom\Plugin\Shop\Services\ConfiguredDeliveryService::enabled()) {
            return;
        }
        $table = (new \Azuriom\Plugin\Shop\Models\ConfiguredPrivilegePurchase())->getTable();
        if (! \Illuminate\Support\Facades\Schema::hasColumn($table, 'payment_id')) {
            return;
        }
        \Azuriom\Plugin\Shop\Models\ConfiguredPrivilegePurchase::query()
            ->where('payment_id', $payment->id)
            ->get()
            ->each(function (\Azuriom\Plugin\Shop\Models\ConfiguredPrivilegePurchase $purchase): void {
                $purchase->update([
                    'status' => \Azuriom\Plugin\Shop\Enums\ConfiguredPurchaseStatus::Refunded,
                ]);
                try {
                    app(\Azuriom\Plugin\Shop\Services\ConfiguredDeliveryService::class)->revokePurchase($purchase);
                } catch (\Throwable $e) {
                    \Illuminate\Support\Facades\Log::warning('[shop] refund revoke failed: '.$e->getMessage());
                }
            });
    }
);

// Admin nav label: «Доставки» (was «Выдача в игре»)
// 'name' => 'Доставки',
