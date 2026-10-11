<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\PrefixOperation;
use Azuriom\Models\Server;
use Illuminate\Http\Request;

class PrefixOperationController extends Controller
{
    public function index(Request $request)
    {
        $query = PrefixOperation::query()->with(['user:id,name,game_id', 'server:id,name']);

        if ($request->filled('user_id')) {
            $query->where('user_id', (int) $request->input('user_id'));
        }

        if ($request->filled('player')) {
            $name = (string) $request->input('player');
            $query->whereHas('user', fn ($q) => $q->where('name', 'like', '%'.$name.'%'));
        }

        if ($request->filled('server_id')) {
            $query->where('server_id', (int) $request->input('server_id'));
        }

        if ($request->filled('status')) {
            $query->where('status', (string) $request->input('status'));
        }

        return view('admin.prefixes.operations', [
            'operations' => $query->orderByDesc('created_at')->paginate(40)->withQueryString(),
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(['id', 'name']),
            'userId' => $request->input('user_id'),
            'player' => $request->input('player'),
            'serverId' => $request->input('server_id'),
            'status' => $request->input('status'),
        ]);
    }
}
