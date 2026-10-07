<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (! Schema::hasTable('currency_server_operations')) {
            Schema::create('currency_server_operations', function (Blueprint $table) {
                $table->string('id', 40)->primary();
                $table->unsignedInteger('user_id');
                $table->unsignedInteger('server_id');
                $table->string('direction', 16); // to_server | from_server
                $table->decimal('amount', 14, 2);
                $table->string('status', 16)->default('pending'); // pending | applied | failed
                $table->unsignedInteger('deliveries')->default(0);
                $table->string('error', 191)->nullable();
                $table->json('meta')->nullable();
                $table->timestamp('applied_at')->nullable();
                $table->timestamps();

                $table->index(['server_id', 'status', 'created_at']);
                $table->index(['user_id', 'created_at']);
                $table->foreign('user_id')->references('id')->on('users')->cascadeOnDelete();
                $table->foreign('server_id')->references('id')->on('servers')->cascadeOnDelete();
            });
        }

        // Feature flag: off until tested and confirmed.
        if (! DB::table('settings')->where('name', 'currency.server_queue_enabled')->exists()) {
            DB::table('settings')->insert(['name' => 'currency.server_queue_enabled', 'value' => '0']);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('currency_server_operations');
        DB::table('settings')->where('name', 'currency.server_queue_enabled')->delete();
    }
};
