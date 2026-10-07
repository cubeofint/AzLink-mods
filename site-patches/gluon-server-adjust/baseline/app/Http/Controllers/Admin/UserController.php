<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Http\Requests\UserRequest;
use Azuriom\Models\ActionLog;
use Azuriom\Models\CurrencyTransaction;
use Azuriom\Models\Notification;
use Azuriom\Models\Permission;
use Azuriom\Models\Role;
use Azuriom\Models\Server;
use Azuriom\Models\User;
use Azuriom\Models\UserServerBalance;
use Azuriom\Notifications\AlertNotification;
use Azuriom\Services\Currency\CurrencyLedger;
use Azuriom\Support\Discord\LinkedRoles;
use Illuminate\Auth\Events\PasswordReset;
use Illuminate\Http\Request;
use Illuminate\Support\Arr;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;
use InvalidArgumentException;
use RuntimeException;

class UserController extends Controller
{
    /**
     * Display a listing of the resource.
     */
    public function index(Request $request)
    {
        $search = trim((string) $request->input('search', ''));
        $roleId = $request->filled('role_id') ? (int) $request->input('role_id') : null;
        $canViewEmail = $request->user()->can('admin.users.email');

        $users = User::with('ban')
            ->whereNull('deleted_at')
            ->adminDirectory($search !== '' ? $search : null, $roleId, $canViewEmail)
            ->paginate();

        return view('admin.users.index', [
            'users' => $users,
            'search' => $search,
            'roleId' => $roleId,
            'roles' => Role::query()->orderByDesc('power')->get(),
            'canViewEmail' => $canViewEmail,
            'notificationLevels' => Notification::LEVELS,
        ]);
    }

    /**
     * Send a notification to one or all users.
     *
     * @throws \Illuminate\Validation\ValidationException;
     */
    public function notify(Request $request, ?User $user = null)
    {
        $this->validate($request, [
            'level' => ['required', Rule::in(Notification::LEVELS)],
            'content' => ['required', 'string', 'max:100'],
        ]);

        $users = $user !== null ? [$user] : User::lazy();
        $notification = (new AlertNotification($request->input('content')))
            ->level($request->input('level'))
            ->from($request->user());

        foreach ($users as $localUser) {
            $notification->send($localUser);
        }

        return redirect()->back()->with('success', trans('messages.status.success'));
    }

    /**
     * Show the form for creating a new resource.
     */
    public function create()
    {
        return view('admin.users.create', [
            'roles' => Role::orderByDesc('power')->get(),
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(),
        ]);
    }

    /**
     * Store a newly created resource in storage.
     *
     * @throws \Illuminate\Validation\ValidationException;
     */
    public function store(UserRequest $request)
    {
        $role = Role::find($request->input('role'));

        $this->validateRole($request->user(), $role);

        $user = new User(Arr::except($request->validated(), ['role', 'staff_server_ids', 'extra_permissions']));
        $user->role()->associate($role);
        $user->save();

        $user->staffServers()->sync($request->input('staff_server_ids', []));

        return to_route('admin.users.index')
            ->with('success', trans('messages.status.success'));
    }

    /**
     * Show the form for editing the specified resource.
     */
    public function edit(User $user)
    {
        $logs = ActionLog::with('target')
            ->whereBelongsTo($user)
            ->latest()
            ->paginate();

        $user->load(['ban', 'hwid.users.role', 'hwid.users.ban', 'serverBalances.server', 'staffServers', 'extraPermissions', 'role.permissions']);

        $servers = Server::query()
            ->orderBy('position')
            ->orderBy('name')
            ->get();

        $balancesByServerId = $user->serverBalances->keyBy('server_id');

        return view('admin.users.edit', [
            'user' => $user,
            'roles' => Role::orderByDesc('power')->get(),
            'logs' => $logs,
            'notificationLevels' => Notification::LEVELS,
            'servers' => $servers,
            'balancesByServerId' => $balancesByServerId,
            'permissions' => Permission::permissionsWithName(),
        ]);
    }

