<?php

namespace Azuriom\Plugin\Shop\Data;

readonly class GroupRevokeRequest
{
    public function __construct(
        public string $operationKey,
        public int $serverId,
        public string $playerUuid,
        public string $group,
        public string $entitlementKey,
        public int $version,
        public ?int $purchaseId = null,
        public ?int $paymentItemId = null,
    ) {
    }
}
