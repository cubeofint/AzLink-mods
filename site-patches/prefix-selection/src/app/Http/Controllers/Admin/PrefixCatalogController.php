<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\Server;
use Azuriom\Services\Prefix\PrefixFormatter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\Request;

class PrefixCatalogController extends Controller
{
    public function index(Request $request, PrefixFormatter $formatter)
    {
        $serverId = $request->input('server_id');

        $query = PrefixCatalog::query()
            ->with('server:id,name')
            ->whereNotNull('server_id')
            ->orderBy('sort')
            ->orderBy('group');

        if (filled($serverId)) {
            $query->where('server_id', (int) $serverId);
        }

        return view('admin.prefixes.index', [
            'items' => $query->get(),
            'formatter' => $formatter,
            'enabled' => PrefixSelection::enabled(),
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(['id', 'name']),
            'imports' => PrefixCatalogImport::query()->with('server:id,name')->orderByDesc('imported_at')->get(),
            'serverId' => $serverId,
        ]);
    }
}
