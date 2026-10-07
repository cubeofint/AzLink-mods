<?php

namespace Azuriom\Http\Controllers;

use Azuriom\Models\ActionLog;
use Azuriom\Models\CurrencyTransaction;
use Azuriom\Models\Server;
use Azuriom\Models\ServerPlayerStat;
use Azuriom\Models\TelegramAccount;
use Azuriom\Models\User;
use Azuriom\Notifications\AlertNotification;
use Azuriom\Notifications\UserDelete;
use Azuriom\Rules\TrustedEmailDomain;
use Azuriom\Rules\Username;
use Azuriom\Services\Currency\CurrencyLedger;
use Azuriom\Support\Discord\LinkedRoles;
use Azuriom\Support\QrCodeRenderer;
use Azuriom\Support\Telegram\TelegramLoginVerifier;
use Illuminate\Auth\Events\PasswordReset;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Arr;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\HtmlString;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Password;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;
use Laravel\Socialite\Facades\Socialite;
use PragmaRX\Google2FA\Google2FA;
use RuntimeException;
use Throwable;

class ProfileController extends Controller
{
    public function __construct()
    {
        $this->middleware(function (Request $request, callable $next) {
            abort_if(! setting('user.delete'), 404);

            return $next($request);
        })->only(['showDelete', 'showDeleteConfirm', 'sendDelete', 'confirmDelete']);
    }

    /**
     * Show the user profile.
     */
    public function index(Request $request)
    {
        $user = $request->user();
        $discordLink = setting('discord.link_roles', false);
        $emailVerification = setting('mail.users_email_verification', false);
        $shopSubscriptions = collect();

        // Shop может быть отключен, поэтому не дергаем его модели без проверки.
        if (class_exists('Azuriom\\Plugin\\Shop\\Models\\Subscription')) {
            try {
                $subscriptionModel = 'Azuriom\\Plugin\\Shop\\Models\\Subscription';

                $shopSubscriptions = $subscriptionModel::notPending()
                    ->whereBelongsTo($user)
                    ->with('package')
                    ->latest()
                    ->get();
            } catch (Throwable) {
                // Если таблица/плагин недоступны, профиль должен открываться без ошибок.
            }
        }

        $forumProfile = null;

        if (plugins()->isEnabled('forum') && class_exists('Azuriom\\Plugin\\Forum\\Models\\ForumUser')) {
            try {
                $forumUserModel = 'Azuriom\\Plugin\\Forum\\Models\\ForumUser';
                $forumProfile = $forumUserModel::query()->firstOrCreate([
                    'user_id' => $user->id,
                ]);
            } catch (Throwable) {
                $forumProfile = null;
            }
        }

        return view('profile.index', [
            'user' => $user,
            'canChangeName' => ! oauth_login() && setting('user.change_name', false),
            'canUploadAvatar' => setting('user.upload_avatar', false) && $user->canUploadAvatar(),
            'hasAvatar' => $user->hasUploadedAvatar(),
            'canDelete' => setting('user.delete', false),
            'canVerifyEmail' => $user->email !== null && ! $user->hasVerifiedEmail() && $emailVerification,
            'discordAccount' => $discordLink ? $user->discordAccount : null,
            'enableDiscordLink' => $discordLink,
            'telegramAccount' => $user->telegramAccount,
            'enableTelegramLink' => telegram_link_enabled(),
            'telegramBotUsername' => ltrim((string) setting('telegram.bot_username', ''), '@'),
            'shopSubscriptions' => $shopSubscriptions,
            'forumProfile' => $forumProfile,
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(),
            'balancesByServerId' => $user->serverBalances()->get()->keyBy('server_id'),
            'playerStats' => ServerPlayerStat::query()
                ->where(function ($query) use ($user) {
                    $query->where('user_id', $user->id)
                        ->orWhere('name', $user->name);

                    if (filled($user->game_id)) {
                        $query->orWhere('uuid', $user->game_id);
                    }
                })
                ->with('server:id,name,slug')
                ->orderByDesc('synced_at')
                ->get(),
        ]);
    }

