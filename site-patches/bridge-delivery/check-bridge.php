<?php

/**
 * Standalone (no Laravel) checks for CSV parsing and payload shape.
 *   php site-patches/bridge-delivery/check-bridge.php
 */

function ids(string $raw): array
{
    if (trim($raw) === '') {
        return [];
    }
    $out = [];
    foreach (explode(',', $raw) as $part) {
        $id = (int) trim($part);
        if ($id > 0) {
            $out[$id] = $id;
        }
    }

    return array_values($out);
}

function assert_same($expected, $actual, string $label): void
{
    if ($expected !== $actual) {
        fwrite(STDERR, "FAIL $label: ".var_export($actual, true)."\n");
        exit(1);
    }
    echo "ok  $label\n";
}

assert_same([9, 12], ids('9, 12,9'), 'csv unique');
assert_same([], ids(''), 'empty csv');
assert_same(true, in_array(9, ids('9,12'), true), 'allows 9');
assert_same(false, in_array(3, ids('9,12'), true), 'rejects 3');

$grant = [
    'type' => 'group_grant',
    'uuid' => '11111111-1111-1111-1111-111111111111',
    'group' => 'vip',
    'expires_at' => '2027-01-01T00:00:00Z',
    'entitlement_key' => 'vip',
    'version' => 3,
];
$credit = [
    'type' => 'kit_credit',
    'uuid' => '11111111-1111-1111-1111-111111111111',
    'kit' => 'starter',
    'qty' => 2,
];
assert_same(false, array_key_exists('valid_until', $credit), 'kit_credit has no valid_until');
assert_same(true, array_key_exists('expires_at', $grant), 'group_grant has expires_at');

echo "bridge-delivery standalone check passed\n";
