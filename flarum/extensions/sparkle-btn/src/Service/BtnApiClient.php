<?php

namespace Sparkle\Flarum\Service;

use Flarum\Settings\SettingsRepositoryInterface;
use GuzzleHttp\Client;
use JsonException;
use RuntimeException;

class BtnApiClient
{
    private const MAX_RESPONSE_BYTES = 2_000_000;

    public function __construct(
        private readonly SettingsRepositoryInterface $settings
    ) {
    }

    /**
     * @return array<string, mixed>
     */
    public function fetch(string $githubUid): array
    {
        $endpoint = trim((string) $this->settings->get('sparkle-btn.endpoint'));
        $token = trim((string) $this->settings->get('sparkle-btn.token'));
        $timeout = $this->boundedInteger('sparkle-btn.request_timeout', 10, 1, 60);

        if ($endpoint === '') {
            throw new RuntimeException('Sparkle BTN API endpoint is not configured');
        }
        if ($token === '') {
            throw new RuntimeException('Sparkle BTN API token is not configured');
        }

        $parts = parse_url($endpoint);
        if (!is_array($parts)
            || !in_array(strtolower((string) ($parts['scheme'] ?? '')), ['http', 'https'], true)
            || empty($parts['host'])) {
            throw new RuntimeException('Sparkle BTN API endpoint must be an absolute HTTP(S) URL');
        }

        $requestEndpoint = str_contains($endpoint, '{githubUid}')
            ? str_replace('{githubUid}', rawurlencode($githubUid), $endpoint)
            : $endpoint;
        $query = str_contains($endpoint, '{githubUid}')
            ? []
            : ['githubUid' => $githubUid];

        $client = new Client([
            'timeout' => $timeout,
            'connect_timeout' => min(5, $timeout),
            'http_errors' => false,
            'allow_redirects' => false,
            'headers' => [
                'Accept' => 'application/json',
                'X-Sparkle-Token' => $token,
                'User-Agent' => 'SparkleBTN-Flarum/1.0',
            ],
        ]);

        $response = $client->request('GET', $requestEndpoint, [
            'query' => $query,
        ]);

        $status = $response->getStatusCode();
        $body = (string) $response->getBody();

        if ($status < 200 || $status >= 300) {
            throw new RuntimeException($this->errorMessage($status, $body));
        }
        if (strlen($body) > self::MAX_RESPONSE_BYTES) {
            throw new RuntimeException('Sparkle BTN API response is too large');
        }

        try {
            $payload = json_decode($body, true, 128, JSON_THROW_ON_ERROR);
        } catch (JsonException $exception) {
            throw new RuntimeException('Sparkle BTN API returned invalid JSON', 0, $exception);
        }

        if (!is_array($payload)) {
            throw new RuntimeException('Sparkle BTN API returned an unexpected response');
        }

        return $this->normalizePayload($payload, $githubUid);
    }

    private function errorMessage(int $status, string $body): string
    {
        $message = '';
        try {
            $decoded = json_decode($body, true, 32, JSON_THROW_ON_ERROR);
            if (is_array($decoded) && is_string($decoded['message'] ?? null)) {
                $message = trim($decoded['message']);
            }
        } catch (JsonException) {
            // Keep a generic message when the upstream body is not JSON.
        }

        return 'Sparkle BTN API request failed with HTTP '.$status
            .($message === '' ? '' : ': '.$message);
    }

    /**
     * @param array<string, mixed> $payload
     * @return array<string, mixed>
     */
    private function normalizePayload(array $payload, string $requestedGithubUid): array
    {
        if (($payload['success'] ?? true) === false) {
            throw new RuntimeException(
                (string) ($payload['message'] ?? 'Sparkle BTN API rejected the request')
            );
        }

        if (isset($payload['data']) && is_array($payload['data'])) {
            $payload = $payload['data'];
        }

        $bound = filter_var(
            $payload['bound'] ?? false,
            FILTER_VALIDATE_BOOLEAN,
            FILTER_NULL_ON_FAILURE
        );
        if ($bound === null) {
            throw new RuntimeException('Sparkle BTN API response has an invalid bound flag');
        }

        $githubUid = $this->stringValue($payload, ['githubUid', 'github_uid', 'githubId']);
        if ($githubUid !== null && $githubUid !== $requestedGithubUid) {
            throw new RuntimeException('Sparkle BTN API returned a different GitHub UID');
        }

        $btnUserId = $this->stringValue($payload, ['btnUserId', 'btn_user_id', 'userId']);
        if ($bound && ($btnUserId === null || !preg_match('/^[0-9]{1,32}$/', $btnUserId))) {
            throw new RuntimeException('Sparkle BTN API did not return a valid BTN user ID');
        }

        if (!$bound) {
            return [
                'bound' => false,
                'githubUid' => $requestedGithubUid,
                'btnUserId' => null,
                'basicInfo' => null,
                'traffic7d' => null,
                'points' => null,
                'rankings' => null,
                'privacy' => null,
            ];
        }

        return [
            'bound' => true,
            'githubUid' => $requestedGithubUid,
            'btnUserId' => $btnUserId,
            'basicInfo' => $this->arrayValue($payload, ['basicInfo', 'basic_info', 'user']),
            'traffic7d' => $this->normalizeTraffic(
                $this->arrayValue($payload, ['traffic7d', 'traffic_7d', 'traffic'])
            ),
            'points' => $this->normalizePoints(
                $this->value($payload, ['points'])
            ),
            'rankings' => $this->normalizeRankings(
                $this->arrayValue($payload, ['rankings'])
            ),
            'privacy' => $this->normalizePrivacy(
                $this->arrayValue($payload, ['privacy'])
            ),
        ];
    }

