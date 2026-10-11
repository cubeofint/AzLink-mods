<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;

return new class extends Migration
{
    public function up(): void
    {
        if (! DB::table('settings')->where('name', 'shop.configured_delivery.servers')->exists()) {
            DB::table('settings')->insert(['name' => 'shop.configured_delivery.servers', 'value' => '']);
        }
    }

    public function down(): void
    {
        DB::table('settings')->where('name', 'shop.configured_delivery.servers')->delete();
    }
};
