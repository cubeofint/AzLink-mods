<?php

namespace Azuriom\Plugin\Shop\Controllers\Admin;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\Server;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationStatus;
use Azuriom\Plugin\Shop\Enums\MinecraftOperationType;
use Azuriom\Plugin\Shop\Models\MinecraftOperation;
use Azuriom\Plugin\Shop\Services\ConfiguredDeliveryService;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\View\View;

class DeliveryController extends Controller
{
    public function index(Request $request): View
    {
        $status = $request->input('status');
        $type = $request->input('type');
        $serverId = $request->input('server_id');
        $player = trim((string) $request->input('player'));
        $purchase = $request->input('purchase_id');

        $operations = MinecraftOperation::with(['server', 'purchase.user', 'attempts'])
            ->when($status, fn (Builder $q) => $q->where('status', $status))
            ->when($type, fn (Builder $q) => $q->where('type', $type))
            ->when($serverId, fn (Builder $q) => $q->where('server_id', $serverId))
            ->when($purchase, fn (Builder $q) => $q->where('purchase_id', $purchase))
            ->when($player !== '', function (Builder $q) use ($player) {
                $q->where(function (Builder $inner) use ($player) {
                    $inner->where('player_uuid', 'like', '%'.$player.'%')
                        ->orWhereHas('purchase.user', fn (Builder $user) => $user
                            ->where('name', 'like', '%'.$player.'%')
                            ->orWhere('game_id', 'like', '%'.$player.'%'));
                });
            })
            ->latest('id')
            ->paginate(50)
            ->withQueryString();

        $problems = MinecraftOperation::query()
            ->where(function (Builder $q) {
                $q->whereIn('status', [
                    MinecraftOperationStatus::Failed,
                    MinecraftOperationStatus::Uncertain,
                ])->orWhere('result_code', 'failed:no_uuid');
            })
            ->count();

        return view('shop::admin.deliveries.index', [
            'operations' => $operations,
            'status' => $status,
            'type' => $type,
            'serverId' => $serverId,
            'player' => $player,
            'purchaseId' => $purchase,
            'servers' => Server::query()->orderBy('name')->get(['id', 'name']),
            'types' => MinecraftOperationType::cases(),
            'problems' => $problems,
            'enabled' => ConfiguredDeliveryService::enabled(),
        ]);
    }

    public function retry(MinecraftOperation $operation): RedirectResponse
    {
        $operation->forceFill([
            'status' => MinecraftOperationStatus::Pending,
            'available_at' => now(),
            'completed_at' => null,
            'result_code' => null,
        ])->save();

        foreach (['claim_token', 'lease_expires_at', 'leased_until'] as $column) {
            if ($operation->isFillable($column) || array_key_exists($column, $operation->getAttributes())) {
                $operation->forceFill([$column => null])->save();
            }
        }

        return redirect()->back()->with('success', 'Операция #'.$operation->id.' поставлена в очередь повторно.');
    }

    public function markDelivered(MinecraftOperation $operation): RedirectResponse
    {
        $operation->forceFill([
            'status' => MinecraftOperationStatus::Succeeded,
            'result_code' => 'manual',
            'last_error' => null,
            'completed_at' => now(),
        ])->save();

        if ($operation->purchase_id && ConfiguredDeliveryService::enabled()) {
            try {
                $purchase = $operation->purchase;
                if ($purchase !== null) {
                    app(ConfiguredDeliveryService::class)->refresh($purchase);
                }
            } catch (\Throwable $e) {
                // keep the manual mark even if purchase refresh fails
            }
        }

        return redirect()->back()->with('success', 'Операция #'.$operation->id.' отмечена как доставленная.');
    }
}
