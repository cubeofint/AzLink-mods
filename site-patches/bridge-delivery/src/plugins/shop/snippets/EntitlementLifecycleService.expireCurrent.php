<?php

// In EntitlementLifecycleService::expireCurrent, replace the successor status check:

if ($successor !== null) {
    $this->assertAdjacent($current, $successor);
    if (! in_array($successor->status, [
        ConfiguredPurchaseStatus::Scheduled,
        ConfiguredPurchaseStatus::PendingActivation,
    ], true)) {
        throw new LogicException('Expiration successor is not scheduled or pending activation.');
    }
    if ($successor->status === ConfiguredPurchaseStatus::Scheduled) {
        $successor->update(['status' => ConfiguredPurchaseStatus::PendingActivation]);
    }
    $state = $this->purchaseState($successor, [
        'status' => UserPrivilegeEntitlementStatus::PendingActivation,
        'version' => $entitlement->version + 1,
    ]);
}
