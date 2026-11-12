<?php

namespace Sparkle\Flarum\Console;

use Illuminate\Console\Scheduling\Event;

class FifteenMinuteSchedule
{
    public function __invoke(Event $event): void
    {
        $event->everyFifteenMinutes()->withoutOverlapping();
    }
}
