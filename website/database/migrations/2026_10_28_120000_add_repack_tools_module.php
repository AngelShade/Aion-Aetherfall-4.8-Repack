<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (DB::table('module_plugins')->where('feature_key', 'repack.tools')->orWhere('slug', 'repack-tools')->exists()) return;
        $row = ['name' => 'Repack Tools & Codex', 'slug' => 'repack-tools', 'feature_key' => 'repack.tools',
            'type' => 'module', 'version' => '1.0.0', 'description' => 'Class explorer, party planner, instance atlas and companion command reference.',
            'is_enabled' => true, 'sort_order' => 21, 'created_at' => now(), 'updated_at' => now()];
        if (Schema::hasColumn('module_plugins', 'module_key')) $row['module_key'] = 'repack-tools';
        DB::table('module_plugins')->insert($row);
        cache()->forget('aion-feature-enabled:repack.tools');
    }

    public function down(): void
    {
        // Preserve the owner's module choice; it can be disabled through the CMS.
    }
};
