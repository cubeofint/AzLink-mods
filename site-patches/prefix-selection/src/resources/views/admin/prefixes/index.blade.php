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

    <p class="text-muted">{{ trans('prefixes.admin.catalog.readonly') }}</p>

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
        <div class="card-header">
            <h5 class="card-title mb-0">{{ trans('prefixes.admin.catalog.title') }}</h5>
        </div>
        <div class="card-body">
            <form method="GET" class="row g-3 mb-4">
                <div class="col-md-4">
                    <label class="form-label" for="catalogServerInput">{{ trans('prefixes.admin.ops.server') }}</label>
                    <select class="form-select" id="catalogServerInput" name="server_id" onchange="this.form.submit()">
                        <option value="">{{ trans('messages.all') ?? '—' }}</option>
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
                        <th>{{ trans('prefixes.admin.catalog.enabled') }}</th>
                    </tr>
                    </thead>
                    <tbody>
                    @forelse($items as $item)
                        <tr>
                            <td>{{ $item->server?->name ?? '—' }}</td>
                            <td><code>{{ $item->group }}</code></td>
                            <td>{{ $item->visibleDisplayName() }}</td>
                            <td>{!! $formatter->toHtml($item->visiblePrefixRaw()) !!}</td>
                            <td>{{ \Azuriom\Models\PrefixCatalog::categoryLabel($item->category) }}</td>
                            <td>{{ $item->visibleSort() }}</td>
                            <td>{{ $item->enabled ? trans('messages.yes') : trans('messages.no') }}</td>
                        </tr>
                    @empty
                        <tr>
                            <td colspan="7" class="text-center text-muted">{{ trans('prefixes.admin.catalog.empty') }}</td>
                        </tr>
                    @endforelse
                    </tbody>
                </table>
            </div>
        </div>
    </div>
@endsection
