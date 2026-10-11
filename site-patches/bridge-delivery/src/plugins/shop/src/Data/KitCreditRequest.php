<?php

namespace Azuriom\Plugin\Shop\Data;

readonly class KitCreditRequest
{
    public function __construct(
        public string $operationKey,
        public int $serverId,
        public string $playerUuid,
        public string $kit,
        public int $qty,
        public ?int $purchaseId = null,
        public ?int $paymentItemId = null,
    ) {
    }
}
