<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (Schema::hasTable('prefix_catalog') && ! Schema::hasColumn('prefix_catalog', 'server_id')) {
            Schema::table('prefix_catalog', function (Blueprint $table) {
                $table->dropUnique(['group']);
            });

            Schema::table('prefix_catalog', function (Blueprint $table) {
                $table->unsignedInteger('server_id')->nullable()->after('id');
                $table->string('auto_display_name', 120)->nullable()->after('display_name');
                $table->string('auto_prefix_raw', 255)->nullable()->after('prefix_raw');
                $table->unsignedInteger('auto_sort')->default(0)->after('sort');
                $table->integer('priority')->default(0)->after('auto_sort');
                $table->timestamp('last_seen_at')->nullable()->after('enabled');
                $table->timestamp('missing_since')->nullable()->after('last_seen_at');

                $table->unique(['server_id', 'group']);
                $table->index(['server_id', 'enabled', 'category']);
                $table->foreign('server_id')->references('id')->on('servers')->nullOnDelete();
            });

            DB::table('prefix_catalog')->update([
                'server_id' => null,
                'auto_display_name' => DB::raw('display_name'),
                'auto_prefix_raw' => DB::raw('prefix_raw'),
                'auto_sort' => DB::raw('sort'),
            ]);
        }

        if (! Schema::hasTable('prefix_catalog_imports')) {
            Schema::create('prefix_catalog_imports', function (Blueprint $table) {
                $table->id();
                $table->unsignedInteger('server_id')->unique();
                $table->string('schema', 32)->nullable();
                $table->string('hash', 64);
                $table->boolean('full')->default(false);
                $table->unsignedInteger('group_count')->default(0);
                $table->timestamp('imported_at');
                $table->timestamps();

                $table->foreign('server_id')->references('id')->on('servers')->cascadeOnDelete();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('prefix_catalog_imports');

        if (Schema::hasTable('prefix_catalog') && Schema::hasColumn('prefix_catalog', 'server_id')) {
            Schema::table('prefix_catalog', function (Blueprint $table) {
                $table->dropForeign(['server_id']);
                $table->dropUnique(['server_id', 'group']);
                $table->dropIndex(['server_id', 'enabled', 'category']);
                $table->dropColumn([
                    'server_id',
                    'auto_display_name',
                    'auto_prefix_raw',
                    'auto_sort',
                    'priority',
                    'last_seen_at',
                    'missing_since',
                ]);
            });

            Schema::table('prefix_catalog', function (Blueprint $table) {
                $table->unique('group');
            });
        }
    }
};
