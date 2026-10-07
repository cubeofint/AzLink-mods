<?php

use Azuriom\Http\Controllers\Admin\ActionLogController;
use Azuriom\Http\Controllers\Admin\AdminController;
use Azuriom\Http\Controllers\Admin\BanController;
use Azuriom\Http\Controllers\Admin\HwidBanController;
use Azuriom\Http\Controllers\Admin\HwidController;
use Azuriom\Http\Controllers\Admin\ImageController;
use Azuriom\Http\Controllers\Admin\NavbarController;
use Azuriom\Http\Controllers\Admin\PageAttachmentController;
use Azuriom\Http\Controllers\Admin\PageController;
use Azuriom\Http\Controllers\Admin\PluginController;
use Azuriom\Http\Controllers\Admin\PostAttachmentController;
use Azuriom\Http\Controllers\Admin\PostController;
use Azuriom\Http\Controllers\Admin\PostCategoryController;
use Azuriom\Http\Controllers\Admin\RedirectController;
use Azuriom\Http\Controllers\Admin\RoleController;
use Azuriom\Http\Controllers\Admin\ServerController;
use Azuriom\Http\Controllers\Admin\SettingsController;
use Azuriom\Http\Controllers\Admin\SocialLinkController;
use Azuriom\Http\Controllers\Admin\ThemeController;
use Azuriom\Http\Controllers\Admin\UpdateController;
use Azuriom\Http\Controllers\Admin\CurrencyTransactionController;
use Azuriom\Http\Controllers\Admin\FinanceController;
use Azuriom\Http\Controllers\Admin\FinanceExpenseController;
use Azuriom\Http\Controllers\Admin\MoneyGrantController;
use Azuriom\Http\Controllers\Admin\UserController;
use Illuminate\Support\Facades\Route;

Route::get('/', [AdminController::class, 'index'])->name('dashboard');

Route::prefix('settings')->name('settings.')->middleware('can:admin.settings')->group(function () {
    Route::get('/', [SettingsController::class, 'index'])->name('index');
    Route::post('/update', [SettingsController::class, 'update'])->name('update');

    Route::post('/cache/clear', [SettingsController::class, 'clearCache'])->name('cache.clear');
    Route::post('/cache/advanced/enable', [SettingsController::class, 'enableAdvancedCache'])->name('cache.advanced.enable');
    Route::post('/cache/advanced/clear', [SettingsController::class, 'disableAdvancedCache'])->name('cache.advanced.clear');

    Route::get('/performance', [SettingsController::class, 'performance'])->name('performance');
    Route::get('/storage/link', [SettingsController::class, 'linkStorage'])->name('link-storage');
    Route::get('/migrate', [SettingsController::class, 'migrate'])->name('migrate');

    Route::get('/home', [SettingsController::class, 'home'])->name('home');
    Route::post('/home/update', [SettingsController::class, 'updateSeo'])->name('home.update');

    Route::get('/auth', [SettingsController::class, 'auth'])->name('auth');
    Route::post('/auth/update', [SettingsController::class, 'updateauth'])->name('auth.update');
    Route::post('/security/update', [SettingsController::class, 'updateSecurity'])->name('security.update');

    Route::get('/mail', [SettingsController::class, 'mail'])->name('mail');
    Route::post('/mail/update', [SettingsController::class, 'updateMail'])->name('mail.update');
    Route::post('/mail/test', [SettingsController::class, 'sendTestMail'])->name('mail.send');

    Route::get('/maintenance', [SettingsController::class, 'maintenance'])->name('maintenance');
    Route::post('/maintenance/update', [SettingsController::class, 'updateMaintenance'])->name('maintenance.update');

    Route::get('/twitch', [SettingsController::class, 'twitch'])->name('twitch');
    Route::post('/twitch/update', [SettingsController::class, 'updateTwitch'])->name('twitch.update');

    Route::get('/telegram', [SettingsController::class, 'telegram'])->name('telegram');
    Route::post('/telegram/update', [SettingsController::class, 'updateTelegram'])->name('telegram.update');
});

