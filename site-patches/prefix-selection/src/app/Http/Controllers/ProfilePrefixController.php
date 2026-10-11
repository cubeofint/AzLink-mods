<?php

namespace Azuriom\Http\Controllers;

use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
use RuntimeException;

class ProfilePrefixController extends Controller
{
    public function select(Request $request, PrefixSelection $selection): RedirectResponse
    {
        abort_unless(PrefixSelection::enabled(), 404);

        $data = $this->validate($request, [
            'server_id' => ['required', 'integer', Rule::exists('servers', 'id')],
            'group' => ['nullable', 'string', 'max:64'],
        ]);

        $group = filled($data['group'] ?? null) ? (string) $data['group'] : null;

        try {
            $selection->select($request->user(), (int) $data['server_id'], $group, 'profile');
        } catch (RuntimeException $e) {
            $message = match ($e->getMessage()) {
                'prefix_staff_locked' => trans('prefixes.cabinet.staff_locked'),
                'prefix_not_synced' => trans('prefixes.cabinet.not_synced'),
                'prefix_not_available' => trans('prefixes.cabinet.not_available'),
                'prefix_disabled' => trans('prefixes.cabinet.disabled'),
                'prefix_already_selected' => trans('prefixes.cabinet.already_selected'),
                'prefix_op_pending' => trans('prefixes.cabinet.pending'),
                default => trans('prefixes.cabinet.failed'),
            };

            return to_route('profile.index')
                ->with('error', $message)
                ->withFragment('prefixes-tab');
        }

        return to_route('profile.index')
            ->with('success', trans('prefixes.cabinet.queued'))
            ->withFragment('prefixes-tab');
    }
}
