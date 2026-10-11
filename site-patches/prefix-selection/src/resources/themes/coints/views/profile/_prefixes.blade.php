@php
    $prefixSelection = app(\Azuriom\Services\Prefix\PrefixSelection::class);
    $prefixServers = $prefixSelection->cabinetServers(auth()->user());
    $selectRoute = Route::has('profile.prefixes.select')
        ? route('profile.prefixes.select')
        : url('/api/profile/prefixes/select');
@endphp

<div class="profile-hub-card">
    <div class="profile-hub-card-head">
        <h2 class="profile-hub-card-title">
            <i class="bi bi-chat-square-quote me-2"></i>{{ trans('prefixes.cabinet.title') }}
        </h2>
    </div>
    <div class="profile-hub-card-body">
        <p class="text-muted mb-2">{{ trans('prefixes.cabinet.intro') }}</p>
        <p class="small text-muted mb-3">{{ trans('prefixes.cabinet.always_shown') }}</p>

        @if($prefixServers->isEmpty())
            <p class="text-muted mb-0">{{ trans('prefixes.cabinet.empty') }}</p>
        @else
            <ul class="nav nav-pills profile-hub-tabs flex-wrap mb-3" role="tablist">
                @foreach($prefixServers as $index => $block)
                    <li class="nav-item" role="presentation">
                        <button class="nav-link @if($index === 0) active @endif"
                                id="prefix-server-tab-{{ $block['server']->id }}"
                                data-bs-toggle="pill"
                                data-bs-target="#prefix-server-{{ $block['server']->id }}"
                                type="button"
                                role="tab">
                            {{ $block['server']->name }}
                        </button>
                    </li>
                @endforeach
            </ul>

            <div class="tab-content">
                @foreach($prefixServers as $index => $block)
                    @php
                        $row = $block['row'];
                        $pending = $block['pending'];
                        $latest = $block['latest'];
                        $options = $block['options'];
                    @endphp
                    <div class="tab-pane fade @if($index === 0) show active @endif"
                         id="prefix-server-{{ $block['server']->id }}"
                         role="tabpanel">
                        @if($row === null)
                            <p class="text-muted mb-0">{{ trans('prefixes.cabinet.not_synced') }}</p>
                        @else
                            <p class="mb-2">
                                <strong>{{ trans('prefixes.cabinet.current') }}:</strong>
                                @if($block['selected'])
                                    @php $current = $options->firstWhere('group', $block['selected']); @endphp
                                    @if($current)
                                        {!! $current['preview'] !!}
                                        <span class="text-muted small">{{ $current['display_name'] }}</span>
                                    @else
                                        <code>{{ $block['selected'] }}</code>
                                    @endif
                                @else
                                    {{ trans('prefixes.cabinet.auto') }}
                                @endif
                            </p>

                            @if($pending)
                                <div class="alert alert-info py-2">
                                    {{ trans('prefixes.status.pending') }}:
                                    {{ $pending->group ?: trans('prefixes.cabinet.auto') }}
                                </div>
                            @elseif($latest && $latest->status !== 'pending')
                                <p class="small text-muted">
                                    {{ trans('prefixes.cabinet.last_op') }}:
                                    {{ $latest->statusLabel() }}
                                    @if($latest->reason)
                                        — {{ $latest->reason }}
                                    @endif
                                </p>
                            @endif

                            <div class="table-responsive">
                                <table class="table table-sm align-middle">
                                    <tbody>
                                    <tr>
                                        <td>
                                            <strong>{{ trans('prefixes.cabinet.auto') }}</strong>
                                            <div class="small text-muted">{{ trans('prefixes.cabinet.auto_hint') }}</div>
                                        </td>
                                        <td class="text-end" style="width: 8rem">
                                            @if($block['selected'] === null && ! $pending)
                                                <span class="badge text-bg-success">{{ trans('prefixes.cabinet.selected') }}</span>
                                            @else
                                                <form action="{{ $selectRoute }}" method="POST" class="d-inline">
                                                    @csrf
                                                    <input type="hidden" name="server_id" value="{{ $block['server']->id }}">
                                                    <input type="hidden" name="group" value="">
                                                    <button type="submit" class="btn btn-sm home-server-play profile-hub-btn" @disabled($pending)>
                                                        {{ trans('prefixes.cabinet.select') }}
                                                    </button>
                                                </form>
                                            @endif
                                        </td>
                                    </tr>
                                    @foreach($options as $option)
                                        <tr>
                                            <td>
                                                <div class="prefix-preview">{!! $option['preview'] ?: '<span class="text-muted">'.e(trans('prefixes.cabinet.no_preview')).'</span>' !!}</div>
                                                <div>
                                                    <strong>{{ $option['display_name'] }}</strong>
                                                    <span class="badge text-bg-secondary">{{ $option['category_label'] }}</span>
                                                </div>
                                            </td>
                                            <td class="text-end">
                                                @if($block['selected'] === $option['group'] && ! $pending)
                                                    <span class="badge text-bg-success">{{ trans('prefixes.cabinet.selected') }}</span>
                                                @else
                                                    <form action="{{ $selectRoute }}" method="POST" class="d-inline">
                                                        @csrf
                                                        <input type="hidden" name="server_id" value="{{ $block['server']->id }}">
                                                        <input type="hidden" name="group" value="{{ $option['group'] }}">
                                                        <button type="submit" class="btn btn-sm home-server-play profile-hub-btn" @disabled($pending)>
                                                            {{ trans('prefixes.cabinet.select') }}
                                                        </button>
                                                    </form>
                                                @endif
                                            </td>
                                        </tr>
                                    @endforeach
                                    </tbody>
                                </table>
                            </div>

                            @if($block['synced_at'])
                                <p class="small text-muted mb-0">
                                    {{ trans('prefixes.cabinet.synced_at') }}: {{ format_date($block['synced_at'], true) }}
                                </p>
                            @endif
                        @endif
                    </div>
                @endforeach
            </div>
        @endif
    </div>
</div>
