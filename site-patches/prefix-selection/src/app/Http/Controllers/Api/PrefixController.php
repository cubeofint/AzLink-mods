<?php

namespace Azuriom\Http\Controllers\Api;

use Azuriom\Http\Controllers\Controller;
use Azuriom\Services\Prefix\PrefixOperationQueue;
use Azuriom\Services\Prefix\PrefixReporter;
use Azuriom\Services\Prefix\PrefixSelection;
use Illuminate\Http\Request;

/**
 * AzLink prefix sync. Disabled unless setting prefixes.enabled = 1.
 *
 * POST /api/azlink/prefixes/report
 * GET  /api/azlink/prefixes/operations
 * POST /api/azlink/prefixes/ack
 */
class PrefixController extends Controller
{
    public function report(Request $request, PrefixReporter $reporter)
    {
        abort_unless(PrefixSelection::enabled(), 404);

        $data = $this->validate($request, [
            'players' => ['required', 'array', 'max:200'],
            'players.*.uuid' => ['required', 'string', 'max:36'],
            'players.*.name' => ['nullable', 'string', 'max:64'],
            'players.*.available' => ['nullable', 'array', 'max:64'],
            'players.*.available.*.group' => ['required_with:players.*.available', 'string', 'max:64'],
            'players.*.available.*.prefix_raw' => ['nullable', 'string', 'max:255'],
            'players.*.available.*.category' => ['nullable', 'string', 'max:16'],
            'players.*.selected' => ['nullable', 'string', 'max:64'],
            'players.*.locked_staff' => ['nullable', 'boolean'],
        ]);

        $result = $reporter->report((int) $request->input('server-id'), $data['players']);

        return response()->json($result);
    }

    public function operations(Request $request, PrefixOperationQueue $queue)
    {
        abort_unless(PrefixSelection::enabled(), 404);

        return response()->json([
            'operations' => $queue->pending((int) $request->input('server-id'), (int) $request->query('limit', 50))->values(),
        ]);
    }

    public function ack(Request $request, PrefixOperationQueue $queue)
    {
        abort_unless(PrefixSelection::enabled(), 404);

        $data = $this->validate($request, [
            'op_id' => ['required', 'string', 'max:40'],
            'status' => ['required', 'in:applied,rejected'],
            'reason' => ['nullable', 'string', 'max:500'],
            'selected' => ['nullable', 'string', 'max:64'],
        ]);

        $op = $queue->ack(
            (int) $request->input('server-id'),
            $data['op_id'],
            $data['status'],
            $data['reason'] ?? null,
            $data['selected'] ?? null
        );

        if ($op === null) {
            return response()->json(['message' => 'not_found'], 404);
        }

        return response()->json([
            'op_id' => $op->id,
            'status' => $op->status,
            'selected' => $op->selected,
        ]);
    }
}
