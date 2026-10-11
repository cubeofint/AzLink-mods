<?php

/**
 * Standalone preview of PrefixFormatter (no Laravel).
 *
 *   php site-patches/prefix-selection/check-formatter.php
 */

require __DIR__.'/src/app/Services/Prefix/PrefixFormatter.php';

$formatter = new Azuriom\Services\Prefix\PrefixFormatter();

$samples = [
    '&a[VIP] &fNick',
    '<gold>[VIP]</gold> <white>Nick',
    '&#FFAA00[Hex]',
    '&x&f&f&a&a&0&0[Hex2]',
    '<red><bold>A</bold>B</red>',
    '<red>a<script>alert(1)</script>b</red>',
];

$failed = 0;
foreach ($samples as $raw) {
    $html = $formatter->toHtml($raw);
    $ok = $html !== '' && ! str_contains($html, '<script');
    echo ($ok ? '[ok]  ' : '[FAIL] ').$raw.' → '.$html.PHP_EOL;
    $failed += $ok ? 0 : 1;
}

exit($failed === 0 ? 0 : 1);