Route::prefix('users')->name('users.')->middleware('can:admin.users')->group(function () {
    Route::post('/{user}/verify', [UserController::class, 'verifyEmail'])->name('verify');
    Route::post('/{user}/2fa', [UserController::class, 'disable2fa'])->name('2fa');
    Route::post('/{user}/password/force', [UserController::class, 'forcePasswordChange'])->name('force-password');
    Route::post('/{user}/discord/unlink', [UserController::class, 'unlinkDiscord'])->name('discord.unlink');
    Route::post('/{user}/telegram/unlink', [UserController::class, 'unlinkTelegram'])->name('telegram.unlink');
    Route::post('/{user}/hwid/ban', [HwidBanController::class, 'store'])->name('hwid.ban');
    Route::delete('/{user}/hwid/ban', [HwidBanController::class, 'destroy'])->name('hwid.unban');
});

Route::prefix('themes')->name('themes.')->middleware('can:admin.themes')->group(function () {
    Route::get('/', [ThemeController::class, 'index'])->name('index');
    Route::post('/reload', [ThemeController::class, 'reload'])->name('reload');
    Route::post('/change/{theme?}', [ThemeController::class, 'changeTheme'])->name('change');
    Route::prefix('/{theme}/config')->group(function () {
        Route::get('/', [ThemeController::class, 'edit'])->name('edit');
        Route::post('/', [ThemeController::class, 'config'])->name('config');
    });
    Route::post('/{theme}/update', [ThemeController::class, 'update'])->name('update');
    Route::post('/{themeId}/download', [ThemeController::class, 'download'])->name('download');
    Route::delete('/{theme}', [ThemeController::class, 'delete'])->name('delete');
});

Route::prefix('plugins')->name('plugins.')->middleware('can:admin.plugins')->group(function () {
    Route::get('/', [PluginController::class, 'index'])->name('index');
    Route::post('/reload', [PluginController::class, 'reload'])->name('reload');
    Route::post('/{plugin}/enable', [PluginController::class, 'enable'])->name('enable');
    Route::post('/{plugin}/disable', [PluginController::class, 'disable'])->name('disable');
    Route::post('/{plugin}/update', [PluginController::class, 'update'])->name('update');
    Route::post('/{pluginId}/download', [PluginController::class, 'download'])->name('download');
    Route::delete('/{plugin}', [PluginController::class, 'delete'])->name('delete');
});

Route::prefix('update')->name('update.')->middleware('can:admin.update')->group(function () {
    Route::get('/', [UpdateController::class, 'index'])->name('index');
    Route::get('/version', [UpdateController::class, 'version'])->name('version');
    Route::post('/fetch', [UpdateController::class, 'fetch'])->name('fetch');
    Route::post('/download', [UpdateController::class, 'download'])->name('download');
    Route::post('/install', [UpdateController::class, 'install'])->name('install');
});

Route::resource('navbar-elements', NavbarController::class)->except('show')->middleware('can:admin.navbar');
Route::post('/navbar-elements/order', [NavbarController::class, 'updateOrder'])->name('navbar-elements.update-order')->middleware('can:admin.navbar');

Route::resource('social-links', SocialLinkController::class)->except('show');
Route::post('/social-links/order', [SocialLinkController::class, 'updateOrder'])->name('social-links.update-order');

Route::resource('users', UserController::class)->except('show')->middleware(['can:admin.users', 'throttle:20,1']);
Route::post('/users/notify', [UserController::class, 'notify'])->name('users.notify.all')->middleware('can:admin.users');
Route::post('/users/{user}/notify', [UserController::class, 'notify'])->name('users.notify')->middleware('can:admin.users');
Route::post('/users/{user}/coins/transfer', [UserController::class, 'transferCoins'])
    ->name('users.coins.transfer')
    ->middleware(['can:admin.users', 'can:admin.users.money', 'throttle:30,1']);
Route::get('/currency-transactions', [CurrencyTransactionController::class, 'index'])
    ->name('currency-transactions.index')
    ->middleware('can:admin.users');
