<?php

namespace Azuriom\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;

/**
 * Admin-managed display metadata for a chat prefix group key.
 *
 * @property int $id
 * @property string $group
 * @property string $display_name
 * @property string|null $prefix_raw
 * @property string $category
 * @property int $sort
 * @property bool $enabled
 */
class PrefixCatalog extends Model
{
    public const CATEGORIES = ['quest', 'donate', 'staff', 'other'];

    protected $table = 'prefix_catalog';

    protected $fillable = [
        'group', 'display_name', 'prefix_raw', 'category', 'sort', 'enabled',
    ];

    protected $casts = [
        'sort' => 'integer',
        'enabled' => 'boolean',
    ];

    public function scopeEnabled(Builder $query): Builder
    {
        return $query->where('enabled', true);
    }

    public static function categoryLabel(string $category): string
    {
        $key = 'prefixes.categories.'.$category;
        $label = trans($key);

        return $label === $key ? $category : $label;
    }
}
