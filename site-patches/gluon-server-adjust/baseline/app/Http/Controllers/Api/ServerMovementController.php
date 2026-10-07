<?php

namespace Azuriom\Http\Controllers\Api;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Models\CurrencyServerMovement;
use Azuriom\Services\Currency\ServerMovementRecorder;
use Illuminate\Http\Request;

/**
 * POST /api/azlink/coins/movements — batch of server-wallet movements (cointcore outbox).
 * Disabled unless setting currency.server_movements_enabled = 1.
 */
class ServerMovementController extends Controller
{
    public function store(Request $request, ServerMovementRecorder $recorder)
    {
        abort_unless(CurrencyServerMovement::enabled(), 404);

        $data = $this->validate($request, [
            'movements' => ['required', 'array', 'max:200'],
            'movements.*.id' => ['required', 'integer', 'min:1'],
            'movements.*.timestamp' => ['required', 'integer', 'min:0'],
            'movements.*.type' => ['required', 'string', 'max:32'],
            'movements.*.amount' => ['required', 'integer', 'min:0'],
            'movements.*.from_id' => ['nullable', 'string', 'max:36'],
            'movements.*.from_name' => ['nullable', 'string', 'max:64'],
            'movements.*.to_id' => ['nullable', 'string', 'max:36'],
            'movements.*.to_name' => ['nullable', 'string', 'max:64'],
            'movements.*.note' => ['nullable', 'string', 'max:500'],
        ]);

        return response()->json($recorder->record((int) $request->input('server-id'), $data['movements']));
    }
}
