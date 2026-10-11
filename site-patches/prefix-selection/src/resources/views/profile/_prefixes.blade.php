{{-- Default-theme fallback; the coints theme ships a richer partial. --}}
@php
    $prefixSelection = app(\Azuriom\Services\Prefix\PrefixSelection::class);
    $prefixServers = $prefixSelection->cabinetServers(auth()->user());
    $selectRoute = Route::has('profile.prefixes.select')
        ? route('profile.prefixes.select')
        : url('/api/profile/prefixes/select');
@endphp

<div class="card mb-4">
    <div class="card-header">{{ trans('prefixes.cabinet.title') }}</div>
    <div class="card-body">
        <p class="text-muted">{{ trans('prefixes.cabinet.intro') }}</p>
        @forelse($prefixServers as $block)
            <h6>{{ $block['server']->name }}</h6>
            @if($block['locked_staff'])
                <div class="alert alert-warning py-2">{{ trans('prefixes.cabinet.staff_locked') }}</div>
            @endif
            @if($block['row'] === null)
                <p class="text-muted">{{ trans('prefixes.cabinet.not_synced') }}</p>
            @else
                <p>{{ trans('prefixes.cabinet.current') }}: {{ $block['selected'] ?: trans('prefixes.cabinet.auto') }}</p>
                @if($block['pending'])
                    <p class="text-info">{{ trans('prefixes.status.pending') }}</p>
                @endif
                <form action="{{ $selectRoute }}" method="POST" class="mb-2">
                    @csrf
                    <input type="hidden" name="server_id" value="{{ $block['server']->id }}">
                    <input type="hidden" name="group" value="">
                    <button class="btn btn-sm btn-secondary" @disabled($block['pending'] || $block['locked_staff'])>{{ trans('prefixes.cabinet.auto') }}</button>
                </form>
                @foreach($block['options'] as $option)
                    <form action="{{ $selectRoute }}" method="POST" class="mb-2">
                        @csrf
                        <input type="hidden" name="server_id" value="{{ $block['server']->id }}">
                        <input type="hidden" name="group" value="{{ $option['group'] }}">
                        <button class="btn btn-sm btn-primary" @disabled($block['pending'] || $block['locked_staff'])>
                            {!! $option['preview'] !!} {{ $option['display_name'] }}
                        </button>
                    </form>
                @endforeach
            @endif
        @empty
            <p class="text-muted mb-0">{{ trans('prefixes.cabinet.empty') }}</p>
        @endforelse
    </div>
</div>