    /**
     * Update the specified resource in storage.
     *
     * @throws \Illuminate\Validation\ValidationException;
     */
    public function update(UserRequest $request, User $user)
    {
        if ($user->isDeleted()) {
            return redirect()->back();
        }

        $this->validate($request, [
            'server_balances' => ['nullable', 'array'],
            'server_balances.*' => ['nullable', 'numeric', 'min:0', 'max:999999999999'],
        ]);

        $validated = $request->validated();
        $canCreditCurrency = $request->user()->can('admin.users.money');
        $newMoney = $canCreditCurrency && array_key_exists('money', $validated) ? (float) $validated['money'] : null;
        $newCoins = $canCreditCurrency && array_key_exists('coins', $validated) ? (float) $validated['coins'] : null;
        $serverBalancesInput = $canCreditCurrency ? $request->input('server_balances', []) : [];
        $staffServerIds = $request->input('staff_server_ids', []);

        $user->fill(Arr::except($validated, ['role', 'money', 'coins', 'staff_server_ids', 'extra_permissions']));

        $role = Role::find($request->input('role'));

        $this->validateRole($request->user(), $role, $user);

        $user->role()->associate($role);
        $user->save();

        $user->staffServers()->sync($staffServerIds);

        $this->syncUserExtraPermissions($request, $user);

        if ($canCreditCurrency) {
            $ledger = app(CurrencyLedger::class);

            if ($newMoney !== null) {
                $delta = round($newMoney - (float) $user->money, 2);

                if ($delta !== 0.0) {
                    $ledger->adjustMoney($user, $delta, CurrencyTransaction::TYPE_ADMIN, [
                        'admin_id' => $request->user()->id,
                    ]);
                }
            }

            if ($newCoins !== null) {
                $delta = round($newCoins - (float) $user->coins, 2);

                if ($delta !== 0.0) {
                    $ledger->adjustCoins($user, $delta, CurrencyTransaction::TYPE_ADMIN, [
                        'admin_id' => $request->user()->id,
                    ]);
                }
            }

            $this->syncServerBalances($request, $user, $ledger, $serverBalancesInput);
        }

        $log = ActionLog::log('users.updated', $user);

        if ($log !== null) {
            $user->createLogEntries($log);
        }

        if ($user->wasChanged('password')) {
            event(new PasswordReset($user));

            $log->createEntries(['password' => '**old**'], ['password' => '**new**']);
        }

        return to_route('admin.users.edit', $user)
            ->with('success', trans('messages.status.success'));
    }

    /**
     * Move coins between the global site wallet and a server balance.
     *
     * @throws \Illuminate\Validation\ValidationException
     */
    public function transferCoins(Request $request, User $user)
    {
        $this->authorize('admin.users.money');

        if ($user->isDeleted()) {
            return redirect()->back();
        }

        $data = $this->validate($request, [
            'direction' => ['required', Rule::in(['to_site', 'to_server'])],
            'server_id' => ['required', 'integer', Rule::exists('servers', 'id')],
            'amount' => ['required', 'numeric', 'gt:0', 'max:999999999999'],
        ]);

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
                'insufficient_coins' => trans('admin.users.coins_transfer.insufficient_global'),
                'insufficient_server_coins' => trans('admin.users.coins_transfer.insufficient_server'),
                default => trans('admin.users.coins_transfer.failed'),
            };