Route::get('/currency-transactions/server-movements', [\Azuriom\Http\Controllers\Admin\CurrencyServerMovementController::class, 'index'])
    ->name('currency-transactions.server-movements')
    ->middleware('can:admin.users');
Route::prefix('users/money-grants')->name('users.money-grants.')->middleware(['can:admin.users.money', 'throttle:10,1'])->group(function () {
    Route::get('/', [MoneyGrantController::class, 'index'])->name('index');
    Route::post('/', [MoneyGrantController::class, 'store'])->name('store');
});

Route::prefix('finance')->name('finance.')->middleware('can:admin.finance')->group(function () {
    Route::get('/', [FinanceController::class, 'index'])->name('index');
    Route::get('/export', [FinanceController::class, 'export'])->name('export');
    Route::post('/bank/refresh', [FinanceController::class, 'refreshBank'])->name('bank.refresh');
    Route::get('/settings', [FinanceController::class, 'settings'])->name('settings');
    Route::post('/settings', [FinanceController::class, 'updateSettings'])->name('settings.update');

    Route::post('/expenses/{expense}/toggle-done', [FinanceExpenseController::class, 'toggleDone'])
        ->name('expenses.toggle-done');
    Route::resource('expenses', FinanceExpenseController::class)->except('show');
});

Route::resource('roles', RoleController::class)->except('show')->middleware('can:admin.roles');
Route::post('/roles/power', [RoleController::class, 'updatePower'])->name('roles.update-power')->middleware('can:admin.roles');
Route::post('/roles/settings', [RoleController::class, 'updateSettings'])->name('roles.settings')->middleware('can:admin.roles');

Route::resource('bans', BanController::class)->only('index')->middleware('can:admin.users');
Route::resource('users.bans', BanController::class)->only(['store', 'destroy'])->middleware('can:admin.users');
Route::get('/hwids', [HwidController::class, 'index'])->name('hwids.index')->middleware('can:admin.users');
Route::post('/hwids/{hwid}/ban', [HwidController::class, 'ban'])->name('hwids.ban')->middleware('can:admin.users');
Route::delete('/hwids/{hwid}/ban', [HwidController::class, 'unban'])->name('hwids.unban')->middleware('can:admin.users');

Route::resource('pages', PageController::class)->except('show')->middleware('can:admin.pages');
Route::resource('posts', PostController::class)->except('show')->middleware('can:admin.posts');
Route::resource('images', ImageController::class)->except('show')->middleware('can:admin.images');
Route::resource('redirects', RedirectController::class)->except('show')->middleware('can:admin.redirects');

Route::resource('pages.attachments', PageAttachmentController::class)->only('store');
Route::resource('posts.attachments', PostAttachmentController::class)->only('store');
Route::resource('categories', PostCategoryController::class)->except('show');
Route::post('pages/attachments/{pendingId}', [PageAttachmentController::class, 'pending'])->name('pages.attachments.pending');
Route::post('posts/attachments/{pendingId}', [PostAttachmentController::class, 'pending'])->name('posts.attachments.pending');

Route::middleware('can:admin.servers')->group(function () {
    Route::resource('servers', ServerController::class)->except('show');
    Route::post('/servers/{server}/verify/azlink', [ServerController::class, 'verifyAzLink'])->name('servers.verify-azlink');
    Route::post('/servers/{server}/regenerate/azlink', [ServerController::class, 'regenerateAzLink'])->name('servers.regenerate-azlink');
    Route::post('/servers/default', [ServerController::class, 'changeDefault'])->name('servers.change-default');
    Route::post('/servers/update-order', [ServerController::class, 'updateOrder'])->name('servers.update-order');
});

Route::post('logs/clear', [ActionLogController::class, 'clear'])->name('logs.clear')->middleware('can:admin.logs');
Route::resource('logs', ActionLogController::class)->only(['index', 'show'])->middleware('can:admin.logs');

Route::fallback([AdminController::class, 'fallback']);
