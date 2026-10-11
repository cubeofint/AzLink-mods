<?php

namespace Azuriom\Http\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\Server;
use Azuriom\Models\User;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\RedirectResponse;
use RuntimeException;

class PrefixUserController extends Controller
{
    public function reset(User $user, Server $server, PrefixSelection $selection): RedirectResponse
    {
        abort_unless(PrefixSelection::enabled(), 404);

        try {
            $selection->select($user, $server->id, null, 'admin_reset');
        } catch (RuntimeException $e) {
            $message = match ($e->getMessage()) {
                'prefix_not_synced' => trans('prefixes.admin.user.not_synced'),
                'prefix_already_selected' => trans('prefixes.admin.user.already_auto'),
                'prefix_op_pending' => trans('prefixes.admin.user.pending'),
                default => trans('prefixes.admin.user.reset_failed'),
            };

            return redirect()
                ->to(route('admin.users.edit', $user).'#user-prefixes')
                ->with('error', $message);
        }

        return redirect()
            ->to(route('admin.users.edit', $user).'#user-prefixes')
            ->with('success', trans('prefixes.admin.user.reset_queued'));
    }
}