    /**
     * Update the user email address.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function updateEmail(Request $request)
    {
        $this->validate($request, [
            'email_confirm_pass' => oauth_login()
                ? ['sometimes', 'nullable']
                : ['required', 'current_password'],
        ]);

        $user = $request->user();

        if (RateLimiter::tooManyAttempts('email:'.$user->id, 5)) {
            throw ValidationException::withMessages([
                'email' => trans('messages.profile.email_limit'),
            ]);
        }

        RateLimiter::hit('email:'.$user->id, 5 * 60);

        // Only check if email is already used after the rate limit check
        $this->validate($request, [
            'email' => ['required', 'string', 'email', 'max:50', 'unique:users', new TrustedEmailDomain()],
        ]);

        $user->forceFill([
            'email' => $request->input('email'),
            'email_verified_at' => null,
        ])->save();

        $user->sendEmailVerificationNotification();

        return to_route('profile.index')
            ->with('success', trans('messages.profile.updated'));
    }

    /**
     * Update the user password.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function updatePassword(Request $request)
    {
        $this->validate($request, [
            'password_confirm_pass' => ['required', 'current_password'],
            'password' => ['required', 'confirmed', Password::default()],
        ]);

        $password = $request->input('password');
        $user = $request->user();

        $user->update([
            'password' => $password,
            'access_token' => null,
        ]);

        Auth::logoutOtherDevices($password);
        event(new PasswordReset($user));

        return to_route('profile.index')
            ->with('success', trans('messages.profile.updated'));
    }

    public function updateName(Request $request): RedirectResponse
    {
        abort_if(oauth_login() || ! setting('user.change_name'), 403);

        $validated = $this->validate($request, [
            'name' => [
                'required', 'max:25', new Username(),
                Rule::unique('users', 'name')->ignore($request->user()),
            ],
        ]);

        $request->user()->update($validated);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.updated'));
    }

    public function uploadAvatar(Request $request)
    {
        $user = $request->user();

        abort_if(! setting('user.upload_avatar', false) || ! $user->canUploadAvatar(), 403);

        $this->validate($request, [
            'image' => ['required', 'mimes:jpg,jpeg,png,gif', 'dimensions:ratio=1', 'max:2048'],
        ]);

        $user->storeImage($request->file('image'), true);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.updated'));
    }

    public function deleteAvatar(Request $request)
    {
        if ($request->user()->hasUploadedAvatar()) {
            $request->user()->deleteImage(true);
        }

        return to_route('profile.index')
            ->with('success', trans('messages.profile.updated'));
    }

    /**
     * Show the form to enable two-factor authentification.
     *
     * @throws \PragmaRX\Google2FA\Exceptions\Google2FAException
     */
    public function show2fa(Request $request)
    {
        if ($request->user()->hasTwoFactorAuth()) {
            return view('profile.2fa.index', [
                'user' => $request->user(),
                'codesBackupName' => Str::slug(site_name()).'-codes.txt',
            ]);
        }

        $google2fa = new Google2FA();
        $secret = $request->session()->get('2fa.secret', $google2fa->generateSecretKey());
        $accountLabel = filled($request->user()->email) ? $request->user()->email : $request->user()->name;
        $qrCodeUrl = $google2fa->getQRCodeUrl(site_name(), $accountLabel, $secret);

        $request->session()->put('2fa.secret', $secret);

        return view('profile.2fa.enable', [
            'secret' => $secret,
            'qrCode' => new HtmlString(QrCodeRenderer::render($qrCodeUrl, 250)),
        ]);
    }

    public function download2faCodes(Request $request)
    {
        abort_if(! $request->user()->hasTwoFactorAuth(), 404);

        $codes = $request->user()->two_factor_recovery_codes;

        return new Response(Arr::join($codes, "\n"), 200, [
            'Content-Disposition' => 'attachment',
            'Content-Type' => 'text/plain',
        ]);
    }

    /**
     * Enable two-factor authentification for this user.
     *
     * @throws \Illuminate\Validation\ValidationException
     * @throws \PragmaRX\Google2FA\Exceptions\Google2FAException
     */
    public function enable2fa(Request $request)
    {
        $this->validate($request, [
            'code' => ['required', 'string'],
        ]);

        if ($request->user()->hasTwoFactorAuth()) {
            return to_route('profile.2fa.index');
        }

        $code = Str::remove(' ', $request->input('code'));
        $secret = $request->session()->get('2fa.secret');

        if (! $secret || ! (new Google2FA())->verifyKey($secret, $code)) {
            throw ValidationException::withMessages(['code' => trans('auth.2fa.invalid')]);
        }

        $request->user()->forceFill([
            'two_factor_secret' => $secret,
            'two_factor_recovery_codes' => $request->user()->generateRecoveryCodes(),
        ])->save();

        ActionLog::log('users.2fa.enabled', null, ['ip' => $request->ip()]);

        return to_route('profile.2fa.index');
    }

