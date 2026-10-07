<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\CurrencyServerMovement;
use Azuriom\Models\Server;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\Request;

class CurrencyServerMovementController extends Controller
{
    public function index(Request $request)
    {
        $userId = $request->input('user_id');
        $serverId = $request->input('server_id');
        $type = $request->input('type');
        $player = $request->input('player');

        $movements = CurrencyServerMovement::with(['server', 'fromUser', 'toUser'])
            ->when($userId, fn (Builder $q) => $q->where(fn (Builder $w) => $w->where('from_user_id', $userId)->orWhere('to_user_id', $userId)))
            ->when($serverId, fn (Builder $q) => $q->where('server_id', $serverId))
            ->when($type, fn (Builder $q) => $q->where('type', $type))
            ->when($player, fn (Builder $q) => $q->where(fn (Builder $w) => $w->where('from_name', $player)->orWhere('to_name', $player)))
            ->latest('occurred_at')->latest('id')
            ->paginate(50)
            ->withQueryString();

        return view('admin.currency-transactions.server-movements', [
            'movements' => $movements,
            'servers' => Server::orderBy('name')->get(['id', 'name']),
            'userId' => $userId,
            'serverId' => $serverId,
            'type' => $type,
            'player' => $player,
            'enabled' => CurrencyServerMovement::enabled(),
        ]);
    }
}
