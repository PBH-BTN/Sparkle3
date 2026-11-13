<?php

namespace Sparkle\Flarum\Listener;

use Flarum\User\LoginProvider;
use FoF\OAuth\Events\OAuthLoginSuccessful;
use Psr\Log\LoggerInterface;
use Sparkle\Flarum\Service\BtnSyncService;
use Throwable;

class OAuthLoginListener
{
    public function __construct(
        private readonly BtnSyncService $sync,
        private readonly LoggerInterface $log
    ) {
    }

    public function handle(OAuthLoginSuccessful $event): void
    {
        if (strtolower($event->providerName) !== 'github') {
            return;
        }

        $provider = LoginProvider::query()
            ->where('provider', 'github')
            ->where('identifier', $event->identifier)
            ->first();
        $user = $provider?->user;

        if ($user === null) {
            // A first-time OAuth callback starts the registration form. The
            // RegistrationListener handles that flow after the user is saved.
            if ($event->actor?->exists) {
                $this->log->warning('[sparkle-btn] GitHub login succeeded but the Flarum account was not found');
            }

            return;
        }

        try {
            $this->sync->sync($user, $event->identifier);
        } catch (Throwable $exception) {
            // Profile synchronization must never turn a valid OAuth login into a failure.
            $this->log->error('[sparkle-btn] Unexpected login sync failure: '.$exception->getMessage());
        }
    }
}
