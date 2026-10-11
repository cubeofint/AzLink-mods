<?php

namespace Azuriom\Plugin\Shop\Data;

readonly class GroupGrantRequest
{
    public function __construct(
        public string $operationKey,
        public int $serverId,
        public string $playerUuid,
        public string $group,
        public ?string $expiresAt,
        public string $entitlementKey,
        public int $version,
        public ?int $purchaseId = null,
        public ?int $paymentItemId = null,
    ) {
    }
}
