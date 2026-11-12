<?php

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Database\Schema\Builder;

return [
    'up' => function (Builder $schema) {
        $schema->table('users', function (Blueprint $table) {
            $table->string('sparkle_github_uid', 32)->nullable();
            $table->string('sparkle_btn_user_id', 32)->nullable();
            $table->text('sparkle_btn_data')->nullable();
            $table->dateTime('sparkle_btn_synced_at')->nullable();
            $table->dateTime('sparkle_btn_attempted_at')->nullable();
            $table->text('sparkle_btn_error')->nullable();
        });

        $schema->table('users', function (Blueprint $table) {
            $table->index('sparkle_github_uid');
            $table->index('sparkle_btn_user_id');
            $table->index('sparkle_btn_attempted_at');
        });
    },

    'down' => function (Builder $schema) {
        $schema->table('users', function (Blueprint $table) {
            $table->dropIndex(['sparkle_github_uid']);
            $table->dropIndex(['sparkle_btn_user_id']);
            $table->dropIndex(['sparkle_btn_attempted_at']);
        });

        $schema->table('users', function (Blueprint $table) {
            $table->dropColumn([
                'sparkle_github_uid',
                'sparkle_btn_user_id',
                'sparkle_btn_data',
                'sparkle_btn_synced_at',
                'sparkle_btn_attempted_at',
                'sparkle_btn_error',
            ]);
        });
    },
];
