<?php

namespace Azuriom\Plugin\Shop\Services;

use Azuriom\Models\User;
use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Models\PaymentItem;
use Carbon\CarbonInterface;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Schema;

/**
 * Absolute privilege expiry: max(current end, now) + N calendar months.
 * Current end is taken from shop_subscriptions.ends_at or user_package_states.
 */
final class BridgeExpiry
{
    public static function addMonths(CarbonInterface $from, int $months): Carbon
    {
        $start = $from->greaterThan(now()) ? $from->toImmutable() : now();

        return Carbon::parse($start)->addMonthsNoOverflow(max(0, $months));
    }

    public static function absoluteEnd(?CarbonInterface $currentEnd, int $months): Carbon
    {
        return self::addMonths($currentEnd ?? now(), $months);
    }

    public static function currentEnd(User $user, Package $package): ?Carbon
    {
        $fromSubscription = self::subscriptionEnd($user, $package);
        $fromState = self::packageStateEnd($user, $package);
        $candidates = array_values(array_filter([$fromSubscription, $fromState]));
        if ($candidates === []) {
            return null;
        }

        return collect($candidates)->sort()->last();
    }

    public static function monthsFromPackage(Package $package): int
    {
        if (method_exists($package, 'subscriptionPeriodCount') && $package->subscriptionPeriodCount() > 0) {
            $count = $package->subscriptionPeriodCount();
            $unit = $package->subscriptionPeriodUnit();

            return match ($unit) {
                'year', 'years' => $count * 12,
                'week', 'weeks' => max(1, (int) ceil($count / 4)),
                'day', 'days' => max(1, (int) ceil($count / 30)),
                default => $count,
            };
        }

        $period = (string) ($package->billing_period ?? '');
        if ($period === '') {
            return 1;
        }

        $count = (int) \Illuminate\Support\Str::before($period, ' ');
        $unit = \Illuminate\Support\Str::after($period, ' ');

        return match ($unit) {
            'year', 'years' => max(1, $count) * 12,
            'week', 'weeks' => max(1, (int) ceil(max(1, $count) / 4)),
            'day', 'days' => max(1, (int) ceil(max(1, $count) / 30)),
            default => max(1, $count),
        };
    }

    public static function resolveMonths(Package $package, PaymentItem $item, string $duration): int
    {
        $duration = trim($duration);
        if ($duration === '' || $duration === 'package') {
            return self::monthsFromPackage($package);
        }
        if ($duration === '{duration_months}') {
            $fromItem = data_get($item->variables ?? [], 'duration_months');
            if (is_numeric($fromItem)) {
                return max(1, (int) $fromItem);
            }

            return self::monthsFromPackage($package);
        }
        if (is_numeric($duration)) {
            return max(1, (int) $duration);
        }

        return self::monthsFromPackage($package);
    }

    public static function resolveQty(Package $package, PaymentItem $item, string $qty): int
    {
        $qty = trim($qty);
        if ($qty === '' || $qty === '{quantity}') {
            return max(1, (int) $item->quantity);
        }
        if ($qty === '{duration_months}') {
            return self::monthsFromPackage($package);
        }
        if (is_numeric($qty)) {
            return max(1, (int) $qty);
        }

        return max(1, (int) $item->quantity);
    }

    private static function subscriptionEnd(User $user, Package $package): ?Carbon
    {
        if (! Schema::hasTable('shop_subscriptions')) {
            return null;
        }

        $end = \Illuminate\Support\Facades\DB::table('shop_subscriptions')
            ->where('user_id', $user->id)
            ->where('package_id', $package->id)
            ->whereIn('status', ['active', 'canceled'])
            ->orderByDesc('ends_at')
            ->value('ends_at');

        return $end !== null ? Carbon::parse($end) : null;
    }

    private static function packageStateEnd(User $user, Package $package): ?Carbon
    {
        if (! Schema::hasTable('user_package_states')) {
            return null;
        }

        $row = \Illuminate\Support\Facades\DB::table('user_package_states')
            ->where('user_id', $user->id)
            ->where('package_id', $package->id)
            ->orderByDesc('id')
            ->first();
        if ($row === null) {
            return null;
        }

        foreach (['ends_at', 'expires_at', 'valid_until'] as $column) {
            if (isset($row->{$column}) && $row->{$column} !== null) {
                return Carbon::parse($row->{$column});
            }
        }

        return null;
    }
}