    /**
     * Disable two-factor authentification for this user.
     */
    public function disable2fa(Request $request)
    {
        $request->user()->forceFill([
            'two_factor_secret' => null,
            'two_factor_recovery_codes' => null,
        ])->save();

        $request->session()->remove('2fa.secret');

        ActionLog::log('users.2fa.disabled', null, ['ip' => $request->ip()]);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.2fa.disabled'));
    }

    /**
     * Update the user preferred theme.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function theme(Request $request)
    {
        $this->validate($request, [
            'theme' => ['required', 'in:light,dark'],
        ]);

        $cookie = cookie('theme', $request->input('theme'), 525600, null, null, true, true);

        return $request->expectsJson()
            ? response()->json($request->only('theme'))->withCookie($cookie)
            : redirect()->back()->withCookie($cookie);
    }

    /**
     * Redirect the user to the Discord OAuth page to link his account.
     */
    public function linkDiscord()
    {
        return Socialite::driver('discord')->redirect();
    }

    /**
     * Handle the Discord OAuth callback.
     */
    public function discordCallback(Request $request)
    {
        abort_if(! $request->filled('code'), 401);

        $user = $request->user();
        $discordUser = Socialite::driver('discord')->user();

        $discordAccount = $user->discordAccount()->updateOrCreate([], [
            'name' => $discordUser->getNickname(),
            'discord_user_id' => $discordUser->getId(),
            'access_token' => $discordUser->token,
            'refresh_token' => $discordUser->refreshToken,
            'expires_at' => now()->addSeconds($discordUser->expiresIn),
        ]);

        LinkedRoles::linkRole($discordAccount);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.discord.linked'));
    }

    /**
     * Handle the Telegram Login Widget callback and link the account.
     */
    public function telegramCallback(Request $request)
    {
        abort_unless(telegram_link_enabled(), 404);

        $verified = TelegramLoginVerifier::verify(
            $request->query(),
            (string) setting('telegram.bot_token')
        );

        if ($verified === null) {
            return to_route('profile.index')
                ->with('error', trans('messages.profile.telegram.invalid'));
        }

        $user = $request->user();

        $taken = TelegramAccount::query()
            ->where('telegram_user_id', $verified['telegram_user_id'])
            ->where('user_id', '!=', $user->id)
            ->exists();

        if ($taken) {
            return to_route('profile.index')
                ->with('error', trans('messages.profile.telegram.taken'));
        }

        $account = $user->telegramAccount()->updateOrCreate([], $verified);

        $this->syncForumTelegramUsername($user, $account->username);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.telegram.linked'));
    }

    protected function syncForumTelegramUsername(User $user, ?string $username): void
    {
        if (! filled($username) || ! plugins()->isEnabled('forum')) {
            return;
        }

        if (! class_exists('Azuriom\\Plugin\\Forum\\Models\\ForumUser')) {
            return;
        }

        try {
            $forumUserModel = 'Azuriom\\Plugin\\Forum\\Models\\ForumUser';
            $forumUserModel::query()->updateOrCreate(
                ['user_id' => $user->id],
                ['telegram' => ltrim($username, '@')]
            );
        } catch (Throwable) {
            // Forum table may be unavailable.
        }
    }

    public function showDelete()
    {
        return view('profile.delete', ['confirmDelete' => false]);
    }

    public function sendDelete(Request $request)
    {
        $request->user()->notify(new UserDelete());

        return to_route('profile.index')
            ->with('success', trans('messages.profile.delete.sent'));
    }

    public function showDeleteConfirm()
    {
        return view('profile.delete', ['confirmDelete' => true]);
    }

    public function confirmDelete(Request $request)
    {
        $user = $request->user();

        abort_if($request->integer('id') !== $user->id, 403);

        ActionLog::log('users.deleted', $user);

        $user->delete();
        $request->session()->flush();

        return to_route('home')
            ->with('success', trans('messages.profile.delete.success'));
    }

