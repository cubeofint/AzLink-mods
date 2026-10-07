<?php

namespace Azuriom\Http\Controllers\Api;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Services\Currency\ServerOperationQueue;
use Illuminate\Http\Request;

/**
 * AzLink pull/ack endpoints for the gluon transfer queue. Disabled unless
 * setting currency.server_queue_enabled = 1.
 */
class ServerOperationController extends Controller
{
    public function __construct(private ServerOperationQueue $queue)
    {
    }

    public function index(Request $request)
    {
        abort_unless(ServerOperationQueue::enabled(), 404);

        $serverId = (int) $request->input('server-id');

        return response()->json([
            'operations' => $this->queue->pending($serverId, (int) $request->query('limit', 50))->values(),
        ]);
    }

    public function ack(Request $request, string $operation)
    {
        abort_unless(ServerOperationQueue::enabled(), 404);

        $data = $this->validate($request, [
            'status' => ['required', 'in:applied,failed'],
            'error' => ['nullable', 'string', 'max:500'],
        ]);

        $op = $this->queue->ack((int) $request->input('server-id'), $operation, $data['status'], $data['error'] ?? null);

        if ($op === null) {
            return response()->json(['message' => 'not_found'], 404);
        }

        return response()->json(['id' => $op->id, 'status' => $op->status]);
    }
}
