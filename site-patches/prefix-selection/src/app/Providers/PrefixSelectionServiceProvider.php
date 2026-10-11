<?php

namespace Azuriom\Providers;

use Azuriom\Http\Controllers\ProfilePrefixController;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\ServiceProvider;

/**
 * Registers the cabinet POST /user/prefixes/select route on the web stack.
 * The same action is also wired in routes/api.php (auth+web) so the form works
 * even if this provider is not listed in bootstrap/providers.php.
 */
class PrefixSelectionServiceProvider extends ServiceProvider
{
    public function boot(): void
    {
        if ($this->app->routesAreCached()) {
            return;
        }

        Route::middleware(['web', 'auth'])
            ->prefix('user')
            ->name('profile.')
            ->group(function () {
                if (! Route::has('profile.prefixes.select')) {
                    Route::post('/prefixes/select', [ProfilePrefixController::class, 'select'])
                        ->name('prefixes.select');
                }
            });
    }
}
