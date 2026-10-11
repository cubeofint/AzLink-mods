<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (! Schema::hasTable('prefix_catalog')) {
            Schema::create('prefix_catalog', function (Blueprint $table) {
                $table->id();
                $table->string('group', 64)->unique();
                $table->string('display_name', 120);
                $table->string('prefix_raw', 255)->nullable();
                $table->string('category', 16)->default('other'); // quest|donate|staff|other
                $table->unsignedInteger('sort')->default(0);
                $table->boolean('enabled')->default(true);
                $table->timestamps();
            });
        }

        if (! Schema::hasTable('user_server_prefixes')) {
            Schema::create('user_server_prefixes', function (Blueprint $table) {
                $table->id();
                $table->unsignedInteger('user_id');
                $table->unsignedInteger('server_id');
                $table->json('available')->nullable();
                $table->string('selected', 64)->nullable();
                $table->boolean('locked_staff')->default(false);
                $table->timestamp('synced_at')->nullable();
                $table->timestamps();

                $table->unique(['user_id', 'server_id']);
                $table->index(['server_id', 'updated_at']);
                $table->foreign('user_id')->references('id')->on('users')->cascadeOnDelete();
                $table->foreign('server_id')->references('id')->on('servers')->cascadeOnDelete();
            });
        }

        if (! Schema::hasTable('prefix_operations')) {
            Schema::create('prefix_operations', function (Blueprint $table) {
                $table->string('id', 40)->primary();
                $table->unsignedInteger('user_id');
                $table->unsignedInteger('server_id');
                $table->string('type', 32)->default('prefix_select');
                $table->string('group', 64)->nullable();
                $table->string('status', 16)->default('pending'); // pending|applied|rejected
                $table->unsignedInteger('deliveries')->default(0);
                $table->timestamp('leased_until')->nullable();
                $table->string('reason', 191)->nullable();
                $table->string('selected', 64)->nullable();
                $table->string('source', 32)->nullable();
                $table->timestamp('applied_at')->nullable();
                $table->timestamps();

                $table->index(['server_id', 'status', 'leased_until', 'created_at']);
                $table->index(['user_id', 'server_id', 'status']);
                $table->foreign('user_id')->references('id')->on('users')->cascadeOnDelete();
                $table->foreign('server_id')->references('id')->on('servers')->cascadeOnDelete();
            });
        }

        if (! DB::table('settings')->where('name', 'prefixes.enabled')->exists()) {
            DB::table('settings')->insert(['name' => 'prefixes.enabled', 'value' => '0']);
        }

        if (! DB::table('settings')->where('name', 'prefixes.lease_seconds')->exists()) {
            DB::table('settings')->insert(['name' => 'prefixes.lease_seconds', 'value' => '30']);
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('prefix_operations');
        Schema::dropIfExists('user_server_prefixes');
        Schema::dropIfExists('prefix_catalog');
        DB::table('settings')->whereIn('name', ['prefixes.enabled', 'prefixes.lease_seconds'])->delete();
    }
};
