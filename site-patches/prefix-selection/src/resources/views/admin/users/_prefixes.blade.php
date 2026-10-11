@php
    $prefixEnabled = \Azuriom\Services\Prefix\PrefixSelection::enabled();
    $prefixBlocks = $prefixEnabled
        ? app(\Azuriom\Services\Prefix\PrefixSelection::class)->cabinetServers($user)
        : collect();
@endphp

<div class="card shadow mb-4" id="user-prefixes">
    <div class="card-header d-flex align-items-center justify-content-between">
        <h5 class="card-title mb-0">{{ trans('prefixes.admin.user.title') }}</h5>
        <a href="{{ route('admin.prefixes.index') }}" class="btn btn-sm btn-outline-secondary">{{ trans('prefixes.admin.catalog.title') }}</a>
    </div>
    <div class="card-body">
        @unless($prefixEnabled)
            <div class="alert alert-info mb-0">{{ trans('prefixes.admin.catalog.flag_off') }}</div>
        @else
            @if($prefixBlocks->every(fn ($block) => $block['row'] === null))
                <p class="text-muted mb-0">{{ trans('prefixes.admin.user.empty') }}</p>
            @else
                <div class="table-responsive">
                    <table class="table table-sm table-bordered mb-0">
                        <thead>
                        <tr>
                            <th>{{ trans('prefixes.admin.ops.server') }}</th>
                            <th>{{ trans('prefixes.admin.user.selected') }}</th>
                            <th>{{ trans('prefixes.admin.user.available') }}</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        @foreach($prefixBlocks as $block)
                            @continue($block['row'] === null)
                            <tr>
                                <td>{{ $block['server']->name }}</td>
                                <td>
                                    @if($block['selected'])
                                        @php $current = $block['options']->firstWhere('group', $block['selected']); @endphp
                                        @if($current)
                                            {!! $current['preview'] !!}
                                            <span class="small">{{ $current['display_name'] }}</span>
                                        @else
                                            <code>{{ $block['selected'] }}</code>
                                        @endif
                                    @else
                                        {{ trans('prefixes.cabinet.auto') }}
                                    @endif
                                    @if($block['pending'])
                                        <div class="small text-info">{{ trans('prefixes.status.pending') }}: {{ $block['pending']->group ?: trans('prefixes.cabinet.auto') }}</div>
                                    @endif
                                </td>
                                <td>
                                    @forelse($block['options'] as $option)
                                        <div class="small">{!! $option['preview'] !!} {{ $option['display_name'] }}</div>
                                    @empty
                                        —
                                    @endforelse
                                </td>
                                <td class="text-end">
                                    <form action="{{ route('admin.users.prefixes.reset', [$user, $block['server']]) }}"
                                          method="POST"
                                          onsubmit="return confirm(@json(trans('prefixes.admin.user.reset_confirm')))">
                                        @csrf
                                        <button type="submit" class="btn btn-sm btn-outline-warning" @disabled($block['pending'])>
                                            {{ trans('prefixes.admin.user.reset') }}
                                        </button>
                                    </form>
                                </td>
                            </tr>
                        @endforeach
                        </tbody>
                    </table>
                </div>
            @endif
        @endunless
    </div>
</div>
