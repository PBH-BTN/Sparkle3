<?php

namespace Sparkle\Flarum\Listener;

use Flarum\User\Event\RegisteringFromProvider;
use Flarum\User\User;
use Psr\Log\LoggerInterface;
use Sparkle\Flarum\Service\BtnSyncService;
use Throwable;

class RegistrationListener
{
    public function __construct(
        private readonly BtnSyncService $sync,
        private readonly LoggerInterface $log
    ) {
    }

    public function handle(RegisteringFromProvider $event): void
    {
        if (strtolower($event->provider) !== 'github') {
            return;
        }

        $githubUid = trim((string) ($event->payload['id'] ?? ''));
        if (!preg_match('/^[0-9]{1,32}$/', $githubUid)) {
            $this->log->warning('[sparkle-btn] GitHub registration payload did not contain a numeric user ID');

            return;
        }

        // RegisteringFromProvider fires before the new user has a database ID.
        // Run the sync after the account and its GitHub relation are persisted.
        $event->user->afterSave(function (User $user) use ($githubUid) {
            try {
                $this->sync->sync($user, $githubUid);
            } catch (Throwable $exception) {
                $this->log->error('[sparkle-btn] Unexpected registration sync failure: '.$exception->getMessage());
            }
        });
    }
}
