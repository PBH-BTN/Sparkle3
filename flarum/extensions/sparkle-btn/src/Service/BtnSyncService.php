<?php

namespace Sparkle\Flarum\Service;

use Flarum\User\User;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Carbon;
use JsonException;
use Psr\Log\LoggerInterface;
use Throwable;

class BtnSyncService
{
    public function __construct(
        private readonly BtnApiClient $client,
        private readonly BoundGroupService $groups,
        private readonly LoggerInterface $log
    ) {
    }

    public function sync(User $user, string $githubUid): bool
    {
        $githubUid = trim($githubUid);

        if (!preg_match('/^[0-9]{1,32}$/', $githubUid)) {
            $this->recordFailure($user->id, $githubUid, new \RuntimeException(
                'FoF OAuth returned an invalid GitHub UID'
            ));

            return false;
        }

        try {
            $payload = $this->client->fetch($githubUid);
            $this->applySuccess($user->id, $githubUid, $payload);

            return true;
        } catch (Throwable $exception) {
            $this->recordFailure($user->id, $githubUid, $exception);
            $this->log->warning('[sparkle-btn] Could not sync user '.$user->id.': '.$exception->getMessage());

            return false;
        }
    }

    public function forget(User $user): void
    {
        DB::transaction(function () use ($user) {
            DB::table('users')->where('id', $user->id)->update([
                'sparkle_github_uid' => null,
                'sparkle_btn_user_id' => null,
                'sparkle_btn_data' => null,
                'sparkle_btn_synced_at' => null,
                'sparkle_btn_attempted_at' => null,
                'sparkle_btn_error' => null,
            ]);

            $this->groups->remove($user);
        });
    }

    /**
     * @param array<string, mixed> $payload
     */
    private function applySuccess(int $userId, string $githubUid, array $payload): void
    {
        $bound = ($payload['bound'] ?? false) === true;
        $now = Carbon::now();
        $data = $bound
            ? [
                'basicInfo' => $payload['basicInfo'] ?? null,
                'traffic7d' => $payload['traffic7d'] ?? null,
                'points' => $payload['points'] ?? null,
                'rankings' => $payload['rankings'] ?? null,
            ]
            : null;

        DB::transaction(function () use (
            $userId,
            $githubUid,
            $payload,
            $bound,
            $data,
            $now
        ) {
            DB::table('users')->where('id', $userId)->update([
                'sparkle_github_uid' => $githubUid,
                'sparkle_btn_user_id' => $bound ? $payload['btnUserId'] : null,
                'sparkle_btn_data' => $this->encode($data),
                'sparkle_btn_synced_at' => $now,
                'sparkle_btn_attempted_at' => $now,
                'sparkle_btn_error' => null,
            ]);

            $user = User::query()->findOrFail($userId);
            if ($bound) {
                $this->groups->add($user);
            } else {
                $this->groups->remove($user);
            }
        });
    }

    private function recordFailure(int $userId, string $githubUid, Throwable $exception): void
    {
        $message = mb_substr($exception->getMessage(), 0, 2000);
        $now = Carbon::now();

        if ($githubUid !== '' && preg_match('/^[0-9]{1,32}$/', $githubUid)) {
            DB::table('users')->where('id', $userId)->update([
                'sparkle_github_uid' => $githubUid,
                'sparkle_btn_attempted_at' => $now,
                'sparkle_btn_error' => $message,
            ]);
        } else {
            DB::table('users')->where('id', $userId)->update([
                'sparkle_btn_attempted_at' => $now,
                'sparkle_btn_error' => $message,
            ]);
        }
    }

    /**
     * @param array<string, mixed>|null $data
     */
    private function encode(?array $data): ?string
    {
        if ($data === null) {
            return null;
        }

        try {
            return json_encode(
                $data,
                JSON_UNESCAPED_UNICODE
                    | JSON_UNESCAPED_SLASHES
                    | JSON_THROW_ON_ERROR
            );
        } catch (JsonException $exception) {
            throw new \RuntimeException('Could not cache Sparkle BTN data', 0, $exception);
        }
    }
}
