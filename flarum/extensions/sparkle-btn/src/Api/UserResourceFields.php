<?php

namespace Sparkle\Flarum\Api;

use Flarum\Api\Context;
use Flarum\Api\Schema;
use Flarum\User\User;

class UserResourceFields
{
    public const VIEW_PERMISSION = 'sparkleBtn.viewUserData';
    public const VIEW_HIDDEN_PERMISSION = 'sparkleBtn.viewHiddenUserData';

    /**
     * User preference that decides whether other people may see this user's
     * cached Sparkle BTN card. The owner always sees their own card.
     */
    public const SHOW_CARD_PREFERENCE = 'sparkleBtnShowCard';

    public function __invoke(): array
    {
        return [
            Schema\Arr::make('sparkleBtn')
                ->nullable()
                ->visible(fn (User $user, Context $context) => $this->canView($user, $context))
                ->get(fn (User $user) => $this->serialize($user)),
        ];
    }

    private function canView(User $user, Context $context): bool
    {
        $actor = $context->getActor();

        if ($actor->id === $user->id) {
            return true;
        }

        // This is deliberately stronger than VIEW_PERMISSION: it also ignores
        // the owner's "show my card" preference. Sparkle's own privacy rules
        // still apply to the cached payload.
        if ($actor->hasPermission(self::VIEW_HIDDEN_PERMISSION)) {
            return true;
        }

        if (! $actor->hasPermission(self::VIEW_PERMISSION)) {
            return false;
        }

        return (bool) $user->getPreference(self::SHOW_CARD_PREFERENCE, true);
    }

    private function serialize(User $user): ?array
    {
        if (!$user->sparkle_btn_user_id) {
            return null;
        }

        $data = is_array($user->sparkle_btn_data) ? $user->sparkle_btn_data : [];

        return array_merge($data, [
            'githubUid' => $user->sparkle_github_uid,
            'btnUserId' => (string) $user->sparkle_btn_user_id,
            'syncedAt' => $user->sparkle_btn_synced_at?->toIso8601String(),
        ]);
    }
}
