<?php

namespace Sparkle\Flarum\Listener;

use FoF\OAuth\Events\LinkingToProvider;
use Psr\Log\LoggerInterface;
use Sparkle\Flarum\Service\BtnSyncService;
use Throwable;

class LinkAccountListener
{
    public function __construct(
        private readonly BtnSyncService $sync,
        private readonly LoggerInterface $log
    ) {
    }

    public function handle(LinkingToProvider $event): void
    {
        if (strtolower($event->providerName) !== 'github') {
            return;
        }

        try {
            $this->sync->sync($event->actor, $event->identifier);
        } catch (Throwable $exception) {
            $this->log->error('[sparkle-btn] Unexpected account-link sync failure: '.$exception->getMessage());
        }
    }
}