    /**
     * @param array<string, mixed>|null $privacy
     * @return array<string, mixed>|null
     */
    private function normalizePrivacy(?array $privacy): ?array
    {
        if ($privacy === null) {
            return null;
        }

        return [
            'pointsVisible' => filter_var(
                $privacy['pointsVisible'] ?? $privacy['points_visible'] ?? true,
                FILTER_VALIDATE_BOOLEAN
            ),
        ];
    }

    /**
     * @param array<string, mixed>|null $traffic
     * @return array<string, mixed>|null
     */
    private function normalizeTraffic(?array $traffic): ?array
    {
        if ($traffic === null) {
            return null;
        }

        return [
            'enabled' => filter_var(
                $traffic['enabled'] ?? false,
                FILTER_VALIDATE_BOOLEAN
            ),
            'windowDays' => (int) ($traffic['windowDays'] ?? $traffic['window_days'] ?? 7),
            'sentTrafficSelfReport' => $this->stringValue(
                $traffic,
                ['sentTrafficSelfReport', 'sent_traffic_self_report'],
                '0'
            ),
            'receivedTrafficSelfReport' => $this->stringValue(
                $traffic,
                ['receivedTrafficSelfReport', 'received_traffic_self_report'],
                '0'
            ),
            'sentTrafficOtherAck' => $this->stringValue(
                $traffic,
                ['sentTrafficOtherAck', 'sent_traffic_other_ack'],
                '0'
            ),
            'receivedTrafficOtherAck' => $this->stringValue(
                $traffic,
                ['receivedTrafficOtherAck', 'received_traffic_other_ack'],
                '0'
            ),
            'lastUpdatedAt' => $this->stringValue(
                $traffic,
                ['lastUpdatedAt', 'last_updated_at', 'lastUpdateAt']
            ),
        ];
    }

    /**
     * @return array<string, mixed>|null
     */
    private function normalizePoints(mixed $points): ?array
    {
        if (is_array($points)) {
            $balance = $this->stringValue(
                $points,
                ['balanceBytes', 'balance_bytes', 'scoreBytes', 'score_bytes', 'balance'],
                '0'
            );

            return ['balanceBytes' => $balance];
        }

        if (is_scalar($points)) {
            return ['balanceBytes' => (string) $points];
        }

        return null;
    }

    /**
     * @param array<string, mixed>|null $rankings
     * @return array<string, mixed>|null
     */
    private function normalizeRankings(?array $rankings): ?array
    {
        if ($rankings === null) {
            return null;
        }

        $traffic = $this->arrayValue($rankings, ['traffic']);
        $points = $this->arrayValue($rankings, ['points']);

        return [
            'traffic' => $traffic === null ? null : [
                'rank' => $this->nullableInteger($traffic, ['rank']),
                'sentTrafficSelfReport' => $this->stringValue(
                    $traffic,
                    ['sentTrafficSelfReport', 'sent_traffic_self_report'],
                    '0'
                ),
                'receivedTrafficSelfReport' => $this->stringValue(
                    $traffic,
                    ['receivedTrafficSelfReport', 'received_traffic_self_report'],
                    '0'
                ),
                'sentTrafficOtherAck' => $this->stringValue(
                    $traffic,
                    ['sentTrafficOtherAck', 'sent_traffic_other_ack'],
                    '0'
                ),
                'receivedTrafficOtherAck' => $this->stringValue(
                    $traffic,
                    ['receivedTrafficOtherAck', 'received_traffic_other_ack'],
                    '0'
                ),
            ],
            'points' => $points === null ? null : [
                'rank' => $this->nullableInteger($points, ['rank']),
                'balanceBytes' => $this->stringValue(
                    $points,
                    ['balanceBytes', 'balance_bytes', 'scoreBytes', 'score_bytes'],
                    '0'
                ),
            ],
        ];
    }

    /**
     * @param array<string, mixed> $source
     * @param string[] $keys
     */
    private function value(array $source, array $keys): mixed
    {
        foreach ($keys as $key) {
            if (array_key_exists($key, $source)) {
                return $source[$key];
            }
        }

        return null;
    }

    /**
     * @param array<string, mixed> $source
     * @param string[] $keys
     */
    private function stringValue(array $source, array $keys, ?string $default = null): ?string
    {
        $value = $this->value($source, $keys);

        if ($value === null) {
            return $default;
        }

        if (!is_scalar($value)) {
            return $default;
        }

        $string = trim((string) $value);

        return $string === '' ? $default : $string;
    }

    /**
     * @param array<string, mixed> $source
     * @param string[] $keys
     * @return array<string, mixed>|null
     */
    private function arrayValue(array $source, array $keys): ?array
    {
        $value = $this->value($source, $keys);

        return is_array($value) ? $value : null;
    }

    /**
     * @param array<string, mixed> $source
     * @param string[] $keys
     */
    private function nullableInteger(array $source, array $keys): ?int
    {
        $value = $this->value($source, $keys);

        return is_numeric($value) ? (int) $value : null;
    }

    private function boundedInteger(
        string $key,
        int $default,
        int $minimum,
        int $maximum
    ): int {
        $value = filter_var(
            $this->settings->get($key),
            FILTER_VALIDATE_INT,
            ['options' => ['default' => $default]]
        );

        return max($minimum, min($maximum, (int) $value));
    }
}
