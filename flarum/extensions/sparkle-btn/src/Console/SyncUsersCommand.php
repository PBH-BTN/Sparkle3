<?php

namespace Sparkle\Flarum\Console;

use Flarum\Settings\SettingsRepositoryInterface;
use Flarum\User\LoginProvider;
use Flarum\User\User;
use Illuminate\Console\Command;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Support\Carbon;
use Sparkle\Flarum\Service\BtnSyncService;

class SyncUsersCommand extends Command
{
    protected $signature = 'sparkle-btn:sync
        {--limit= : Maximum number of users to process}
        {--force : Ignore the configured refresh interval}
        {--github-uid= : Synchronize only the account with this GitHub UID}';

    protected $description = 'Synchronize Flarum users linked to Sparkle BTN';

    public function handle(
        BtnSyncService $sync,
        SettingsRepositoryInterface $settings
    ): int {
        $githubUid = trim((string) $this->option('github-uid'));

        if ($githubUid !== '') {
            return $this->syncSingleUser($sync, $githubUid);
        }

        $limit = $this->limit($settings);
        $force = (bool) $this->option('force');
        $intervalMinutes = $this->intervalMinutes($settings);
        $cutoff = Carbon::now()->subMinutes($intervalMinutes);

        $query = User::query()
            ->whereHas('loginProviders', function (Builder $query) {
                $query->where('provider', 'github');
            })
            ->with([
                'loginProviders' => function ($query) {
                    $query
                        ->where('provider', 'github')
                        ->orderByDesc('last_login_at');
                },
            ]);

        if (!$force) {
            $query->where(function (Builder $query) use ($cutoff) {
                $query
                    ->whereNull('sparkle_btn_attempted_at')
                    ->orWhere('sparkle_btn_attempted_at', '<=', $cutoff);
            });
        }

        $users = $query
            ->orderBy('sparkle_btn_attempted_at')
            ->orderBy('id')
            ->limit($limit)
            ->get();

        if ($users->isEmpty()) {
            $this->info('No Sparkle BTN users are due for synchronization.');

            return self::SUCCESS;
        }

        $succeeded = 0;
        $failed = 0;

        foreach ($users as $user) {
            $provider = $user->loginProviders->first();
            if ($provider === null) {
                continue;
            }

            if ($sync->sync($user, $provider->identifier)) {
                $succeeded++;
            } else {
                $failed++;
            }
        }

        $this->info(sprintf(
            'Sparkle BTN synchronization finished: %d succeeded, %d failed.',
            $succeeded,
            $failed
        ));

        return $failed > 0 ? self::FAILURE : self::SUCCESS;
    }

    private function syncSingleUser(BtnSyncService $sync, string $githubUid): int
    {
        $provider = LoginProvider::query()
            ->where('provider', 'github')
            ->where('identifier', $githubUid)
            ->first();

        if ($provider === null || $provider->user === null) {
            $this->error('No Flarum account is linked to GitHub UID '.$githubUid.'.');

            return self::FAILURE;
        }

        return $sync->sync($provider->user, $githubUid)
            ? self::SUCCESS
            : self::FAILURE;
    }

    private function limit(SettingsRepositoryInterface $settings): int
    {
        $configured = filter_var(
            $settings->get('sparkle-btn.batch_size'),
            FILTER_VALIDATE_INT,
            ['options' => ['default' => 25]]
        );
        $option = filter_var(
            $this->option('limit'),
            FILTER_VALIDATE_INT,
            ['options' => ['default' => 0]]
        );
        $limit = (int) $option > 0 ? (int) $option : (int) $configured;

        return max(1, min(500, $limit));
    }

    private function intervalMinutes(SettingsRepositoryInterface $settings): int
    {
        $value = filter_var(
            $settings->get('sparkle-btn.sync_interval_minutes'),
            FILTER_VALIDATE_INT,
            ['options' => ['default' => 60]]
        );

        return max(5, min(10_080, (int) $value));
    }
}
