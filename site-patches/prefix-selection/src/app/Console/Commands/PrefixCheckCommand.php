<?php

namespace Azuriom\Console\Commands;

use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\PrefixOperation;
use Azuriom\Models\UserServerPrefix;
use Azuriom\Services\Prefix\PrefixFormatter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\Facades\Schema;

/**
 * Read-only dry-run: schema, flag, routes, formatter samples. Does not write.
 *
 *   php artisan prefixes:check
 */
class PrefixCheckCommand extends Command
{
    protected $signature = 'prefixes:check';

    protected $description = 'Dry-run check of the prefix-selection site patch (no writes)';

    public function handle(PrefixFormatter $formatter): int
    {
        $ok = true;

        $tables = ['prefix_catalog', 'user_server_prefixes', 'prefix_operations', 'prefix_catalog_imports'];
        foreach ($tables as $table) {
            $exists = Schema::hasTable($table);
            $this->line(($exists ? '[ok]  ' : '[MISS] ').'table '.$table);
            $ok = $ok && $exists;
        }

        foreach (['server_id', 'auto_display_name', 'auto_prefix_raw', 'auto_sort', 'last_seen_at', 'missing_since'] as $column) {
            $exists = Schema::hasColumn('prefix_catalog', $column);
            $this->line(($exists ? '[ok]  ' : '[MISS] ').'prefix_catalog.'.$column);
            $ok = $ok && $exists;
        }

        $enabled = PrefixSelection::enabled();
        $this->line('[info] prefixes.enabled = '.($enabled ? '1' : '0').' (endpoints 404 while off)');
        $this->line('[info] prefixes.lease_seconds = '.(setting('prefixes.lease_seconds', 30)));

        $routes = [
            'azlink prefixes report' => $this->findPath('prefixes/report'),
            'azlink prefixes catalog' => $this->findPath('prefixes/catalog'),
            'azlink prefixes operations' => $this->findPath('prefixes/operations'),
            'azlink prefixes ack' => $this->findPath('prefixes/ack'),
            'profile.prefixes.select' => Route::has('profile.prefixes.select'),
            'admin.prefixes.index' => Route::has('admin.prefixes.index'),
            'admin.prefixes.operations' => Route::has('admin.prefixes.operations'),
        ];

        foreach ($routes as $label => $present) {
            $this->line(($present ? '[ok]  ' : '[MISS] ').'route '.$label);
            $ok = $ok && (bool) $present;
        }

        if (! Route::has('profile.prefixes.select')) {
            $this->warn('Add App\\Providers\\PrefixSelectionServiceProvider::class to bootstrap/providers.php,');
            $this->warn('or keep the POST /api/profile/prefixes/select route from the api.php hunk.');
        }

        $samples = [
            '&a[VIP] &fNick' => 'legacy',
            '<gold>[VIP]</gold> <white>Nick' => 'minimessage',
            '&#FFAA00[Hex]' => 'legacy-hex',
        ];
        foreach ($samples as $raw => $kind) {
            $html = $formatter->toHtml($raw);
            $this->line('[preview:'.$kind.'] '.$raw.' → '.$html);
            if ($html === '' || str_contains($html, '<script')) {
                $this->error('formatter produced empty or unsafe HTML for '.$kind);
                $ok = false;
            }
        }

        if ($ok && Schema::hasTable('prefix_catalog')) {
            $this->line('[info] catalog rows: '.PrefixCatalog::query()->count());
            $this->line('[info] catalog imports: '.(Schema::hasTable('prefix_catalog_imports') ? PrefixCatalogImport::query()->count() : 0));
            $this->line('[info] user_server_prefixes rows: '.UserServerPrefix::query()->count());
            $this->line('[info] prefix_operations rows: '.PrefixOperation::query()->count());
        }

        if (! $ok) {
            $this->error('prefixes:check found problems (see MISS lines). No data was written.');

            return self::FAILURE;
        }

        $this->info('prefixes:check passed (read-only).');

        return self::SUCCESS;
    }

    private function findPath(string $needle): bool
    {
        foreach (Route::getRoutes() as $route) {
            if (str_contains($route->uri(), $needle)) {
                return true;
            }
        }

        return false;
    }
}
