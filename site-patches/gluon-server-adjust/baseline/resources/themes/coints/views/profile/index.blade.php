@extends('layouts.app')

@section('title', 'Профиль')

@section('content')
<section class="profile-hub">
    <div class="container">
        <div class="row align-items-start g-3">
            <!-- Боковая панель профиля -->
            <div class="col-lg-4">
                <div class="profile-hub-card profile-identity">
                    <div class="profile-hub-card-body">
                        <div class="profile-identity-top">
                            <div class="profile-avatar-wrap">
                                <img src="{{ url('/api/skin-api/avatars/face/' . auth()->user()->id) }}?t={{ time() }}"
                                     alt="{{ auth()->user()->name }}"
                                     class="profile-avatar-img"
                                     width="96"
                                     height="96"
                                     loading="lazy">
                            </div>

                            <div class="profile-identity-main">
                                <h1 class="profile-name">{{ auth()->user()->name }}</h1>

                                @if(auth()->user()->role)
                                    <span class="badge forum-user-role profile-role-badge" style="{{ auth()->user()->role->getBadgeStyle() }}">
                                        @if(auth()->user()->role->icon)<i class="{{ auth()->user()->role->icon }}"></i>@endif
                                        {{ auth()->user()->role->name }}
                                    </span>
                                @endif
                            </div>
                        </div>

                        <div class="profile-identity-meta">
                            <span>
                                <i class="bi bi-envelope me-1"></i>
                                {{ auth()->user()->email }}
                            </span>
                            <span>
                                <i class="bi bi-calendar3 me-1"></i>
                                <strong>Регистрация:</strong>
                                {{ format_date(auth()->user()->created_at) }}
                            </span>
                            @if($forumProfile?->display_last_seen && $forumProfile->last_seen_at)
                                <span>
                                    <i class="bi bi-clock me-1"></i>
                                    <strong>Последний раз видели:</strong>
                                    {{ format_date($forumProfile->last_seen_at, true) }}
                                </span>
                            @endif
                            @if($forumProfile?->location)
                                <span>
                                    <i class="bi bi-geo-alt me-1"></i>
                                    <strong>Расположение:</strong>
                                    {{ $forumProfile->location }}
                                </span>
                            @endif
                        </div>

                        @if(($forumProfile && ($forumProfile->website || $forumProfile->telegram)) || auth()->user()->discordAccount || auth()->user()->telegramAccount)
                            <div class="profile-forum-contacts">
                                @if($forumProfile?->website)
                                    <div class="profile-forum-contact">
                                        <i class="bi bi-globe"></i>
                                        <div>
                                            <span class="profile-forum-contact-label">Сайт</span>
                                            <a href="{{ $forumProfile->website }}" target="_blank" rel="noopener noreferrer">
                                                {{ $forumProfile->website }}
                                            </a>
                                        </div>
                                    </div>
                                @endif

                                @if(auth()->user()->discordAccount)
                                    <div class="profile-forum-contact">
                                        <i class="bi bi-discord"></i>
                                        <div>
                                            <span class="profile-forum-contact-label">Discord</span>
                                            <span>{{ auth()->user()->discordAccount->name }}</span>
                                        </div>
                                    </div>
                                @endif

                                @if(auth()->user()->telegramAccount)
                                    <div class="profile-forum-contact">
                                        <i class="bi bi-telegram"></i>
                                        <div>
                                            <span class="profile-forum-contact-label">Telegram</span>
                                            @if(auth()->user()->telegramAccount->profileUrl())
                                                <a href="{{ auth()->user()->telegramAccount->profileUrl() }}" target="_blank" rel="noopener noreferrer">
                                                    {{ auth()->user()->telegramAccount->displayName() }}
                                                </a>
                                            @else
                                                <span>{{ auth()->user()->telegramAccount->displayName() }}</span>
                                            @endif
                                        </div>
                                    </div>
                                @elseif($forumProfile?->telegram)
                                    <div class="profile-forum-contact">
                                        <i class="bi bi-telegram"></i>
                                        <div>
                                            <span class="profile-forum-contact-label">Telegram</span>
                                            <a href="https://t.me/{{ $forumProfile->telegram }}" target="_blank" rel="noopener noreferrer">
                                                {{ '@'.$forumProfile->telegram }}
                                            </a>
                                        </div>
                                    </div>
                                @endif
                            </div>
                        @endif

                        @if((function_exists('use_site_money') && use_site_money()) || use_site_coins())
                            <div class="profile-balances">
                                @if(function_exists('use_site_money') && use_site_money())
                                    <div class="profile-balance-chip">
                                        <div class="profile-balance-chip-amount">
                                            @include('elements.currency-icon', ['type' => 'money', 'size' => 32])
                                            <strong>{{ format_money(auth()->user()->money) }}</strong>
                                        </div>
                                        @if(Route::has('shop.offers.select'))
                                            <a href="{{ route('shop.offers.select') }}" class="btn home-server-play profile-balance-topup">
                                                {{ trans('shop::messages.cart.credit') }}
                                            </a>
                                        @endif
                                    </div>
                                @endif
                                @if(use_site_coins())
                                    <div class="profile-balance-chip">
                                        @include('elements.currency-icon', ['type' => 'coins', 'size' => 32])
                                        <strong>{{ format_coins(auth()->user()->coins) }}</strong>
                                    </div>
                                @endif
                            </div>
                        @endif

                        <div class="quick-links d-grid gap-2">
                            @if(plugins()->isEnabled('forum'))
                                <a href="{{ route('forum.profile.edit') }}" class="btn home-server-play profile-hub-btn">
                                    <i class="bi bi-pencil-square"></i>
                                    <span>Редактировать профиль</span>
                                </a>
                            @endif
                            @can('admin.access')
                                <a href="{{ route('admin.dashboard') }}" class="profile-hub-secondary-btn">
                                    <i class="bi bi-gear"></i>
                                    <span>Панель управления</span>
                                </a>
                            @endcan
                            <a href="{{ url('/') }}" class="profile-hub-secondary-btn">
                                <i class="bi bi-house-door"></i>
                                <span>На главную</span>
                            </a>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Основное содержание -->
            <div class="col-lg-8">
                <!-- Навигация -->
                <div class="profile-hub-card profile-tabs-card">
                    <div class="profile-hub-card-body profile-tabs-body">
                        <ul class="nav nav-pills profile-hub-tabs flex-wrap" id="profileTabs" role="tablist">
                            <li class="nav-item" role="presentation">
                                <button class="nav-link active" id="profile-tab" data-bs-toggle="pill" data-bs-target="#profile-content" type="button" role="tab">
                                    <i class="bi bi-person me-2"></i>Профиль
                                </button>
                            </li>
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="security-tab" data-bs-toggle="pill" data-bs-target="#security-content" type="button" role="tab">
                                    <i class="bi bi-shield me-2"></i>Безопасность
                                </button>
                            </li>
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="skin-tab" data-bs-toggle="pill" data-bs-target="#skin-content" type="button" role="tab">
                                    <i class="bi bi-person-bounding-box me-2"></i>Скин
                                </button>
                            </li>
                            @if(\Azuriom\Models\CurrencyServerMovement::enabled())
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="movements-tab" data-bs-toggle="pill" data-bs-target="#movements-content" type="button" role="tab">
                                    <i class="bi bi-arrow-left-right me-2"></i>Движение валют
                                </button>
                            </li>
                            @endif
                            <li class="nav-item" role="presentation">
                                <button class="nav-link" id="stats-tab" data-bs-toggle="pill" data-bs-target="#stats-content" type="button" role="tab">
                                    <i class="bi bi-bar-chart-line me-2"></i>Статистика
                                </button>
                            </li>
                        </ul>
                    </div>
                </div>

                <!-- Содержимое табов -->
                <div class="tab-content">
                    <!-- Вкладка Профиль -->
                    <div class="tab-pane fade show active" id="profile-content" role="tabpanel">
                        <div class="profile-hub-card">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-person me-2"></i>Обзор аккаунта
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                @if(session('success'))
                                    <div class="alert alert-success border-primary">
                                        <i class="bi bi-check-circle me-2"></i>{{ session('success') }}
                                    </div>
                                @endif

                                @if(session('error'))
                                    <div class="alert alert-danger border-primary">
                                        <i class="bi bi-exclamation-circle me-2"></i>{{ session('error') }}
                                    </div>
                                @endif

                                <p class="text-muted mb-4">Краткий статус аккаунта и быстрые действия. Данные аккаунта — слева, настройки — во вкладках выше.</p>

                                <div class="row g-3 profile-status-grid mb-4">
                                    <div class="col-sm-6 col-xl-3">
                                        <div class="profile-status-card {{ auth()->user()->hasVerifiedEmail() ? 'is-ok' : 'is-warn' }}">
                                            <div class="profile-status-icon"><i class="bi bi-envelope-check"></i></div>
                                            <div>
                                                <div class="profile-status-title">Email</div>
                                                <div class="profile-status-value">
                                                    {{ auth()->user()->hasVerifiedEmail() ? 'Подтверждён' : 'Не подтверждён' }}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-sm-6 col-xl-3">
                                        <div class="profile-status-card {{ auth()->user()->hasTwoFactorAuth() ? 'is-ok' : 'is-muted' }}">
                                            <div class="profile-status-icon"><i class="bi bi-shield-lock"></i></div>
                                            <div>
                                                <div class="profile-status-title">2FA</div>
                                                <div class="profile-status-value">
                                                    {{ auth()->user()->hasTwoFactorAuth() ? 'Включена' : 'Выключена' }}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-sm-6 col-xl-3">
                                        <div class="profile-status-card {{ auth()->user()->discordAccount ? 'is-ok' : 'is-muted' }}">
                                            <div class="profile-status-icon"><i class="bi bi-discord"></i></div>
                                            <div>
                                                <div class="profile-status-title">Discord</div>
                                                <div class="profile-status-value">
                                                    {{ auth()->user()->discordAccount ? auth()->user()->discordAccount->name : 'Не привязан' }}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-sm-6 col-xl-3">
                                        <div class="profile-status-card {{ auth()->user()->telegramAccount ? 'is-ok' : 'is-muted' }}">
                                            <div class="profile-status-icon"><i class="bi bi-telegram"></i></div>
                                            <div>
                                                <div class="profile-status-title">Telegram</div>
                                                <div class="profile-status-value">
                                                    {{ auth()->user()->telegramAccount ? auth()->user()->telegramAccount->displayName() : 'Не привязан' }}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                <div class="profile-quick-actions mb-4">
                                    <h6 class="profile-section-title">Быстрые действия</h6>
                                    <div class="row g-3">
                                        <div class="col-sm-6 col-lg-4">
                                            <button type="button" class="profile-action-tile" data-bs-toggle="pill" data-bs-target="#security-content" onclick="document.getElementById('security-tab').click()">
                                                <i class="bi bi-shield-lock"></i>
                                                <span>Безопасность</span>
                                                <small>Пароль, 2FA, Discord, Telegram</small>
                                            </button>
                                        </div>
                                        <div class="col-sm-6 col-lg-4">
                                            <button type="button" class="profile-action-tile" data-bs-toggle="pill" data-bs-target="#skin-content" onclick="document.getElementById('skin-tab').click()">
                                                <i class="bi bi-person-bounding-box"></i>
                                                <span>Скин</span>
                                                <small>Загрузка и предпросмотр</small>
                                            </button>
                                        </div>
                                        <div class="col-sm-6 col-lg-4">
                                            <button type="button" class="profile-action-tile" data-bs-toggle="pill" data-bs-target="#stats-content" onclick="document.getElementById('stats-tab').click()">
                                                <i class="bi bi-bar-chart-line"></i>
                                                <span>Статистика</span>
                                                <small>Игровые показатели с серверов</small>
                                            </button>
                                        </div>
                                    </div>
                                </div>

                                @php
                                    $activeSubscriptions = collect($shopSubscriptions ?? [])->filter(fn ($s) => ($s->status ?? null) === 'active');
                                @endphp

                                @if($activeSubscriptions->isNotEmpty())
                                    <div class="profile-subscriptions mb-4">
                                        <h6 class="profile-section-title">Активные привилегии</h6>
                                        <div class="d-grid gap-2">
                                            @foreach($activeSubscriptions as $subscription)
                                                <div class="profile-subscription-item">
                                                    <div>
                                                        <strong>{{ $subscription->package?->name ?? trans('messages.unknown') }}</strong>
                                                        <div class="text-muted small">до {{ format_date($subscription->ends_at) }}</div>
                                                    </div>
                                                    <span class="badge text-bg-success">Активна</span>
                                                </div>
                                            @endforeach
                                        </div>
                                    </div>
                                @endif

                                @if(function_exists('use_site_money') && use_site_money() && use_site_coins())
                                    <div class="currency-convert-section profile-convert-panel">
                                        <h6 class="profile-section-title mb-2">{{ trans('messages.profile.currency_convert.title') }}</h6>
                                        <p class="text-muted small mb-3">{{ trans('messages.profile.currency_convert.description') }}</p>
                                        <p class="profile-convert-rate mb-3">
                                            {{ trans('messages.profile.currency_convert.rate', [
                                                'money' => money_name(1),
                                                'rate' => setting('currency.convert_rate', 100),
                                                'coins' => coins_name((float) setting('currency.convert_rate', 100)),
                                            ]) }}
                                            @if((float) setting('currency.convert_fee', 0) > 0)
                                                · {{ trans('messages.profile.currency_convert.fee', ['fee' => setting('currency.convert_fee', 0)]) }}
                                            @endif
                                        </p>

                                        <form action="{{ route('profile.convert-money') }}" method="POST" class="row g-3 align-items-end">
                                            @csrf
                                            <div class="col-md-7">
                                                <label for="convertAmount" class="form-label">{{ trans('messages.profile.currency_convert.amount') }}</label>
                                                <input type="number" min="0.01" step="0.01" class="form-control @error('amount') is-invalid @enderror" id="convertAmount" name="amount" value="{{ old('amount') }}" required>
                                                @error('amount')
                                                    <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                                @enderror
                                            </div>
                                            <div class="col-md-5">
                                                <button type="submit" class="btn home-server-play profile-hub-btn w-100">
                                                    <i class="bi bi-arrow-left-right"></i>
                                                    <span>{{ trans('messages.profile.currency_convert.submit') }}</span>
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                @endif

                                @if(use_site_coins() && isset($servers) && $servers->isNotEmpty())
                                    <div class="currency-convert-section profile-convert-panel mt-4">
                                        <h6 class="profile-section-title mb-2">{{ trans('messages.profile.coins_transfer.title') }}</h6>
                                        <p class="text-muted small mb-3">{{ trans('messages.profile.coins_transfer.description') }}</p>
                                        <p class="mb-2">
                                            {{ trans('messages.profile.coins_transfer.global_balance', [
                                                'balance' => format_coins((float) auth()->user()->coins),
                                            ]) }}
                                        </p>

                                        @if(isset($balancesByServerId) && $balancesByServerId->isNotEmpty())
                                            <ul class="list-unstyled small text-muted mb-3">
                                                @foreach($servers as $server)
                                                    @php $bal = (float) ($balancesByServerId->get($server->id)?->balance ?? 0); @endphp
                                                    @continue($bal <= 0)
                                                    <li>{{ $server->name }}: {{ format_coins($bal) }}</li>
                                                @endforeach
                                            </ul>
                                        @endif

                                        <form action="{{ route('profile.transfer-coins') }}" method="POST" class="row g-3 align-items-end">
                                            @csrf
                                            <div class="col-md-12">
                                                <label class="form-label" for="coinsTransferDirection">{{ trans('messages.profile.coins_transfer.direction') }}</label>
                                                @include('components.hub-select', [
                                                    'name' => 'direction',
                                                    'id' => 'coinsTransferDirection',
                                                    'required' => true,
                                                    'invalid' => $errors->has('direction'),
                                                    'value' => old('direction', 'to_server'),
                                                    'options' => [
                                                        [
                                                            'value' => 'to_site',
                                                            'label' => trans('messages.profile.coins_transfer.to_site'),
                                                        ],
                                                        [
                                                            'value' => 'to_server',
                                                            'label' => trans('messages.profile.coins_transfer.to_server'),
                                                        ],
                                                    ],
                                                ])
                                                @error('direction')
                                                    <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                                                @enderror
                                            </div>
                                            <div class="col-md-5">
                                                <label class="form-label" for="coinsTransferServer">{{ trans('messages.profile.coins_transfer.server') }}</label>
                                                @include('components.hub-select', [
                                                    'name' => 'server_id',
                                                    'id' => 'coinsTransferServer',
                                                    'required' => true,
                                                    'invalid' => $errors->has('server_id'),
                                                    'value' => old('server_id'),
                                                    'options' => $servers->map(function ($server) use ($balancesByServerId) {
                                                        $bal = (float) ($balancesByServerId->get($server->id)?->balance ?? 0);

                                                        return [
                                                            'value' => (string) $server->id,
                                                            'label' => $server->name.' ('.format_coins($bal).')',
                                                        ];
                                                    })->values()->all(),
                                                ])
                                                @error('server_id')
                                                    <span class="invalid-feedback d-block" role="alert"><strong>{{ $message }}</strong></span>
                                                @enderror
                                            </div>
                                            <div class="col-md-4">
                                                <label for="coinsTransferAmount" class="form-label">{{ trans('messages.profile.coins_transfer.amount') }}</label>
                                                <input type="number" min="0.01" step="0.01" class="form-control @error('amount') is-invalid @enderror" id="coinsTransferAmount" name="amount" value="{{ old('amount') }}" required>
                                                @error('amount')
                                                    <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                                                @enderror
                                            </div>
                                            <div class="col-md-3">
                                                <button type="submit" class="btn home-server-play profile-hub-btn w-100">
                                                    <i class="bi bi-arrow-left-right"></i>
                                                    <span>{{ trans('messages.profile.coins_transfer.submit') }}</span>
                                                </button>
                                            </div>
                                        </form>
                                    </div>
                                @endif
                            </div>
                        </div>

                        @if(plugins()->isEnabled('referrals'))
                            @include('referrals::profile.card')
                        @endif
                    </div>

                    @if(\Azuriom\Models\CurrencyServerMovement::enabled())
                    <!-- Вкладка Движение валют -->
                    <div class="tab-pane fade" id="movements-content" role="tabpanel">
                        @include('profile._currency-movements')
                    </div>
                    @endif

                    <!-- Вкладка Безопасность -->
                    <div class="tab-pane fade" id="security-content" role="tabpanel">
                        <div class="profile-hub-card">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-shield me-2"></i>Настройки безопасности
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                <form action="{{ route('profile.password') }}" method="POST" class="mb-5">
                                    @csrf
                                    <h3 class="profile-section-title">Смена пароля</h3>

                                    <div class="row g-3">
                                        <div class="col-12">
                                            <label for="current_password" class="form-label">Текущий пароль</label>
                                            <div class="input-group profile-input-group">
                                                <span class="input-group-text">
                                                    <i class="bi bi-key"></i>
                                                </span>
                                                <input type="password" class="form-control @error('current_password') is-invalid @enderror"
                                                       id="current_password" name="current_password" required>
                                                @error('current_password')
                                                    <div class="invalid-feedback">{{ $message }}</div>
                                                @enderror
                                            </div>
                                        </div>

                                        <div class="col-md-6">
                                            <label for="password" class="form-label">Новый пароль</label>
                                            <div class="input-group profile-input-group">
                                                <span class="input-group-text">
                                                    <i class="bi bi-lock"></i>
                                                </span>
                                                <input type="password" class="form-control @error('password') is-invalid @enderror"
                                                       id="password" name="password" required>
                                                <button class="btn profile-toggle-password toggle-password" type="button" aria-label="Показать пароль">
                                                    <i class="bi bi-eye"></i>
                                                </button>
                                                @error('password')
                                                    <div class="invalid-feedback">{{ $message }}</div>
                                                @enderror
                                            </div>
                                        </div>

                                        <div class="col-md-6">
                                            <label for="password_confirmation" class="form-label">Подтверждение</label>
                                            <div class="input-group profile-input-group">
                                                <span class="input-group-text">
                                                    <i class="bi bi-lock-fill"></i>
                                                </span>
                                                <input type="password" class="form-control"
                                                       id="password_confirmation" name="password_confirmation" required>
                                                <button class="btn profile-toggle-password toggle-password" type="button" aria-label="Показать пароль">
                                                    <i class="bi bi-eye"></i>
                                                </button>
                                            </div>
                                        </div>

                                        <div class="col-12">
                                            <button type="submit" class="btn home-server-play profile-hub-btn">
                                                <i class="bi bi-key"></i>
                                                <span>Сменить пароль</span>
                                            </button>
                                        </div>
                                    </div>
                                </form>

                                @if(! oauth_login())
                                <div class="profile-security-block mb-5">
                                    <h3 class="profile-section-title">Двухфакторная аутентификация</h3>

                                    @if(auth()->user()->hasTwoFactorAuth())
                                        <div class="alert alert-success">
                                            <i class="bi bi-shield-check me-2"></i>
                                            2FA включена для вашего аккаунта
                                        </div>
                                        <div class="d-flex flex-wrap gap-2">
                                            <a href="{{ route('profile.2fa.index') }}" class="profile-hub-secondary-btn">
                                                <i class="bi bi-gear"></i>
                                                <span>Управление 2FA</span>
                                            </a>
                                            <form action="{{ route('profile.2fa.disable') }}" method="POST" class="d-inline">
                                                @csrf
                                                @method('POST')
                                                <button type="submit" class="profile-hub-danger-btn">
                                                    <i class="bi bi-power"></i>
                                                    <span>Отключить 2FA</span>
                                                </button>
                                            </form>
                                        </div>
                                    @else
                                        <div class="profile-hub-note mb-3">
                                            <p class="mb-0">
                                                Добавьте дополнительный уровень безопасности к вашему аккаунту с помощью двухфакторной аутентификации.
                                            </p>
                                        </div>
                                        <a href="{{ route('profile.2fa.index') }}" class="btn home-server-play profile-hub-btn">
                                            <i class="bi bi-shield-check"></i>
                                            <span>Включить 2FA</span>
                                        </a>
                                    @endif
                                </div>
                                @endif

                                <div class="profile-security-block">
                                    <h3 class="profile-section-title">Интеграция с Discord</h3>

                                    @if(auth()->user()->discordAccount)
                                        <div class="alert alert-success">
                                            <div class="d-flex align-items-center">
                                                <i class="bi bi-discord me-2 fs-4"></i>
                                                <div>
                                                    Аккаунт Discord привязан
                                                    <div class="fw-bold">{{ auth()->user()->discordAccount->name }}</div>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="profile-hub-note mb-0">
                                            <p class="mb-0">{{ trans('messages.profile.discord.unlink_admin') }}</p>
                                        </div>
                                    @else
                                        <div class="profile-hub-note mb-3">
                                            <p class="mb-0">
                                                Привяжите свой Discord аккаунт для удобного входа и уведомлений.
                                            </p>
                                        </div>
                                        <a href="{{ route('profile.discord.link') }}" class="btn home-server-play profile-hub-btn profile-hub-btn--discord">
                                            <i class="bi bi-discord"></i>
                                            <span>Привязать Discord</span>
                                        </a>
                                    @endif
                                </div>

                                @if(($enableTelegramLink ?? telegram_link_enabled()) || auth()->user()->telegramAccount)
                                <div class="profile-security-block">
                                    <h3 class="profile-section-title">Интеграция с Telegram</h3>

                                    @if(auth()->user()->telegramAccount)
                                        <div class="alert alert-success">
                                            <div class="d-flex align-items-center">
                                                <i class="bi bi-telegram me-2 fs-4"></i>
                                                <div>
                                                    Аккаунт Telegram привязан
                                                    <div class="fw-bold">{{ auth()->user()->telegramAccount->displayName() }}</div>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="profile-hub-note mb-0">
                                            <p class="mb-0">{{ trans('messages.profile.telegram.unlink_admin') }}</p>
                                        </div>
                                    @elseif($enableTelegramLink ?? telegram_link_enabled())
                                        <div class="profile-hub-note mb-3">
                                            <p class="mb-0">
                                                Привяжите Telegram через официальный Login Widget — так сайт получит подтверждённый ID вашего аккаунта.
                                            </p>
                                        </div>
                                        <div class="profile-telegram-widget">
                                            <script async src="https://telegram.org/js/telegram-widget.js?22"
                                                    data-telegram-login="{{ $telegramBotUsername }}"
                                                    data-size="large"
                                                    data-radius="8"
                                                    data-auth-url="{{ route('profile.telegram.callback') }}"
                                                    data-request-access="write"></script>
                                        </div>
                                    @endif
                                </div>
                                @endif
                            </div>
                        </div>
                    </div>

                    <!-- Вкладка Скин -->
                    <div class="tab-pane fade" id="skin-content" role="tabpanel">
                        <div class="profile-hub-card mb-3">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-eye me-2"></i>Предпросмотр
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                <div class="row g-3 justify-content-center text-center">
                                    <div class="col-sm-6 col-lg-5">
                                        <div class="profile-skin-preview-card">
                                            <div class="profile-skin-preview-frame mb-3">
                                                <img src="{{ url('/api/skin-api/avatars/body/' . auth()->user()->id) }}?t={{ time() }}"
                                                     alt="Скин"
                                                     class="profile-skin-body-preview"
                                                     width="128"
                                                     height="256"
                                                     loading="lazy"
                                                     onerror="this.onerror=null; this.src='{{ url('/api/skin-api/avatars/body/default') }}?t={{ time() }}'">
                                            </div>
                                            <h3 class="profile-skin-preview-title">Скин</h3>
                                            <div class="text-muted small">Как в игре</div>
                                        </div>
                                    </div>
                                    <div class="col-sm-6 col-lg-5">
                                        <div class="profile-skin-preview-card">
                                            <div class="profile-skin-preview-frame mb-3">
                                                <img src="{{ url('/api/skin-api/capes/' . auth()->user()->id) }}?t={{ time() }}"
                                                     alt="Плащ"
                                                     class="profile-cape-preview"
                                                     loading="lazy"
                                                     onerror="this.onerror=null; this.style.display='none'; this.nextElementSibling?.classList.remove('d-none');">
                                                <div class="text-muted small d-none">Плащ не загружен</div>
                                            </div>
                                            <h3 class="profile-skin-preview-title">Плащ</h3>
                                            <div class="text-muted small">Предпросмотр</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="profile-hub-card mb-3">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-upload me-2"></i>Загрузка скина и плаща
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                <div class="row g-4">
                                    <div class="col-md-6">
                                        <h3 class="profile-section-title">
                                            <i class="bi bi-person-bounding-box me-2"></i>Скин
                                        </h3>
                                        <form action="{{ route('profile.skin.upload') }}" method="POST" enctype="multipart/form-data">
                                            @csrf
                                            <div class="mb-3">
                                                <input type="file" class="form-control @error('skin') is-invalid @enderror"
                                                       id="skin_tab" name="skin" accept=".png" required>
                                                @error('skin')
                                                    <div class="invalid-feedback">{{ $message }}</div>
                                                @enderror
                                                <div class="form-text text-muted small mt-1">
                                                    PNG, 64×64 или 64×128 пикселей, до 2MB
                                                </div>
                                            </div>
                                            <button type="submit" class="btn home-server-play profile-hub-btn">
                                                <i class="bi bi-cloud-upload"></i>
                                                <span>Загрузить скин</span>
                                            </button>
                                        </form>
                                    </div>
                                    <div class="col-md-6 profile-skin-upload-split">
                                        <h3 class="profile-section-title">
                                            <i class="bi bi-shield-shaded me-2"></i>Плащ
                                        </h3>
                                        <form action="{{ route('profile.skin.cape.upload') }}" method="POST" enctype="multipart/form-data">
                                            @csrf
                                            <div class="mb-3">
                                                <input type="file" class="form-control @error('cape') is-invalid @enderror"
                                                       id="cape_tab" name="cape" accept=".png">
                                                @error('cape')
                                                    <div class="invalid-feedback">{{ $message }}</div>
                                                @enderror
                                                <div class="form-text text-muted small mt-1">
                                                    PNG, 64×32 пикселя, до 1MB
                                                </div>
                                            </div>
                                            <button type="submit" class="profile-hub-secondary-btn">
                                                <i class="bi bi-cloud-upload"></i>
                                                <span>Загрузить плащ</span>
                                            </button>
                                        </form>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="profile-hub-card profile-hub-card--warn">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-arrow-clockwise me-2"></i>Сброс скина
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                <p class="profile-hub-lead mb-3">
                                    Сброс установит пустой скин и плащ, после чего будет использован стандартный скин Minecraft.
                                </p>
                                <form action="{{ route('profile.skin.reset') }}" method="POST" class="d-inline">
                                    @csrf
                                    @method('DELETE')
                                    <button type="submit" class="profile-hub-warn-btn"
                                            onclick="return confirm('Вы уверены, что хотите сбросить скин и плащ?')">
                                        <i class="bi bi-arrow-clockwise"></i>
                                        <span>Сбросить скин и плащ</span>
                                    </button>
                                </form>
                            </div>
                        </div>
                    </div>

                    <div class="tab-pane fade" id="stats-content" role="tabpanel">
                        <div class="profile-hub-card">
                            <div class="profile-hub-card-head">
                                <h2 class="profile-hub-card-title">
                                    <i class="bi bi-bar-chart-line me-2"></i>Игровая статистика
                                </h2>
                            </div>
                            <div class="profile-hub-card-body">
                                <p class="profile-hub-lead mb-4">
                                    Показатели обновляются автоматически, пока вы онлайн на сервере.
                                </p>

                                @forelse(($playerStats ?? collect()) as $stat)
                                    <div class="profile-game-stats-block">
                                        <div class="profile-game-stats-head">
                                            <div>
                                                <h3 class="profile-section-title mb-0">
                                                    {{ $stat->server?->name ?? 'Сервер #'.$stat->server_id }}
                                                </h3>
                                                @if($stat->synced_at)
                                                    <small class="text-muted">
                                                        Обновлено {{ format_date($stat->synced_at, true) }}
                                                    </small>
                                                @endif
                                            </div>
                                        </div>

                                        <div class="profile-game-stats-summary">
                                            <div class="profile-game-stat profile-game-stat--accent">
                                                <i class="bi bi-clock"></i>
                                                <span class="profile-game-stat-label">Всего в игре</span>
                                                <strong class="profile-game-stat-value">{{ $stat->formattedPlayTime() }}</strong>
                                            </div>
                                            <div class="profile-game-stat profile-game-stat--accent">
                                                <i class="bi bi-sun"></i>
                                                <span class="profile-game-stat-label">За сегодня</span>
                                                <strong class="profile-game-stat-value">{{ $stat->formattedPlayTimeToday() }}</strong>
                                            </div>
                                            <div class="profile-game-stat profile-game-stat--accent">
                                                <i class="bi bi-calendar3"></i>
                                                <span class="profile-game-stat-label">За месяц</span>
                                                <strong class="profile-game-stat-value">{{ $stat->formattedPlayTimeMonth() }}</strong>
                                            </div>
                                        </div>

                                        <div class="profile-game-stats-grid">
                                            <div class="profile-game-stat">
                                                <i class="bi bi-heart-pulse"></i>
                                                <span class="profile-game-stat-label">С последней смерти</span>
                                                <strong class="profile-game-stat-value">{{ $stat->formattedTimeSinceDeath() }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-skull"></i>
                                                <span class="profile-game-stat-label">Смерти</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->deaths) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-bug"></i>
                                                <span class="profile-game-stat-label">Убито мобов</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->mob_kills) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-people"></i>
                                                <span class="profile-game-stat-label">Убито игроков</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->player_kills) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-stars"></i>
                                                <span class="profile-game-stat-label">Уровень XP</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->xp_level) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-hammer"></i>
                                                <span class="profile-game-stat-label">Добыто блоков</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->blocks_mined) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-bricks"></i>
                                                <span class="profile-game-stat-label">Поставлено блоков</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->blocks_placed) }}</strong>
                                            </div>
                                            <div class="profile-game-stat">
                                                <i class="bi bi-signpost-2"></i>
                                                <span class="profile-game-stat-label">Пройдено</span>
                                                <strong class="profile-game-stat-value">{{ number_format($stat->walkKilometers(), 2) }} км</strong>
                                            </div>
                                        </div>
                                    </div>
                                @empty
                                    <div class="profile-game-stats-empty">
                                        <i class="bi bi-inbox"></i>
                                        <p class="mb-1">Пока нет статистики с серверов.</p>
                                        <p class="text-muted small mb-0">
                                            Зайдите на сервер — показатели появятся после обновления.
                                        </p>
                                    </div>
                                @endforelse
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</section>
@endsection

@push('scripts')
<script>
document.addEventListener('DOMContentLoaded', function() {
    const urlHash = window.location.hash;
    if (urlHash) {
        const tabTrigger = document.getElementById(urlHash.substring(1));
        if (tabTrigger && tabTrigger.dataset.bsToggle === 'pill') {
            new bootstrap.Tab(tabTrigger).show();
        }
    }

    document.querySelectorAll('.toggle-password').forEach(button => {
        button.addEventListener('click', function() {
            const input = this.closest('.input-group').querySelector('input[type="password"], input[type="text"]');
            const type = input.getAttribute('type') === 'password' ? 'text' : 'password';
            input.setAttribute('type', type);
            this.innerHTML = type === 'password' ? '<i class="bi bi-eye"></i>' : '<i class="bi bi-eye-slash"></i>';
        });
    });
});
</script>
@endpush
