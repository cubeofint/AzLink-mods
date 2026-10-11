@extends('admin.layouts.admin')

@section('title', trans('prefixes.admin.ops.title'))

@section('content')
    <ul class="nav nav-tabs mb-3">
        <li class="nav-item">
            <a class="nav-link" href="{{ route('admin.prefixes.index') }}">{{ trans('prefixes.admin.catalog.title') }}</a>
        </li>
        <li class="nav-item">
            <a class="nav-link active" href="{{ route('admin.prefixes.operations') }}">{{ trans('prefixes.admin.ops.title') }}</a>
        </li>
    </ul>

    <div class="card shadow mb-4">
        <div class="card-body">
            <form method="GET" class="row g-3 mb-4">
                <div class="col-md-2">
                    <label class="form-label" for="userIdInput">ID</label>
                    <input type="number" class="form-control" id="userIdInput" name="user_id" value="{{ $userId }}">
                </div>
                <div class="col-md-3">
                    <label class="form-label" for="playerInput">{{ trans('prefixes.admin.ops.user') }}</label>
                    <input type="text" class="form-control" id="playerInput" name="player" value="{{ $player }}">
                </div>
                <div class="col-md-3">
                    <label class="form-label" for="serverInput">{{ trans('prefixes.admin.ops.server') }}</label>
                    <select class="form-select" id="serverInput" name="server_id">
                        <option value="">{{ trans('messages.all') ?? '—' }}</option>
                        @foreach($servers as $server)
                            <option value="{{ $server->id }}" @selected((string) $serverId === (string) $server->id)>{{ $server->name }}</option>
                        @endforeach
                    </select>
                </div>
                <div class="col-md-2">
                    <label class="form-label" for="statusInput">{{ trans('prefixes.admin.ops.status') }}</label>
                    <select class="form-select" id="statusInput" name="status">
                        <option value="">{{ trans('messages.all') ?? '—' }}</option>
                        @foreach(['pending', 'applied', 'rejected'] as $statusKey)
                            <option value="{{ $statusKey }}" @selected($status === $statusKey)>{{ trans('prefixes.status.'.$statusKey) }}</option>
                        @endforeach
                    </select>
                </div>
                <div class="col-md-2 d-flex align-items-end">
                    <button type="submit" class="btn btn-primary"><i class="bi bi-search"></i> {{ trans('messages.actions.search') }}</button>
                </div>
            </form>

            <div class="table-responsive">
                <table class="table table-striped">
                    <thead>
                    <tr>
                        <th>{{ trans('prefixes.admin.ops.op_id') }}</th>
                        <th>{{ trans('prefixes.admin.ops.user') }}</th>
                        <th>{{ trans('prefixes.admin.ops.server') }}</th>
                        <th>{{ trans('prefixes.admin.ops.group') }}</th>
                        <th>{{ trans('prefixes.admin.ops.status') }}</th>
                        <th>{{ trans('prefixes.admin.ops.reason') }}</th>
                        <th>{{ trans('prefixes.admin.ops.selected') }}</th>
                        <th>{{ trans('prefixes.admin.ops.source') }}</th>
                        <th>{{ trans('prefixes.admin.ops.created') }}</th>
                    </tr>
                    </thead>
                    <tbody>
                    @forelse($operations as $op)
                        <tr>
                            <td><code>{{ \Illuminate\Support\Str::limit($op->id, 8, '') }}</code></td>
                            <td>
                                @if($op->user)
                                    <a href="{{ route('admin.users.edit', $op->user) }}">{{ $op->user->name }}</a>
                                @else
                                    —
                                @endif
                            </td>
                            <td>{{ $op->server?->name ?? '—' }}</td>
                            <td>{{ $op->group ?? trans('prefixes.cabinet.auto') }}</td>
                            <td>{{ $op->statusLabel() }}</td>
                            <td class="small text-muted">{{ $op->reason ?: '—' }}</td>
                            <td>{{ $op->selected ?? '—' }}</td>
                            <td>{{ $op->source ?: '—' }}</td>
                            <td>{{ format_date($op->created_at, true) }}</td>
                        </tr>
                    @empty
                        <tr><td colspan="9" class="text-center text-muted">{{ trans('messages.none') }}</td></tr>
                    @endforelse
                    </tbody>
                </table>
            </div>
            {{ $operations->links() }}
        </div>
    </div>
@endsection