            throw ValidationException::withMessages([
                'amount' => $message,
            ]);
        }

        ActionLog::log('users.updated', $user)?->createEntries([
            'coins_transfer' => $data['direction'],
            'server_id' => $serverId,
            'amount' => $amount,
        ], []);

        $successKey = $data['direction'] === 'to_site'
            ? 'admin.users.coins_transfer.success_to_site'
            : 'admin.users.coins_transfer.success_to_server';

        return to_route('admin.users.edit', $user)
            ->with('success', trans($successKey, [
                'amount' => format_coins($amount),
            ]));
    }

    public function verifyEmail(User $user)
    {
        if ($user->isDeleted()) {
            return redirect()->back();
        }

        $user->markEmailAsVerified();

        ActionLog::log('users.updated', $user)?->createEntries([
            'email_verified_at' => null,
        ], [
            'email_verified_at' => $user->email_verified_at,
        ]);

        return to_route('admin.users.edit', $user)
            ->with('success', trans('admin.users.email.verify_success'));
    }

    public function disable2fa(User $user)
    {
        $user->forceFill([
            'two_factor_secret' => null,
            'two_factor_recovery_codes' => null,
        ])->save();

        ActionLog::log('users.updated', $user)?->createEntries([
            '2fa' => 'enabled',
        ], ['2fa' => 'disabled']);

        return to_route('admin.users.edit', $user)
            ->with('success', trans('admin.users.2fa.disabled'));
    }

    public function forcePasswordChange(User $user)
    {
        $user->update(['password_changed_at' => null]);

        return to_route('admin.users.edit', $user)
            ->with('success', trans('messages.status.success'));
    }

    public function unlinkDiscord(User $user)
    {
        if ($user->discordAccount !== null) {
            LinkedRoles::clearRole($user->discordAccount);

            $user->discordAccount->delete();
        }

        return to_route('admin.users.edit', $user)
            ->with('success', trans('messages.status.success'));
    }

    public function unlinkTelegram(User $user)
    {
        if ($user->telegramAccount !== null) {
            $username = $user->telegramAccount->username;
            $user->telegramAccount->delete();

            if (filled($username) && plugins()->isEnabled('forum')
                && class_exists('Azuriom\\Plugin\\Forum\\Models\\ForumUser')) {
                try {
                    $forumUserModel = 'Azuriom\\Plugin\\Forum\\Models\\ForumUser';
                    $forumUser = $forumUserModel::query()->where('user_id', $user->id)->first();

                    if ($forumUser !== null
                        && strcasecmp((string) $forumUser->twitter, ltrim($username, '@')) === 0) {
                        $forumUser->update(['twitter' => null]);
                    }
                } catch (\Throwable) {
                    // Forum table may be unavailable.
                }
            }
        }

        return to_route('admin.users.edit', $user)
            ->with('success', trans('messages.status.success'));
    }

    /**
     * Remove the specified resource from storage.
     */
    public function destroy(User $user)
    {
        if ($user->isDeleted() || $user->isAdmin()) {
            return redirect()->back();
        }

        $user->delete();

        ActionLog::log('users.deleted', $user);

        return to_route('admin.users.index', $user)
            ->with('success', trans('messages.status.success'));
    }

    /**
     * Sync per-server coin balances from the admin user edit form.
     *
     * @param  array<string|int, mixed>  $serverBalancesInput
     */
    protected function syncServerBalances(Request $request, User $user, CurrencyLedger $ledger, array $serverBalancesInput): void
    {
        if ($serverBalancesInput === []) {
            return;
        }

        $allowedServerIds = Server::query()->pluck('id')
            ->merge(
                UserServerBalance::query()
                    ->where('user_id', $user->id)
                    ->pluck('server_id')
            )
            ->unique()
            ->flip();

        foreach ($serverBalancesInput as $serverId => $amount) {
            $serverId = (int) $serverId;

            if (! $allowedServerIds->has($serverId) || $amount === null || $amount === '') {
                continue;
            }

            $newBalance = round((float) $amount, 2);
            $current = $ledger->serverBalance($user, $serverId);
            $delta = round($newBalance - $current, 2);

            if ($delta === 0.0) {
                continue;
            }

            $ledger->adjustServerBalance($user, $serverId, $delta, CurrencyTransaction::TYPE_ADMIN, [
                'admin_id' => $request->user()->id,
                'source' => 'admin_user_edit',
            ]);
        }
    }

    /**
     * @param  array<int, string>  $requested
     */
    protected function syncUserExtraPermissions(Request $request, User $user): void
    {
        $actor = $request->user();

        if ($user->isDeleted() || $actor === null || ! $actor->can('admin.roles')) {
            return;
        }

        if (! $request->boolean('extra_permissions_present')) {
            return;
        }

        $allowed = Permission::permissions();
        $requested = array_values(array_intersect($request->input('extra_permissions', []), $allowed));

        if (! $actor->isAdmin()) {
            $requested = array_values(array_filter(
                $requested,
                fn (string $permission) => $actor->hasRawPermission($permission)
            ));
        }

        $needsAdminAccess = collect($requested)->contains(
            fn (string $permission) => str_starts_with($permission, 'admin.') && $permission !== 'admin.access'
        );

        if ($needsAdminAccess && ! in_array('admin.access', $requested, true)) {
            $requested[] = 'admin.access';
        }

        $user->syncExtraPermissions($requested);
    }

    /**
     * Ensure if a user can change the specified role.
     *
     * @throws \Illuminate\Validation\ValidationException;
     */
    protected function validateRole(User $user, Role $role, ?User $target = null): void
    {
        if (($target && $user->role->power < $target->role->power)
            || (! $user->isAdmin() && $user->role->power < $role->power)) {
            throw ValidationException::withMessages([
                'role_id' => trans('admin.roles.unauthorized'),
            ]);
        }

        $adminUsers = User::whereRelation('role', 'is_admin', true);

        // So many users lost access to the admin panel because they were the only admin
        if (! $role->is_admin && $target?->isAdmin() && $adminUsers->count() < 2) {
            throw ValidationException::withMessages([
                'role_id' => trans('admin.roles.no_admin'),
            ]);
        }
    }
}
