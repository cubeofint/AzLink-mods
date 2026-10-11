@extends('admin.layouts.admin')

@section('title', trans('prefixes.admin.catalog.title'))

@section('content')
    <ul class="nav nav-tabs mb-3">
        <li class="nav-item">
            <a class="nav-link active" href="{{ route('admin.prefixes.index') }}">{{ trans('prefixes.admin.catalog.title') }}</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" href="{{ route('admin.prefixes.operations') }}">{{ trans('prefixes.admin.ops.title') }}</a>
        </li>
    </ul>

    @unless($enabled)
        <div class="alert alert-info">{{ trans('prefixes.admin.catalog.flag_off') }}</div>
    @endunless

    @if($imports->isNotEmpty())
        <div class="card shadow mb-4">
            <div class="card-header">
                <h5 class="card-title mb-0">{{ trans('prefixes.admin.catalog.last_export') }}</h5>
            </div>
            <div class="card-body">
                <div class="table-responsive">
                    <table class="table table-sm mb-0">
                        <thead>
                        <tr>
                            <th>{{ trans('prefixes.admin.ops.server') }}</th>
                            <th>{{ trans('prefixes.admin.catalog.imported_at') }}</th>
                            <th>{{ trans('prefixes.admin.catalog.hash') }}</th>
                            <th>{{ trans('prefixes.admin.catalog.groups') }}</th>
                        </tr>
                        </thead>
                        <tbody>
                        @foreach($imports as $import)
                            <tr>
                                <td>{{ $import->server?->name ?? ('#'.$import->server_id) }}</td>
                                <td>{{ format_date($import->imported_at, true) }}</td>
                                <td><code>{{ \Illuminate\Support\Str::limit($import->hash, 12, '…') }}</code></td>
                                <td>{{ $import->group_count }}</td>
                            </tr>
                        @endforeach
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    @endif

    <div class="card shadow mb-4">
        <div class="card-header d-flex align-items-center justify-content-between">
            <h5 class="card-title mb-0">{{ trans('prefixes.admin.catalog.title') }}</h5>
            <a href="{{ route('admin.prefixes.create') }}" class="btn btn-primary btn-sm">
                <i class="bi bi-plus-lg"></i> {{ trans('prefixes.admin.catalog.create') }}
            </a>
        </div>
        <div class="card-body">
            <form method="GET" class="row g-3 mb-4">
                <div class="col-md-4">
                    <label class="form-label" for="catalogServerInput">{{ trans('prefixes.admin.ops.server') }}</label>
                    <select class="form-select" id="catalogServerInput" name="server_id" onchange="this.form.submit()">
                        <option value="">{{ trans('messages.all') ?? '—' }}</option>
                        <option value="global" @selected($serverId === 'global')>{{ trans('prefixes.admin.catalog.global') }}</option>
                        @foreach($servers as $server)
                            <option value="{{ $server->id }}" @selected((string) $serverId === (string) $server->id)>{{ $server->name }}</option>
                        @endforeach
                    </select>
                </div>
            </form>

            <div class="table-responsive">
                <table class="table table-striped align-middle">
                    <thead>
                    <tr>
                        <th>{{ trans('prefixes.admin.ops.server') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.group') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.display_name') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.preview') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.category') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.sort') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.source') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.missing_since') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.enabled') }}</th>
                        <th class="text-end"></th>
                    </tr>
                    </thead>
                    <tbody>
                    @forelse($items as $item)
                        <tr>
                            <td>{{ $item->server?->name ?? trans('prefixes.admin.catalog.global') }}</td>
                            <td><code>{{ $item->group }}</code></td>
                            <td>
                                {{ $item->visibleDisplayName() }}
                                @if($item->override_display_name)
                                    <span class="badge text-bg-warning">{{ trans('prefixes.admin.catalog.override') }}</span>
                                @endif
                            </td>
                            <td>{!! $formatter->toHtml($item->visiblePrefixRaw()) !!}</td>
                            <td>{{ \Azuriom\Models\PrefixCatalog::categoryLabel($item->category) }}</td>
                            <td>{{ $item->visibleSort() }}</td>
                            <td>
                                {{ $item->isAuto() ? trans('prefixes.admin.catalog.source_auto') : trans('prefixes.admin.catalog.source_manual') }}
                            </td>
                            <td>
                                @if($item->missing_since)
                                    {{ format_date($item->missing_since, true) }}
                                @else
                                    —
                                @endif
                            </td>
                            <td>{{ $item->enabled ? trans('messages.yes') : trans('messages.no') }}</td>
                            <td class="text-end">
                                <a href="{{ route('admin.prefixes.edit', $item) }}" class="btn btn-sm btn-outline-primary">
                                    {{ trans('messages.actions.edit') }}
                                </a>
                                @if($item->override_display_name || $item->override_sort || $item->override_enabled)
                                    <form action="{{ route('admin.prefixes.reset-auto', $item) }}" method="POST" class="d-inline">
                                        @csrf
                                        <button type="submit" class="btn btn-sm btn-outline-secondary">
                                            {{ trans('prefixes.admin.catalog.reset_auto_btn') }}
                                        </button>
                                    </form>
                                @endif
                                <form action="{{ route('admin.prefixes.destroy', $item) }}" method="POST" class="d-inline"
                                      onsubmit="return confirm(@json(trans('messages.actions.confirm') ?? 'OK'))">
                                    @csrf
                                    @method('DELETE')
                                    <button type="submit" class="btn btn-sm btn-outline-danger">{{ trans('messages.actions.delete') }}</button>
                                </form>
                            </td>
                        </tr>
                    @empty
                        <tr>
                            <td colspan="10" class="text-center text-muted">{{ trans('prefixes.admin.catalog.empty') }}</td>
                        </tr>
                    @endforelse
                    </tbody>
                </table>
            </div>
        </div>
    </div>
@endsection
