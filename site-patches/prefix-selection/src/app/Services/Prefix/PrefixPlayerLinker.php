<?php

namespace Azuriom\Services\Prefix;

use Azuriom\Models\User;

/**
 * Resolve a site user the same way other AzLink endpoints do: game_id (dashed /
 * undashed UUID), then exact player name.
 */
class PrefixPlayerLinker
{
    public function find(?string $uuid, ?string $name): ?User
    {
        $normalized = $this->normalizeUuid((string) $uuid);

        if ($normalized !== null) {
            $candidates = array_unique(array_filter([
                $uuid,
                $normalized,
                str_replace('-', '', $normalized),
            ]));

            $user = User::query()->whereIn('game_id', $candidates)->first();
            if ($user !== null) {
                return $user;
            }
        }

        if (filled($name)) {
            return User::query()->where('name', $name)->first();
        }

        return null;
    }

    public function normalizeUuid(string $value): ?string
    {
        $hex = strtolower(preg_replace('/[^0-9a-f]/', '', $value) ?? '');

        if (strlen($hex) !== 32) {
            return null;
        }

        return sprintf(
            '%s-%s-%s-%s-%s',
            substr($hex, 0, 8),
            substr($hex, 8, 4),
            substr($hex, 12, 4),
            substr($hex, 16, 4),
            substr($hex, 20, 12)
        );
    }
}
