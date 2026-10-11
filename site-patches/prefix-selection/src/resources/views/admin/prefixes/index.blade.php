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

    <div class="card shadow mb-4">
        <div class="card-header d-flex align-items-center justify-content-between">
            <h5 class="card-title mb-0">{{ trans('prefixes.admin.catalog.title') }}</h5>
            <a href="{{ route('admin.prefixes.create') }}" class="btn btn-primary btn-sm">
                <i class="bi bi-plus-lg"></i> {{ trans('prefixes.admin.catalog.create') }}
            </a>
        </div>
        <div class="card-body">
            <div class="table-responsive">
                <table class="table table-striped align-middle">
                    <thead>
                    <tr>
                        <th>{{ trans('prefixes.admin.catalog.group') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.display_name') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.preview') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.category') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.sort') }}</th>
                        <th>{{ trans('prefixes.admin.catalog.enabled') }}</th>
                        <th class="text-end"></th>
                    </tr>
                    </thead>
                    <tbody>
                    @forelse($items as $item)
                        <tr>
                            <td><code>{{ $item->group }}</code></td>
                            <td>{{ $item->display_name }}</td>
                            <td>{!! $formatter->toHtml($item->prefix_raw) !!}</td>
                            <td>{{ \Azuriom\Models\PrefixCatalog::categoryLabel($item->category) }}</td>
                            <td>{{ $item->sort }}</td>
                            <td>{{ $item->enabled ? trans('messages.yes') : trans('messages.no') }}</td>
                            <td class="text-end">
                                <a href="{{ route('admin.prefixes.edit', $item) }}" class="btn btn-sm btn-outline-primary">
                                    {{ trans('messages.actions.edit') }}
                                </a>
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
                            <td colspan="7" class="text-center text-muted">{{ trans('prefixes.admin.catalog.empty') }}</td>
                        </tr>
                    @endforelse
                    </tbody>
                </table>
            </div>
        </div>
    </div>
@endsection
