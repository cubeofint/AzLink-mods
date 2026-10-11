<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\PrefixCatalog;
use Azuriom\Services\Prefix\PrefixFormatter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class PrefixCatalogController extends Controller
{
    public function index(PrefixFormatter $formatter)
    {
        $items = PrefixCatalog::query()->orderBy('sort')->orderBy('group')->get();

        return view('admin.prefixes.index', [
            'items' => $items,
            'formatter' => $formatter,
            'enabled' => PrefixSelection::enabled(),
        ]);
    }

    public function create(PrefixFormatter $formatter)
    {
        return view('admin.prefixes.form', [
            'item' => new PrefixCatalog(['enabled' => true, 'category' => 'other', 'sort' => 0]),
            'formatter' => $formatter,
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

    /**
     * @return array<string, mixed>
     */
    private function validated(Request $request, ?PrefixCatalog $item = null): array
    {
        $data = $this->validate($request, [
            'group' => [
                'required', 'string', 'max:64',
                Rule::unique('prefix_catalog', 'group')->ignore($item),
            ],
            'display_name' => ['required', 'string', 'max:120'],
            'prefix_raw' => ['nullable', 'string', 'max:255'],
            'category' => ['required', Rule::in(PrefixCatalog::CATEGORIES)],
            'sort' => ['required', 'integer', 'min:0', 'max:999999'],
            'enabled' => ['nullable', 'boolean'],
        ]);

        $data['enabled'] = $request->boolean('enabled');

        return $data;
    }
}
