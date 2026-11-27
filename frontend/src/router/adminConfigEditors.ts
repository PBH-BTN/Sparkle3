// Only fields exposed by a business form redirect there; other keys keep the advanced editor.
const groups: Array<[string, string[], string]> = [
  ['sparkle.footer.', ['config'], '/admin/footer'],
  ['sparkle.homepage.', ['network-traffic-enabled', 'reported-staff-only', 'confirmed-staff-only', 'activity-staff-only', 'average-loss-staff-only', 'total-loss-staff-only'], '/admin/homepage-display'],
  ['sparkle.swarm-statistics-track.', ['enabled', 'refresh-interval-minutes', 'window-days'], '/admin/traffic-statistics'],
  ['sparkle.client-discovery.', ['enabled'], '/admin/site-features'],
  ['sparkle.user-contact-email.', ['subject-template', 'content-template'], '/admin/contact-template'],
  ['sparkle.mail.', ['pop3.connection-timeout-seconds', 'pop3.enabled', 'pop3.host', 'pop3.max-messages-per-poll', 'pop3.password', 'pop3.poll-interval-seconds', 'pop3.port', 'pop3.reply-address', 'pop3.security', 'pop3.username', 'smtp.auth', 'smtp.connection-timeout-seconds', 'smtp.enabled', 'smtp.from-address', 'smtp.from-name', 'smtp.host', 'smtp.password', 'smtp.port', 'smtp.read-timeout-seconds', 'smtp.security', 'smtp.username', 'smtp.write-timeout-seconds'], '/admin/mail'],
  ['sparkle.ai.moderation.', ['api-key', 'base-url', 'enabled', 'max-attempts', 'model', 'moderate-avatars', 'rubric', 'timeout-seconds'], '/admin/ai-moderation'],
  ['sparkle.ip-query.comments.', ['enabled', 'hourly-limit', 'max-length', 'visible'], '/admin/comment-policy'],
  ['sparkle.github-community.', ['api-base-url', 'personal-access-token'], '/admin/community-integration'],
  ['sparkle.github-sync.', ['schedule', 'personal-access-token'], '/admin/github-sync'],
  ['sparkle.exports.', ['max-rows', 'max-file-mib', 'retention-hours', 'download-mbps', 'daily-slots', 'pool-mib', 'prompt-zh', 'prompt-en'], '/admin/exports'],
  ['sparkle.career.', ['dedup-retention-days'], '/admin/btn/retention'],
  ['sparkle.afdian.', ['license-donation-amount', 'license-private-key', 'license-valid-years'], '/admin/membership-licenses'],
  ['sparkle.afdian.', ['access-token', 'api-base-url', 'sync-enabled', 'user-id'], '/admin/membership-providers'],
  ['sparkle.memberships.', ['base-currency', 'exchange-rate-api-url', 'supporter-threshold-cny'], '/admin/membership-policy'],
  ['sparkle.flarum-integration.', ['token'], '/admin/flarum-integration'],
  ['sparkle.homepage-statistics-refresh.modules.ban-history.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.active-apps.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.swarm-tracker.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.torrents.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.network-charts.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.companion-charts.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
  ['sparkle.homepage-statistics-refresh.modules.ip-flags.', ['enabled', 'refresh-interval-minutes'], '/admin/homepage-statistics'],
]

export const adminConfigEditors = Object.fromEntries(groups.flatMap(([prefix, fields, path]) => fields.map((field) => [prefix + field, path])))
