@extends('admin.layouts.admin')

@section('title', trans('admin.users.edit', ['user' => $user->name]))

@section('content')
    @if($user->isDeleted())
        <div class="alert alert-warning" role="alert">
            <i class="bi bi-exclamation-triangle"></i> {{ trans('admin.users.alert-deleted') }}
        </div>
    @elseif($user->isBanned())
        <div class="alert alert-warning shadow" role="alert">
            <h5><i class="bi bi-exclamation-triangle"></i> {{ trans('admin.users.alert-banned.title') }}</h5>
            <ul>
                <li>{{ trans('admin.users.alert-banned.banned-by', ['author' => $user->ban->author->name]) }}</li>
                <li>{{ trans('admin.users.alert-banned.reason', ['reason' => $user->ban->reason]) }}</li>
                <li>{{ trans('admin.users.alert-banned.date', ['date' => format_date_compact($user->ban->created_at)]) }}</li>
            </ul>

            <form method="POST" action="{{ route('admin.users.bans.destroy', [$user, $user->ban]) }}">
                @method('DELETE')
                @csrf

                <button type="submit" class="btn btn-warning">
                    <i class="bi bi-slash-circle"></i> {{ trans('admin.users.unban') }}
                </button>
            </form>
        </div>
    @endif

    <div class="card shadow-sm mb-3">
        <div class="card-body py-2">
            <div class="d-flex flex-wrap gap-2 align-items-center">
                <span class="text-muted small me-1">{{ trans('admin.users.jump_nav') }}:</span>
                <a href="#user-profile" class="btn btn-sm btn-outline-secondary">{{ trans('admin.users.edit_profile') }}</a>
                <a href="#user-currency" class="btn btn-sm btn-outline-secondary">{{ trans('admin.users.currency_section') }}</a>
                <a href="#user-info" class="btn btn-sm btn-outline-secondary">{{ trans('admin.users.info') }}</a>
                <a href="#user-hwid" class="btn btn-sm btn-outline-secondary">{{ trans('admin.users.hwid.title') }}</a>
                @can('admin.logs')
                    @if(! $logs->isEmpty())
                        <a href="#user-logs" class="btn btn-sm btn-outline-secondary">{{ trans('admin.logs.title') }}</a>
                    @endif
                @endcan
            </div>
        </div>
    </div>

    <div class="row">
        <div class="col-md-6">
            <div class="card shadow mb-4" id="user-profile">
                <div class="card-header">
                    <h5 class="card-title mb-0">{{ trans('admin.users.edit_profile') }}</h5>
                </div>
                <div class="card-body">
                    <form action="{{ route('admin.users.update', $user) }}" method="POST">
                        @method('PATCH')
                        @csrf

                        <div class="row">
                            <div class="col-md-9">
                                <div class="mb-3">
                                    <label class="form-label" for="nameInput">{{ trans('auth.name') }}</label>
                                    <input type="text" class="form-control @error('name') is-invalid @enderror" id="nameInput" name="name" value="{{ old('name', $user->name) }}" required @disabled($user->isDeleted())>

                                    @error('name')
                                    <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                    @enderror
                                </div>

                                @can('admin.users.personal')
                                    <div class="mb-3">
                                        <label class="form-label" for="emailInput">{{ trans('auth.email') }}</label>
                                        <input type="email" class="form-control @error('email') is-invalid @enderror" id="emailInput" name="email" value="{{ old('email', $user->email ?? '') }}" @disabled($user->isDeleted())>

                                        @error('email')
                                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                        @enderror
                                    </div>
                                @endcan
                            </div>

                            <div class="col-md-3 text-center">
                                <img src="{{ $user->getAvatar(256) }}" alt="{{ $user->name }}" class="rounded img-fluid mb-3" height="150">
                            </div>
                        </div>

                        @if(! oauth_login())
                            <div class="mb-3">
                                <label class="form-label" for="passwordInput">{{ trans('auth.password') }}</label>
                                <input type="password" class="form-control @error('password') is-invalid @enderror" id="passwordInput" name="password" placeholder="**********" @disabled($user->isDeleted())>

                                @error('password')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>
                        @endif

                        <div class="mb-3">
                            <label class="form-label" for="roleSelect">{{ trans('messages.fields.role') }}</label>
                            <select class="form-select @error('role_id') is-invalid @enderror" id="roleSelect" name="role" @disabled($user->isDeleted())>
                                @foreach($roles as $role)
                                    <option value="{{ $role->id }}" @selected($user->role->is($role))>{{ $role->name }}</option>
                                @endforeach
                            </select>

                            @error('role_id')
                            <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                            @enderror
                        </div>

                        @can('admin.roles')
                            @if(! $user->isDeleted())
                                <div class="mb-3">
                                    <label class="form-label">{{ trans('admin.users.extra_permissions') }}</label>
                                    <input type="hidden" name="extra_permissions_present" value="1">
                                    <p class="form-text mt-0 mb-2">{{ trans('admin.users.extra_permissions_help') }}</p>
                                    <div class="border rounded px-3 py-2">
                                        <div class="row">
                                            @foreach($permissions as $permission => $permissionDescription)
                                                @php
                                                    $fromRole = $user->role->hasRawPermission($permission);
                                                    $selected = collect(old('extra_permissions', $user->extraPermissions->pluck('permission')->all()));
                                                @endphp
                                                <div class="col-lg-6">
                                                    <div class="mb-2 form-check">
                                                        <input type="checkbox"
                                                               class="form-check-input"
                                                               id="extraPermission{{ $loop->index }}"
                                                               name="extra_permissions[]"
                                                               value="{{ $permission }}"
                                                               data-user-permission="{{ $permission }}"
                                                               @checked($fromRole || $selected->contains($permission))
                                                               @disabled($fromRole)>
                                                        <label class="form-check-label" for="extraPermission{{ $loop->index }}">
                                                            {{ trans($permissionDescription) }}
                                                            @if($fromRole)
                                                                <span class="text-muted small">({{ trans('admin.users.extra_permissions_from_role') }})</span>
                                                            @endif
                                                        </label>
                                                    </div>
                                                </div>
                                            @endforeach
                                        </div>
                                    </div>
                                </div>
                            @endif
                        @endcan

                        <div class="mb-3">
                            <label class="form-label">Серверы команды</label>
                            @php
                                $selectedStaffServers = collect(old('staff_server_ids', $user->staffServers->modelKeys()))
                                    ->map(fn ($id) => (int) $id)
                                    ->all();
                            @endphp
                            <div class="border rounded px-3 py-2 @error('staff_server_ids') border-danger @enderror @error('staff_server_ids.*') border-danger @enderror">
                                @forelse($servers as $server)
                                    <div class="form-check">
                                        <input type="checkbox"
                                               class="form-check-input"
                                               id="staffServer{{ $server->id }}"
                                               name="staff_server_ids[]"
                                               value="{{ $server->id }}"
                                               @checked(in_array((int) $server->id, $selectedStaffServers, true))
                                               @disabled($user->isDeleted())>
                                        <label class="form-check-label" for="staffServer{{ $server->id }}">
                                            {{ $server->name }}
                                        </label>
                                    </div>
                                @empty
                                    <div class="text-muted small">Нет доступных серверов</div>
                                @endforelse
                            </div>
                            <div class="form-text">Отметьте серверы, на которых показывать пользователя во вкладке «Команда».</div>

                            @error('staff_server_ids')
                            <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                            @enderror
                            @error('staff_server_ids.*')
                            <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                            @enderror
                        </div>

                        <hr class="my-4" id="user-currency">
                        <h6 class="mb-3">{{ trans('admin.users.currency_section') }}</h6>

                        @can('admin.users.money')
                        <div class="mb-3">
                            <label class="form-label" for="moneyInput">{{ trans('messages.fields.money') }}</label>
                            <div class="input-group @error('money') has-validation @enderror">
                                <input type="number" min="0" max="999999999999" step="0.01" class="form-control @error('money') is-invalid @enderror" id="moneyInput" name="money" value="{{ old('money', $user->money) }}" required @disabled($user->isDeleted())>
                                <span class="input-group-text">{{ money_name() }}</span>

                                @error('money')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label" for="coinsInput">{{ trans('messages.fields.coins') }}</label>
                            <div class="input-group @error('coins') has-validation @enderror">
                                <input type="number" min="0" max="999999999999" step="0.01" class="form-control @error('coins') is-invalid @enderror" id="coinsInput" name="coins" value="{{ old('coins', $user->coins) }}" required @disabled($user->isDeleted())>
                                <span class="input-group-text">{{ coins_name() }}</span>

                                @error('coins')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>
                            <div class="form-text">{{ trans('admin.users.coins_global_info') }}</div>
                        </div>
                        @else
                        <div class="mb-3">
                            <div class="form-label">{{ trans('messages.fields.money') }}</div>
                            <p class="mb-1">{{ format_money((float) $user->money) }}</p>
                            <div class="form-label mt-2">{{ trans('messages.fields.coins') }}</div>
                            <p class="mb-0">{{ format_coins((float) $user->coins) }}</p>
                            <div class="form-text">{{ trans('admin.users.currency_credit_denied') }}</div>
                        </div>
                        @endcan

                        <div class="mb-3">
                            <div class="d-flex align-items-center justify-content-between mb-2">
                                <label class="form-label mb-0">{{ trans('admin.users.server_balances.title') }}</label>
                                <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="collapse" data-bs-target="#serverBalancesCollapse" aria-expanded="true">
                                    {{ trans('admin.users.toggle_section') }}
                                </button>
                            </div>
                            <div class="collapse show" id="serverBalancesCollapse">
                            @if($servers->isEmpty() && $balancesByServerId->isEmpty())
                                <p class="text-muted mb-1">{{ trans('admin.users.server_balances.empty') }}</p>
                            @else
                                <div class="table-responsive" style="max-height: 16rem; overflow-y: auto;">
                                    <table class="table table-sm table-bordered mb-1">
                                        <thead class="sticky-top" style="background: var(--bs-body-bg);">
                                        <tr>
                                            <th>{{ trans('admin.users.server_balances.server') }}</th>
                                            <th style="width: 12rem">{{ trans('admin.users.server_balances.balance') }}</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        @foreach($servers as $server)
                                            @php
                                                $currentBalance = old('server_balances.'.$server->id, $balancesByServerId->get($server->id)?->balance ?? 0);
                                            @endphp
                                            <tr>
                                                <td>
                                                    {{ $server->name }}
                                                    <span class="text-muted">(#{{ $server->id }})</span>
                                                </td>
                                                <td>
                                                    @can('admin.users.money')
                                                    <div class="input-group input-group-sm">
                                                        <input type="number" min="0" max="999999999999" step="0.01"
                                                               class="form-control @error('server_balances.'.$server->id) is-invalid @enderror"
                                                               name="server_balances[{{ $server->id }}]"
                                                               value="{{ $currentBalance }}"
                                                               @disabled($user->isDeleted())>
                                                        <span class="input-group-text">{{ coins_name() }}</span>
                                                    </div>
                                                    @error('server_balances.'.$server->id)
                                                    <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                                                    @enderror
                                                    @else
                                                        {{ format_coins((float) $currentBalance) }}
                                                    @endcan
                                                </td>
                                            </tr>
                                        @endforeach

                                        @foreach($balancesByServerId as $serverId => $serverBalance)
                                            @continue($servers->contains('id', $serverId))
                                            @php
                                                $currentBalance = old('server_balances.'.$serverId, $serverBalance->balance);
                                            @endphp
                                            <tr>
                                                <td>
                                                    {{ $serverBalance->server->name ?? ('#'.$serverId) }}
                                                    <span class="text-muted">(#{{ $serverId }})</span>
                                                </td>
                                                <td>
                                                    @can('admin.users.money')
                                                    <div class="input-group input-group-sm">
                                                        <input type="number" min="0" max="999999999999" step="0.01"
                                                               class="form-control"
                                                               name="server_balances[{{ $serverId }}]"
                                                               value="{{ $currentBalance }}"
                                                               @disabled($user->isDeleted())>
                                                        <span class="input-group-text">{{ coins_name() }}</span>
                                                    </div>
                                                    @else
                                                        {{ format_coins((float) $currentBalance) }}
                                                    @endcan
                                                </td>
                                            </tr>
                                        @endforeach
                                        </tbody>
                                    </table>
                                </div>
                            @endif
                            <div class="form-text">{{ trans('admin.users.server_balances.info') }}</div>
                            </div>
                        </div>

                        <div class="mb-3">
                            <a href="{{ route('admin.currency-transactions.index', ['user_id' => $user->id]) }}" class="btn btn-outline-secondary btn-sm">
                                <i class="bi bi-journal-text"></i> {{ trans('admin.currency_transactions.user_link') }}
                            </a>
                        </div>

                        <button type="submit" class="btn btn-primary" @disabled($user->isDeleted())>
                            <i class="bi bi-save"></i> {{ trans('messages.actions.save') }}
                        </button>

                        @if(! $user->isDeleted())
                            <button type="button" class="btn btn-secondary" data-bs-toggle="modal" data-bs-target="#notificationModal">
                                <i class="bi bi-megaphone"></i> {{ trans('admin.users.notify') }}
                            </button>
                        @endif

                        @if(! $user->isDeleted() && ! $user->isAdmin() && ! $user->is(Auth::user()))
                            @if(! $user->isBanned())
                                <button type="button" class="btn btn-warning" data-bs-toggle="modal" data-bs-target="#banModal">
                                    <i class="bi bi-slash-circle"></i> {{ trans('admin.users.ban') }}
                                </button>
                            @endif

                            <a href="{{ route('admin.users.destroy', $user) }}" class="btn btn-danger" data-confirm="delete">
                                <i class="bi bi-trash"></i> {{ trans('admin.users.delete') }}
                            </a>
                        @endif
                    </form>
                </div>
            </div>

            @if(! $user->isDeleted() && $servers->isNotEmpty())
                @can('admin.users.money')
                @php
                    $coinsTransferOpen = $errors->has('direction') || $errors->has('server_id') || $errors->has('amount');
                @endphp
                <div class="card shadow mb-4" id="user-coins-transfer">
                    <div class="card-header d-flex align-items-center justify-content-between">
                        <h5 class="card-title mb-0">{{ trans('admin.users.coins_transfer.title') }}</h5>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="collapse" data-bs-target="#coinsTransferCollapse" aria-expanded="{{ $coinsTransferOpen ? 'true' : 'false' }}">
                            {{ trans('admin.users.toggle_section') }}
                        </button>
                    </div>
                    <div class="collapse{{ $coinsTransferOpen ? ' show' : '' }}" id="coinsTransferCollapse">
                    <div class="card-body">
                        <p class="text-muted small">{{ trans('admin.users.coins_transfer.description') }}</p>
                        <p class="mb-3">
                            <strong>{{ trans('messages.fields.coins') }}:</strong>
                            {{ format_coins((float) $user->coins) }}
                        </p>

                        <form method="POST" action="{{ route('admin.users.coins.transfer', $user) }}" class="row g-3 align-items-end">
                            @csrf

                            <div class="col-md-12">
                                <label class="form-label" for="coinsTransferDirection">{{ trans('admin.users.coins_transfer.direction') }}</label>
                                <select class="form-select @error('direction') is-invalid @enderror" id="coinsTransferDirection" name="direction" required>
                                    <option value="to_site" @selected(old('direction') === 'to_site')>{{ trans('admin.users.coins_transfer.to_site') }}</option>
                                    <option value="to_server" @selected(old('direction', 'to_server') === 'to_server')>{{ trans('admin.users.coins_transfer.to_server') }}</option>
                                </select>
                                @error('direction')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>

                            <div class="col-md-6">
                                <label class="form-label" for="coinsTransferServer">{{ trans('admin.users.coins_transfer.server') }}</label>
                                <select class="form-select @error('server_id') is-invalid @enderror" id="coinsTransferServer" name="server_id" required>
                                    @foreach($servers as $server)
                                        @php
                                            $balance = (float) ($balancesByServerId->get($server->id)?->balance ?? 0);
                                        @endphp
                                        <option value="{{ $server->id }}" @selected((int) old('server_id') === (int) $server->id)>
                                            {{ $server->name }} — {{ format_coins($balance) }}
                                        </option>
                                    @endforeach
                                </select>
                                @error('server_id')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>

                            <div class="col-md-6">
                                <label class="form-label" for="coinsTransferAmount">{{ trans('admin.users.coins_transfer.amount') }}</label>
                                <div class="input-group @error('amount') has-validation @enderror">
                                    <input type="number" min="0.01" max="999999999999" step="0.01"
                                           class="form-control @error('amount') is-invalid @enderror"
                                           id="coinsTransferAmount" name="amount" value="{{ old('amount') }}" required>
                                    <span class="input-group-text">{{ coins_name() }}</span>
                                    @error('amount')
                                    <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                    @enderror
                                </div>
                            </div>

                            <div class="col-12">
                                <button type="submit" class="btn btn-primary">
                                    <i class="bi bi-arrow-left-right"></i> {{ trans('admin.users.coins_transfer.submit') }}
                                </button>
                            </div>
                        </form>
                    </div>
                    </div>
                </div>
                @endcan
            @endif
        </div>

        <div class="col-md-6">
            <div class="card shadow mb-4" id="user-info">
                <div class="card-header">
                    <h5 class="card-title mb-0">{{ trans('admin.users.info') }}</h5>
                </div>
                <div class="card-body">
                    <div class="mb-3">
                        <label class="form-label" for="registerInput">{{ trans('admin.users.registered') }}</label>
                        <input type="text" class="form-control" id="registerInput" value="{{ format_date_compact($user->created_at) }}" disabled>
                    </div>

                    @if($user->last_login_at)
                        <div class="mb-3">
                            <label class="form-label" for="lastLoginInput">{{ trans('admin.users.last_login') }}</label>
                            <input type="text" class="form-control" id="lastLoginInput" value="{{ format_date_compact($user->last_login_at) }}" disabled>
                        </div>
                    @endif

                    @if($user->email !== null)
                        <form action="{{ route('admin.users.verify', $user) }}" method="POST">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="emailVerifiedInput">{{ trans('admin.users.email.verified') }}</label>

                                @if($user->hasVerifiedEmail())
                                    <input type="text" class="form-control text-success" id="emailVerifiedInput"
                                           value="{{ trans('admin.users.email.date', ['date' => format_date_compact($user->email_verified_at)]) }}" disabled>
                                @else
                                    <div class="input-group mb-3">
                                        <input type="text" class="form-control text-danger" id="emailVerifiedInput" value="{{ trans('messages.no') }}" disabled>

                                        @if(! $user->isDeleted())
                                            <button class="btn btn-outline-success" type="submit">
                                                {{ trans('admin.users.email.verify') }}
                                            </button>
                                        @endif
                                    </div>
                                @endif
                            </div>
                        </form>
                    @endif

                    @if(! oauth_login())
                        <form action="{{ route('admin.users.2fa', $user) }}" method="POST">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="2faInput">{{ trans('admin.users.2fa.title') }}</label>

                                @if(! $user->hasTwoFactorAuth())
                                    <input type="text" class="form-control text-danger" id="2faInput" value="{{ trans('messages.no') }}" disabled>
                                @else
                                    <div class="input-group mb-3">
                                        <input type="text" class="form-control text-success" id="2faInput" value="{{ trans('messages.yes') }}" disabled>

                                        <button class="btn btn-outline-danger" type="submit">
                                            {{ trans('admin.users.2fa.disable') }}
                                        </button>
                                    </div>
                                @endif
                            </div>
                        </form>

                        <form action="{{ route('admin.users.force-password', $user) }}" method="POST">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="forcePassword">{{ trans('admin.users.password.title') }}</label>

                                @if($user->mustChangePassword())
                                    <input type="text" class="form-control" id="forcePassword" value="{{ trans('admin.users.password.forced') }}" disabled>
                                @else
                                    <div class="input-group mb-3">
                                        @if($user->password_changed_at->eq($user->created_at))
                                            <input type="text" class="form-control" id="forcePassword" value="{{ trans('messages.unknown') }}" disabled>
                                        @else
                                            <input type="text" class="form-control" id="forcePassword" value="{{ format_date_compact($user->password_changed_at) }}" disabled>
                                        @endif

                                        <button class="btn btn-outline-danger" type="submit">
                                            {{ trans('admin.users.password.force') }}
                                        </button>
                                    </div>
                                @endif
                            </div>
                        </form>
                    @endif

                    @can('admin.users.personal-data')
                        <div class="mb-3">
                            <label class="form-label" for="addressInput">{{ trans('admin.users.ip') }}</label>
                            <input type="text" class="form-control" id="addressInput" value="{{ $user->last_login_ip ?? trans('messages.unknown') }}" disabled>
                        </div>
                    @endcan

                    @if($user->game_id)
                        <div class="mb-3">
                            <label class="form-label" for="idInput">{{ game()->trans('id') }}</label>
                            <input type="text" class="form-control" id="idInput" value="{{ $user->game_id }}" disabled>
                        </div>
                    @endif

                    @if($user->discordAccount !== null)
                        <form action="{{ route('admin.users.discord.unlink', $user) }}" method="POST">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="discordInput">{{ trans('admin.users.discord') }}</label>

                                <div class="input-group mb-3">
                                    <input type="text" class="form-control" id="discordInput" value="{{ $user->discordAccount->name }}" disabled>

                                    <button class="btn btn-outline-danger" type="submit">
                                        {{ trans('messages.actions.remove') }}
                                    </button>
                                </div>
                            </div>
                        </form>
                    @endif

                    @if($user->telegramAccount !== null)
                        <form action="{{ route('admin.users.telegram.unlink', $user) }}" method="POST">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="telegramInput">{{ trans('admin.users.telegram') }}</label>

                                <div class="input-group mb-3">
                                    <input type="text" class="form-control" id="telegramInput" value="{{ $user->telegramAccount->displayName() }}" disabled>

                                    <button class="btn btn-outline-danger" type="submit">
                                        {{ trans('messages.actions.remove') }}
                                    </button>
                                </div>
                            </div>
                        </form>
                    @endif
                </div>
            </div>
        </div>
    </div>

    <div id="user-hwid">
        @include('admin.users._hwid')
    </div>

    @if(! $user->isBanned())
        <div class="modal fade" id="banModal" tabindex="-1" role="dialog" aria-labelledby="banLabel" aria-modal="true">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <h2 class="modal-title" id="banLabel">{{ trans('admin.users.ban-title', ['user' => $user->name]) }}</h2>
                        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                    </div>
                    <div class="modal-body">
                        <p>{{ trans('admin.users.ban-description') }}</p>

                        <form method="POST" action="{{ route('admin.users.bans.store', $user) }}">
                            @csrf

                            <div class="mb-3">
                                <label class="form-label" for="reasonInput">{{ trans('admin.bans.reason') }}</label>
                                <input type="text" class="form-control @error('reason') is-invalid @enderror" id="reasonInput" name="reason" required>

                                @error('reason')
                                <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                @enderror
                            </div>

                            <button class="btn btn-secondary" type="button" data-bs-dismiss="modal">
                                {{ trans('messages.actions.cancel') }}
                            </button>

                            <button class="btn btn-danger" type="submit">
                                <i class="bi bi-slash-circle"></i> {{ trans('admin.users.ban') }}
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    @endif

    @can('admin.logs')
        @if(! $logs->isEmpty())
            <div class="card shadow mb-4" id="user-logs">
                <div class="card-header d-flex align-items-center justify-content-between">
                    <h5 class="card-title mb-0">{{ trans('admin.logs.title') }}</h5>
                    <button type="button" class="btn btn-sm btn-outline-secondary" data-bs-toggle="collapse" data-bs-target="#userLogsCollapse" aria-expanded="false">
                        {{ trans('admin.users.toggle_section') }}
                    </button>
                </div>
                <div class="collapse" id="userLogsCollapse">
                <div class="card-body">
                    <div class="table-responsive">
                        <table class="table table-striped">
                            <thead>
                            <tr>
                                <th scope="col">#</th>
                                <th scope="col">{{ trans('messages.fields.action') }}</th>
                                <th scope="col">{{ trans('messages.fields.date') }}</th>
                                <th scope="col" class="text-end">{{ trans('messages.actions.show') }}</th>
                            </tr>
                            </thead>
                            <tbody>

                            @foreach($logs as $log)
                                <tr>
                                    <th scope="row">{{ $log->id }}</th>
                                    <td>
                                        <i class="text-{{ $log->getActionFormat()['color'] }} bi bi-{{ $log->getActionFormat()['icon'] }}"></i>
                                        {{ $log->getActionMessage() }}
                                    </td>
                                    <td>{{ format_date_compact($log->created_at) }}</td>
                                    <td class="text-end">
                                        <a href="{{ route('admin.logs.show', $log) }}" class="mx-1" title="{{ trans('messages.actions.show') }}" data-bs-toggle="tooltip"><i class="bi bi-eye"></i></a>
                                    </td>
                                </tr>
                            @endforeach

                            </tbody>
                        </table>
                    </div>

                    {{ $logs->links() }}
                </div>
                </div>
            </div>
        @endif
    @endcan

    <div class="row gy-4">
        @foreach($cards ?? [] as $card)
            <div class="col-12">
                <div class="card shadow-sm mb-4">
                    <div class="card-header">
                        <h5 class="card-title mb-0">{{ $card['name'] }}</h5>
                    </div>
                    <div class="card-body">
                        @include($card['view'])
                    </div>
                </div>
            </div>
        @endforeach
    </div>

    @include('admin.users._notify', ['route' => route('admin.users.notify', ['user' => $user])])
@endsection

@push('footer-scripts')
    <script>
        document.querySelectorAll('[data-user-permission]').forEach(function (el) {
            const perm = el.dataset.userPermission;

            if (!perm.includes('admin.') || perm === 'admin.access') {
                return;
            }

            el.addEventListener('change', function () {
                if (!el.checked) {
                    return;
                }

                const access = document.querySelector('[data-user-permission="admin.access"]');
                if (access && !access.disabled) {
                    access.checked = true;
                }
            });
        });
    </script>
@endpush
