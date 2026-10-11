@extends('admin.layouts.admin')

@section('title', 'Доставки')

@section('content')
    @unless($enabled)
        <div class="alert alert-info">Автоматическая выдача выключена (настройка <code>shop.configured_delivery.enabled</code>). Операции ниже не создаются tick(), пока флаг выключен. Пакеты с mode=bridge всё равно пишут операции, если сервер есть в <code>shop.configured_delivery.servers</code>.</div>
    @endunless

    <div class="alert {{ $problems > 0 ? 'alert-warning' : 'alert-success' }}">
        Проблемных доставок: <strong>{{ $problems }}</strong>
        <span class="small text-muted">(failed, uncertain, failed:no_uuid)</span>
    </div>

    <div class="card shadow mb-4">
        <div class="card-body">
            <form method="GET" class="row g-3 mb-4">
                <div class="col-md-2">
                    <label class="form-label" for="serverInput">Сервер</label>
                    <select class="form-select" id="serverInput" name="server_id">
                        <option value="">Все</option>
                        @foreach($servers as $server)
                            <option value="{{ $server->id }}" @selected((string) $serverId === (string) $server->id)>{{ $server->name }} (#{{ $server->id }})</option>
                        @endforeach
                    </select>
                </div>
                <div class="col-md-2">
                    <label class="form-label" for="playerInput">Игрок</label>
                    <input type="text" class="form-control" id="playerInput" name="player" value="{{ $player }}" placeholder="ник или UUID">
                </div>
                <div class="col-md-2">
                    <label class="form-label" for="typeInput">Тип</label>
                    <select class="form-select" id="typeInput" name="type">
                        <option value="">Все</option>
                        @foreach($types as $case)
                            <option value="{{ $case->value }}" @selected($type === $case->value)>{{ $case->value }}</option>
                        @endforeach
                    </select>
                </div>
                <div class="col-md-2">
                    <label class="form-label" for="statusInput">Статус</label>
                    <select class="form-select" id="statusInput" name="status">
                        <option value="">Все</option>
                        @foreach(\Azuriom\Plugin\Shop\Enums\MinecraftOperationStatus::cases() as $case)
                            <option value="{{ $case->value }}" @selected($status === $case->value)>{{ $case->value }}</option>
                        @endforeach
                    </select>
                </div>
                <div class="col-md-2">
                    <label class="form-label" for="purchaseInput">Покупка #</label>
                    <input type="number" class="form-control" id="purchaseInput" name="purchase_id" value="{{ $purchaseId }}">
                </div>
                <div class="col-md-2 d-flex align-items-end">
                    <button type="submit" class="btn btn-primary"><i class="bi bi-search"></i> {{ trans('messages.actions.search') }}</button>
                </div>
            </form>

            <div class="table-responsive">
                <table class="table table-striped align-middle">
                    <thead>
                    <tr>
                        <th>#</th>
                        <th>Покупка</th>
                        <th>Игрок</th>
                        <th>Сервер</th>
                        <th>Операция</th>
                        <th>Статус</th>
                        <th>Попытки</th>
                        <th>Ошибка</th>
                        <th>{{ trans('messages.fields.date') }}</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    @forelse($operations as $op)
                        @php
                            $badge = match ($op->status->value) {
                                'succeeded' => 'success', 'failed' => 'danger', 'uncertain' => 'warning',
                                'claimed' => 'info', 'superseded', 'cancelled' => 'secondary', default => 'primary',
                            };
                        @endphp
                        <tr>
                            <td>{{ $op->id }}</td>
                            <td>
                                @if($op->purchase_id)
                                    #{{ $op->purchase_id }}
                                    <div class="small text-muted">{{ $op->purchase?->privilege_name_snapshot }} · {{ $op->purchase?->delivery_status?->value }}</div>
                                @else
                                    <span class="small text-muted">{{ $op->operation_key }}</span>
                                @endif
                            </td>
                            <td>
                                @if($op->purchase?->user)
                                    <a href="{{ route('admin.users.edit', $op->purchase->user) }}">{{ $op->purchase->user->name }}</a>
                                @endif
                                <div class="small">{{ $op->player_uuid }}</div>
                            </td>
                            <td>{{ $op->server?->name ?? '—' }}</td>
                            <td><code>{{ $op->type->value }}</code></td>
                            <td><span class="badge bg-{{ $badge }}">{{ $op->status->value }}</span></td>
                            <td>{{ $op->attempts_count }}</td>
                            <td class="small">{{ $op->result_code ?? '—' }}@if($op->last_error)<div class="text-danger">{{ \Illuminate\Support\Str::limit($op->last_error, 160) }}</div>@endif</td>
                            <td class="text-nowrap">{{ format_date($op->created_at, true) }}@if($op->completed_at)<div class="small text-muted">{{ format_date($op->completed_at, true) }}</div>@endif</td>
                            <td class="text-nowrap">
                                <form method="POST" action="{{ route('shop.admin.deliveries.retry', $op) }}" class="d-inline">
                                    @csrf
                                    <button type="submit" class="btn btn-sm btn-outline-primary">Retry</button>
                                </form>
                                <form method="POST" action="{{ route('shop.admin.deliveries.mark', $op) }}" class="d-inline" onsubmit="return confirm('Отметить как доставленную вручную?')">
                                    @csrf
                                    <button type="submit" class="btn btn-sm btn-outline-secondary">Mark delivered manually</button>
                                </form>
                            </td>
                        </tr>
                    @empty
                        <tr><td colspan="10" class="text-center text-muted">{{ trans('messages.none') }}</td></tr>
                    @endforelse
                    </tbody>
                </table>
            </div>
            {{ $operations->links() }}
        </div>
    </div>
@endsection
