<?php

namespace Sparkle\Flarum\Api;

use Flarum\Api\Context;
use Flarum\Api\Schema;
use Flarum\User\User;

class UserResourceFields
{
    public const VIEW_PERMISSION = 'sparkleBtn.viewUserData';

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

        return $actor->id === $user->id
            || $actor->hasPermission(self::VIEW_PERMISSION);
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
