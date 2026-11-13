<?php

use Illuminate\Database\Schema\Builder;
use Illuminate\Support\Carbon;

return [
    'up' => function (Builder $schema) {
        $db = $schema->getConnection();
        $settingKey = 'sparkle-btn.bound_group_id';
        $nameSingular = 'Sparkle BTN User';
        $namePlural = 'Sparkle BTN Users';

        $db->transaction(function () use ($db, $settingKey, $nameSingular, $namePlural) {
            $groupId = (int) $db->table('settings')
                ->where('key', $settingKey)
                ->value('value');

            $group = $groupId > 4
                ? $db->table('groups')->where('id', $groupId)->first()
                : null;

            if ($group === null) {
                $group = $db->table('groups')
                    ->where('name_singular', $nameSingular)
                    ->first();
            }

            if ($group === null) {
                $groupId = $db->table('groups')->insertGetId([
                    'name_singular' => $nameSingular,
                    'name_plural' => $namePlural,
                    'color' => '#10B981',
                    'icon' => 'fas fa-satellite-dish',
                    'created_at' => Carbon::now(),
                    'updated_at' => Carbon::now(),
                ]);
            } else {
                $groupId = (int) $group->id;
            }

            $db->table('settings')->updateOrInsert(
                ['key' => $settingKey],
                ['value' => (string) $groupId]
            );

            $db->table('users')
                ->whereNotNull('sparkle_btn_user_id')
                ->select('id')
                ->orderBy('id')
                ->chunkById(500, function ($users) use ($db, $groupId) {
                    foreach ($users as $user) {
                        $db->table('group_user')->insertOrIgnore([
                            'user_id' => $user->id,
                            'group_id' => $groupId,
                            'created_at' => Carbon::now(),
                        ]);
                    }
                });
        });
    },

    'down' => function (Builder $schema) {
        $db = $schema->getConnection();
        $groupId = (int) $db->table('settings')
            ->where('key', 'sparkle-btn.bound_group_id')
            ->value('value');

        $db->table('settings')
            ->where('key', 'sparkle-btn.bound_group_id')
            ->delete();

        if ($groupId > 4) {
            $db->table('group_user')->where('group_id', $groupId)->delete();
            $db->table('groups')->where('id', $groupId)->delete();
        }
    },
];
