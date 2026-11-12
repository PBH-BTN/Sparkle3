<?php

namespace Sparkle\Flarum\Service;

use Flarum\Group\Group;
use Flarum\Settings\SettingsRepositoryInterface;
use Flarum\User\User;

class BoundGroupService
{
    private const SETTING_KEY = 'sparkle-btn.bound_group_id';
    private const NAME_SINGULAR = 'Sparkle BTN User';
    private const NAME_PLURAL = 'Sparkle BTN Users';

    public function __construct(
        private readonly SettingsRepositoryInterface $settings
    ) {
    }

    public function add(User $user): void
    {
        $group = $this->ensureGroup();

        if ($group !== null) {
            $user->groups()->syncWithoutDetaching([$group->id]);
        }
    }

    public function remove(User $user): void
    {
        $group = $this->ensureGroup(false);

        if ($group !== null) {
            $user->groups()->detach($group->id);
        }
    }

    private function ensureGroup(bool $create = true): ?Group
    {
        $groupId = (int) $this->settings->get(self::SETTING_KEY);
        $group = $groupId > 4 ? Group::query()->find($groupId) : null;

        if ($group !== null) {
            return $group;
        }

        $group = Group::query()
            ->where('name_singular', self::NAME_SINGULAR)
            ->first();

        if ($group === null && !$create) {
            return null;
        }

        if ($group === null) {
            $group = new Group();
            $group->name_singular = self::NAME_SINGULAR;
            $group->name_plural = self::NAME_PLURAL;
            $group->color = '#10B981';
            $group->icon = 'fas fa-satellite-dish';
            $group->is_hidden = false;
            $group->save();
        }

        $this->settings->set(self::SETTING_KEY, (string) $group->id);

        return $group;
    }
}