    /**
     * Transfer money from one user to another.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function transferMoney(Request $request)
    {
        abort_if(! function_exists('use_site_money') || ! use_site_money(), 403);
        abort_if(! setting('users.money_transfer'), 403);

        $this->validate($request, [
            'name' => ['required'],
            'money' => ['required', 'numeric', 'min:0.01'],
        ]);

        $user = $request->user();
        $money = $request->input('money');

        $receiver = User::where('game_id', $request->input('name'))
            ->orWhere('name', $request->input('name'))
            ->first();

        if ($receiver === null || $user->is($receiver)) {
            throw ValidationException::withMessages([
                'name' => trans('messages.profile.money_transfer.user'),
            ]);
        }

        if ($user->money < $money) {
            throw ValidationException::withMessages([
                'money' => trans('messages.profile.money_transfer.balance'),
            ]);
        }

        $ledger = app(CurrencyLedger::class);
        $ledger->adjustMoney($user, -(float) $money, CurrencyTransaction::TYPE_TRANSFER, [
            'to_user_id' => $receiver->id,
        ]);
        $ledger->adjustMoney($receiver, (float) $money, CurrencyTransaction::TYPE_TRANSFER, [
            'from_user_id' => $user->id,
        ]);

        ActionLog::log('users.transfer', $receiver, ['money' => $money]);

        (new AlertNotification(trans('messages.profile.money_transfer.notification', [
            'user' => $user->name,
            'money' => format_money($money),
        ])))
            ->from($user)
            ->send($receiver);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.money_transfer.success'));
    }

    /**
     * Convert donate money into game coins.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function convertMoney(Request $request)
    {
        abort_if(! function_exists('use_site_money') || ! use_site_money() || ! use_site_coins(), 403);

        $this->validate($request, [
            'amount' => ['required', 'numeric', 'min:0.01'],
        ]);

        $user = $request->user();
        $amount = (float) $request->input('amount');

        if ($user->money < $amount) {
            throw ValidationException::withMessages([
                'amount' => trans('messages.profile.currency_convert.balance'),
            ]);
        }

        try {
            $result = app(CurrencyLedger::class)->convertMoneyToCoins($user, $amount);
        } catch (InvalidArgumentException $e) {
            throw ValidationException::withMessages([
                'amount' => $e->getMessage(),
            ]);
        } catch (RuntimeException $e) {
            throw ValidationException::withMessages([
                'amount' => trans('messages.profile.currency_convert.balance'),
            ]);
        }

        ActionLog::log('users.convert', $user, [
            'money' => $result['money_spent'],
            'coins' => $result['coins_received'],
            'fee' => $result['fee'],
        ]);

        return to_route('profile.index')
            ->with('success', trans('messages.profile.currency_convert.success', [
                'money' => format_money($result['money_spent']),
                'coins' => format_coins($result['coins_received']),
            ]));
    }

    /**
     * Move coins between the global site wallet and a server balance.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function transferCoins(Request $request)
    {
        abort_if(! use_site_coins(), 403);

        $data = $this->validate($request, [
            'direction' => ['required', Rule::in(['to_site', 'to_server'])],
            'server_id' => ['required', 'integer', Rule::exists('servers', 'id')],
            'amount' => ['required', 'numeric', 'gt:0', 'max:999999999999'],
        ]);

        $user = $request->user();
        $amount = (float) $data['amount'];
        $serverId = (int) $data['server_id'];
        $ledger = app(CurrencyLedger::class);

        try {
            if ($data['direction'] === 'to_site') {
                $ledger->depositFromServer($user, $serverId, $amount);
            } else {
                $ledger->withdrawToServer($user, $serverId, $amount);
            }
        } catch (InvalidArgumentException $e) {
            throw ValidationException::withMessages([
                'amount' => $e->getMessage(),
            ]);
        } catch (RuntimeException $e) {
            $message = match ($e->getMessage()) {
                'insufficient_coins' => trans('messages.profile.coins_transfer.insufficient_global'),
                'insufficient_server_coins' => trans('messages.profile.coins_transfer.insufficient_server'),
                default => trans('messages.profile.coins_transfer.failed'),
            };

            throw ValidationException::withMessages([
                'amount' => $message,
            ]);
        }

        ActionLog::log('users.convert', $user, [
            'direction' => $data['direction'],
            'server_id' => $serverId,
            'amount' => $amount,
            'type' => 'coins_transfer',
        ]);

        $successKey = $data['direction'] === 'to_site'
            ? 'messages.profile.coins_transfer.success_to_site'
            : 'messages.profile.coins_transfer.success_to_server';

        return to_route('profile.index')
            ->with('success', trans($successKey, [
                'amount' => format_coins($amount),
            ]));
    }
}
