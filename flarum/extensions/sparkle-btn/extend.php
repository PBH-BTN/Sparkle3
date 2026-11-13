<?php

use Flarum\Api\Resource\UserResource;
use Flarum\Extend;
use Flarum\User\Event\RegisteringFromProvider;
use Flarum\User\User;
use FoF\OAuth\Events\LinkingToProvider;
use FoF\OAuth\Events\OAuthLoginSuccessful;
use FoF\OAuth\Events\UnlinkingFromProvider;
use Sparkle\Flarum\Api\UserResourceFields;
use Sparkle\Flarum\Console\FifteenMinuteSchedule;
use Sparkle\Flarum\Console\SyncUsersCommand;
use Sparkle\Flarum\Listener\LinkAccountListener;
use Sparkle\Flarum\Listener\OAuthLoginListener;
use Sparkle\Flarum\Listener\RegistrationListener;
use Sparkle\Flarum\Listener\UnlinkAccountListener;

return [
    (new Extend\Frontend('forum'))
        ->js(__DIR__.'/js/dist/forum.js')
        ->css(__DIR__.'/less/forum.less'),

    (new Extend\Frontend('admin'))
        ->js(__DIR__.'/js/dist/admin.js'),

    new Extend\Locales(__DIR__.'/locale'),

    (new Extend\Settings())
        ->default('sparkle-btn.endpoint', '')
        ->default('sparkle-btn.token', '')
        ->default('sparkle-btn.sync_interval_minutes', 60)
        ->default('sparkle-btn.batch_size', 25)
        ->default('sparkle-btn.request_timeout', 10),

    (new Extend\User())
        ->registerPreference('sparkleBtnShowCard', null, true),

    (new Extend\Model(User::class))
        ->cast('sparkle_github_uid', 'string')
        ->cast('sparkle_btn_user_id', 'string')
        ->cast('sparkle_btn_data', 'array')
        ->cast('sparkle_btn_synced_at', 'datetime')
        ->cast('sparkle_btn_attempted_at', 'datetime')
        ->cast('sparkle_btn_error', 'string'),

    (new Extend\ApiResource(UserResource::class))
        ->fields(UserResourceFields::class),

    (new Extend\Event())
        ->listen(RegisteringFromProvider::class, RegistrationListener::class)
        ->listen(OAuthLoginSuccessful::class, OAuthLoginListener::class)
        ->listen(LinkingToProvider::class, LinkAccountListener::class)
        ->listen(UnlinkingFromProvider::class, UnlinkAccountListener::class),

    (new Extend\Console())
        ->command(SyncUsersCommand::class)
        ->schedule(SyncUsersCommand::class, FifteenMinuteSchedule::class),
];
