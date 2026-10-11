<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\PrefixCatalog;
use Azuriom\Models\PrefixCatalogImport;
use Azuriom\Models\Server;
use Azuriom\Services\Prefix\PrefixFormatter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class PrefixCatalogController extends Controller
{
    public function index(Request $request, PrefixFormatter $formatter)
    {
        $serverId = $request->input('server_id');

        $query = PrefixCatalog::query()->with('server:id,name')->orderBy('sort')->orderBy('group');
        if ($serverId === 'global') {
            $query->whereNull('server_id');
        } elseif (filled($serverId)) {
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

    public function create(PrefixFormatter $formatter)
    {
        return view('admin.prefixes.form', [
            'item' => new PrefixCatalog([
                'enabled' => true,
                'category' => 'other',
                'sort' => 0,
                'source' => PrefixCatalog::SOURCE_MANUAL,
            ]),
            'formatter' => $formatter,
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(['id', 'name']),
        ]);
    }

    public function store(Request $request)
    {
        PrefixCatalog::query()->create($this->validated($request));

        return to_route('admin.prefixes.index')
            ->with('success', trans('prefixes.admin.catalog.created'));
    }

    public function edit(PrefixCatalog $prefix, PrefixFormatter $formatter)
    {
        return view('admin.prefixes.form', [
            'item' => $prefix,
            'formatter' => $formatter,
            'servers' => Server::query()->orderBy('position')->orderBy('name')->get(['id', 'name']),
        ]);
    }

    public function update(Request $request, PrefixCatalog $prefix)
    {
        $prefix->update($this->validated($request, $prefix));

        return to_route('admin.prefixes.index')
            ->with('success', trans('prefixes.admin.catalog.updated'));
    }

    public function destroy(PrefixCatalog $prefix)
    {
        $prefix->delete();

        return to_route('admin.prefixes.index')
            ->with('success', trans('prefixes.admin.catalog.deleted'));
    }

    public function resetAuto(PrefixCatalog $prefix)
    {
        $prefix->resetToAuto();

        return to_route('admin.prefixes.index')
            ->with('success', trans('prefixes.admin.catalog.reset_auto'));
    }

    /**
     * @return array<string, mixed>
     */
    private function validated(Request $request, ?PrefixCatalog $item = null): array
    {
        $serverId = $request->filled('server_id') ? (int) $request->input('server_id') : null;

        $unique = Rule::unique('prefix_catalog', 'group')->ignore($item);
        if ($serverId === null) {
            $unique->whereNull('server_id');
        } else {
            $unique->where('server_id', $serverId);
        }

        $data = $this->validate($request, [
            'server_id' => ['nullable', 'integer', Rule::exists('servers', 'id')],
            'group' => ['required', 'string', 'max:64', $unique],
            'display_name' => ['required', 'string', 'max:120'],
            'prefix_raw' => ['nullable', 'string', 'max:255'],
            'category' => ['required', 'string', 'max:16'],
            'sort' => ['required', 'integer', 'min:0', 'max:999999'],
            'enabled' => ['nullable', 'boolean'],
        ]);

        $data['server_id'] = $serverId;
        $data['enabled'] = $request->boolean('enabled');
        $data['source'] = $item?->source ?: PrefixCatalog::SOURCE_MANUAL;

        if ($item === null) {
            $data['override_display_name'] = true;
            $data['override_sort'] = true;
            $data['override_enabled'] = true;
        } else {
            $autoEnabled = $item->missing_since === null;
            $data['override_display_name'] = $data['display_name'] !== (string) ($item->auto_display_name ?: '');
            $data['override_sort'] = (int) $data['sort'] !== (int) $item->auto_sort;
            $data['override_enabled'] = $data['enabled'] !== $autoEnabled;
        }

        return $data;
    }
}
