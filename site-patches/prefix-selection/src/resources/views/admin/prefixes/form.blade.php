@extends('admin.layouts.admin')

@section('title', $item->exists ? trans('prefixes.admin.catalog.edit') : trans('prefixes.admin.catalog.create'))

@section('content')
    <ul class="nav nav-tabs mb-3">
        <li class="nav-item">
            <a class="nav-link active" href="{{ route('admin.prefixes.index') }}">{{ trans('prefixes.admin.catalog.title') }}</a>
        </li>
        <li class="nav-item">
            <a class="nav-link" href="{{ route('admin.prefixes.operations') }}">{{ trans('prefixes.admin.ops.title') }}</a>
        </li>
    </ul>

    <div class="card shadow mb-4">
        <div class="card-body">
            <form method="POST"
                  action="{{ $item->exists ? route('admin.prefixes.update', $item) : route('admin.prefixes.store') }}">
                @csrf
                @if($item->exists)
                    @method('PUT')
                @endif

                <div class="mb-3">
                    <label class="form-label" for="groupInput">{{ trans('prefixes.admin.catalog.group') }}</label>
                    <input type="text" class="form-control @error('group') is-invalid @enderror" id="groupInput"
                           name="group" value="{{ old('group', $item->group) }}" required maxlength="64">
                    @error('group')
                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="mb-3">
                    <label class="form-label" for="nameInput">{{ trans('prefixes.admin.catalog.display_name') }}</label>
                    <input type="text" class="form-control @error('display_name') is-invalid @enderror" id="nameInput"
                           name="display_name" value="{{ old('display_name', $item->display_name) }}" required maxlength="120">
                    @error('display_name')
                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="mb-3">
                    <label class="form-label" for="rawInput">{{ trans('prefixes.admin.catalog.prefix_raw') }}</label>
                    <input type="text" class="form-control @error('prefix_raw') is-invalid @enderror" id="rawInput"
                           name="prefix_raw" value="{{ old('prefix_raw', $item->prefix_raw) }}" maxlength="255">
                    @error('prefix_raw')
                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                    <div class="form-text mt-2">
                        {{ trans('prefixes.admin.catalog.preview') }}:
                        <span id="prefixPreview">{!! $formatter->toHtml(old('prefix_raw', $item->prefix_raw)) !!}</span>
                    </div>
                </div>

                <div class="mb-3">
                    <label class="form-label" for="categoryInput">{{ trans('prefixes.admin.catalog.category') }}</label>
                    <select class="form-select @error('category') is-invalid @enderror" id="categoryInput" name="category" required>
                        @foreach(\Azuriom\Models\PrefixCatalog::CATEGORIES as $category)
                            <option value="{{ $category }}" @selected(old('category', $item->category) === $category)>
                                {{ \Azuriom\Models\PrefixCatalog::categoryLabel($category) }}
                            </option>
                        @endforeach
                    </select>
                    @error('category')
                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="mb-3">
                    <label class="form-label" for="sortInput">{{ trans('prefixes.admin.catalog.sort') }}</label>
                    <input type="number" min="0" max="999999" class="form-control @error('sort') is-invalid @enderror"
                           id="sortInput" name="sort" value="{{ old('sort', $item->sort ?? 0) }}" required>
                    @error('sort')
                        <span class="invalid-feedback" role="alert"><strong>{{ $message }}</strong></span>
                    @enderror
                </div>

                <div class="form-check mb-3">
                    <input type="checkbox" class="form-check-input" id="enabledInput" name="enabled" value="1"
                           @checked(old('enabled', $item->enabled))>
                    <label class="form-check-label" for="enabledInput">{{ trans('prefixes.admin.catalog.enabled') }}</label>
                </div>

                <button type="submit" class="btn btn-primary">{{ trans('messages.actions.save') }}</button>
                <a href="{{ route('admin.prefixes.index') }}" class="btn btn-secondary">{{ trans('messages.actions.cancel') }}</a>
            </form>
        </div>
    </div>
@endsection
