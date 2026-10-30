<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        foreach ([
            ['rankings', 'Player Rankings', 'Public native rankings, character lookup and legions.'],
            ['events', 'Event Schedule', 'Published events and the native siege timetable.'],
            ['start', 'Start Playing', 'First installation and returning-player checklists.'],
        ] as [$key, $name, $description]) {
            if (DB::table('module_plugins')->where('feature_key', $key)->orWhere('slug', $key)->exists()) continue;
            $row = ['name' => $name, 'slug' => $key, 'type' => 'module', 'feature_key' => $key,
                'version' => '1.0.0', 'description' => $description, 'is_enabled' => true,
                'sort_order' => 20, 'created_at' => now(), 'updated_at' => now()];
            if (Schema::hasColumn('module_plugins', 'module_key')) $row['module_key'] = $key;
            DB::table('module_plugins')->insert($row);
            cache()->forget('aion-feature-enabled:'.$key);
        }
    }

    public function down(): void
    {
        // Preserve owner configuration; modules can be disabled in the control panel.
    }
};
