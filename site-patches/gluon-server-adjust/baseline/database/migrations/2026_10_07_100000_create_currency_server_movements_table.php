<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Server-wallet movements reported by game servers (cointcore outbox).
 * Informational only: rows here never change any site balance.
 */
return new class extends Migration
{
    public function up(): void
    {
        if (! Schema::hasTable('currency_server_movements')) {
            Schema::create('currency_server_movements', function (Blueprint $table) {
                $table->id();
                $table->unsignedInteger('server_id');
                $table->unsignedBigInteger('movement_id');
                $table->string('type', 32);
                $table->unsignedBigInteger('amount');
                $table->string('from_uuid', 36)->nullable();
                $table->string('from_name', 64)->nullable();
                $table->unsignedInteger('from_user_id')->nullable();
                $table->string('to_uuid', 36)->nullable();
                $table->string('to_name', 64)->nullable();
                $table->unsignedInteger('to_user_id')->nullable();
                $table->string('note', 191)->nullable();
                $table->timestamp('occurred_at');
                $table->timestamp('created_at')->useCurrent();

                $table->unique(['server_id', 'movement_id'], 'currency_server_movements_server_movement_uq');
                $table->index(['from_user_id', 'occurred_at']);
                $table->index(['to_user_id', 'occurred_at']);
                $table->index(['type', 'occurred_at']);
                $table->foreign('server_id')->references('id')->on('servers')->cascadeOnDelete();
            });
        }

        if (! DB::table('settings')->where('name', 'currency.server_movements_enabled')->exists()) {
            DB::table('settings')->insert(['name' => 'currency.server_movements_enabled', 'value' => '0']);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('currency_server_movements');
        DB::table('settings')->where('name', 'currency.server_movements_enabled')->delete();
    }
};
