<div class="card mb-3" id="bridge-delivery-editor">
    <div class="card-header">Доставка через мост (AzLink)</div>
    <div class="card-body">
        <p class="small text-muted mb-3">
            Группы с <code>mode=bridge</code> создают операции <code>group_grant</code> / <code>kit_credit</code>
            вместо консольных команд. Киты не имеют срока. Привилегии истекают: max(текущий конец, сейчас) + N календарных месяцев.
            Сервер должен быть в <code>shop.configured_delivery.servers</code>.
        </p>
        <div id="bridgeGroups"></div>
        <button type="button" class="btn btn-outline-primary btn-sm" id="bridgeAddGroup">Добавить bridge-группу</button>
    </div>
</div>

@php
    $bridgeServers = [];
    foreach ($servers ?? [] as $id => $name) {
        if (is_object($name)) {
            $bridgeServers[$name->id ?? $id] = $name->name ?? (string) $name;
        } else {
            $bridgeServers[$id] = $name;
        }
    }
@endphp
@push('footer-scripts')
<script>
(function () {
    const servers = @json($bridgeServers);
    const existing = @json(old('commands', $package->commands ?? $commands ?? []));
    const triggers = ['purchase', 'renewal', 'expiration', 'refund', 'chargeback'];
    const root = document.getElementById('bridgeGroups');
    let next = 1000;

    function serverOptions(selected) {
        return Object.entries(servers).map(([id, name]) =>
            `<option value="${id}" ${String(id) === String(selected) ? 'selected' : ''}>${name}</option>`
        ).join('');
    }

    function actionRow(prefix, action) {
        const op = action.op || 'grant_group';
        return `<div class="border rounded p-2 mb-2 bridge-action">
            <div class="row g-2">
                <div class="col-md-3">
                    <label class="form-label">op</label>
                    <select name="${prefix}[op]" class="form-select form-select-sm bridge-op">
                        <option value="grant_group" ${op === 'grant_group' ? 'selected' : ''}>grant_group</option>
                        <option value="add_kit_credits" ${op === 'add_kit_credits' ? 'selected' : ''}>add_kit_credits</option>
                    </select>
                </div>
                <div class="col-md-3 bridge-group-fields">
                    <label class="form-label">group</label>
                    <input class="form-control form-control-sm" name="${prefix}[group]" value="${action.group || '{rank_name}'}">
                </div>
                <div class="col-md-3 bridge-group-fields">
                    <label class="form-label">duration</label>
                    <input class="form-control form-control-sm" name="${prefix}[duration]" value="${action.duration || 'package'}">
                </div>
                <div class="col-md-3 bridge-kit-fields">
                    <label class="form-label">kit</label>
                    <input class="form-control form-control-sm" name="${prefix}[kit]" value="${action.kit || ''}">
                </div>
                <div class="col-md-3 bridge-kit-fields">
                    <label class="form-label">qty</label>
                    <input class="form-control form-control-sm" name="${prefix}[qty]" value="${action.qty || '{quantity}'}">
                </div>
            </div>
        </div>`;
    }

    function toggleFields(card) {
        card.querySelectorAll('.bridge-action').forEach((row) => {
            const op = row.querySelector('.bridge-op')?.value;
            row.querySelectorAll('.bridge-group-fields').forEach((el) => el.style.display = op === 'grant_group' ? '' : 'none');
            row.querySelectorAll('.bridge-kit-fields').forEach((el) => el.style.display = op === 'add_kit_credits' ? '' : 'none');
        });
    }

    function addGroup(data) {
        const i = next++;
        const card = document.createElement('div');
        card.className = 'border rounded p-3 mb-3';
        card.innerHTML = `
            <input type="hidden" name="commands[${i}][mode]" value="bridge">
            <input type="hidden" name="commands[${i}][require_online]" value="0">
            <div class="row g-2 mb-2">
                <div class="col-md-3">
                    <label class="form-label">trigger</label>
                    <select name="commands[${i}][trigger]" class="form-select form-select-sm">
                        ${triggers.map((t) => `<option value="${t}" ${data.trigger === t ? 'selected' : ''}>${t}</option>`).join('')}
                    </select>
                </div>
                <div class="col-md-4">
                    <label class="form-label">server</label>
                    <select name="commands[${i}][server]" class="form-select form-select-sm">${serverOptions(data.server)}</select>
                </div>
            </div>
            <div class="bridge-actions">${(data.actions || [{op: 'grant_group'}]).map((action, j) => actionRow(`commands[${i}][actions][${j}]`, action)).join('')}</div>
            <button type="button" class="btn btn-sm btn-outline-danger bridge-remove">Удалить группу</button>
        `;
        root.appendChild(card);
        toggleFields(card);
        card.querySelectorAll('.bridge-op').forEach((el) => el.addEventListener('change', () => toggleFields(card)));
        card.querySelector('.bridge-remove').addEventListener('click', () => card.remove());
    }

    (Array.isArray(existing) ? existing : []).filter((g) => g && g.mode === 'bridge').forEach(addGroup);
    document.getElementById('bridgeAddGroup')?.addEventListener('click', () => addGroup({trigger: 'purchase', actions: [{op: 'grant_group'}]}));
})();
</script>
@endpush
