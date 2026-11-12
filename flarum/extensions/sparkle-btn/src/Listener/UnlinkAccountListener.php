<?php

namespace Sparkle\Flarum\Listener;

use Flarum\User\LoginProvider;
use FoF\OAuth\Events\UnlinkingFromProvider;
use Psr\Log\LoggerInterface;
use Sparkle\Flarum\Service\BtnSyncService;
use Throwable;

class UnlinkAccountListener
{
    public function __construct(
        private readonly BtnSyncService $sync,
        private readonly LoggerInterface $log
    ) {
    }

    public function handle(UnlinkingFromProvider $event): void
    {
        if (strtolower($event->provider->provider) !== 'github') {
            return;
        }

        $hasAnotherGithubProvider = LoginProvider::query()
            ->where('user_id', $event->user->id)
            ->where('provider', 'github')
            ->where('id', '!=', $event->provider->id)
            ->exists();

        if ($hasAnotherGithubProvider) {
            return;
        }

        try {
            $this->sync->forget($event->user);
        } catch (Throwable $exception) {
            $this->log->error('[sparkle-btn] Unexpected account-unlink cleanup failure: '.$exception->getMessage());
        }
    }
}
