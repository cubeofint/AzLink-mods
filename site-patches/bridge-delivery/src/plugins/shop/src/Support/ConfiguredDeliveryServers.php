<?php

namespace Azuriom\Plugin\Shop\Support;

/**
 * CSV of Azuriom server ids in shop.configured_delivery.servers.
 * Empty list means nobody — tick() and the dispatcher do not act.
 */
final class ConfiguredDeliveryServers
{
    public static function raw(): string
    {
        return trim((string) setting('shop.configured_delivery.servers', ''));
    }

    /**
     * @return list<int>
     */
    public static function ids(?string $raw = null): array
    {
        $value = $raw ?? self::raw();
        if ($value === '') {
            return [];
        }

        $ids = [];
        foreach (explode(',', $value) as $part) {
            $id = (int) trim($part);
            if ($id > 0) {
                $ids[$id] = $id;
            }
        }

        return array_values($ids);
    }

    public static function allows(int $serverId, ?string $raw = null): bool
    {
        return in_array($serverId, self::ids($raw), true);
    }
}
