// Admin navigation, routes and frontend permissions share this registry.
export interface AdminPage {
  id: string
  category: string
  path: string
  name: string
  label: string
  view: string
  moderator: boolean
  props: Record<string, string>
}

export const adminCategories = [
  {
    'id': 'home',
    'label': 'admin.organization.categories.home'
  },
  {
    'id': 'global',
    'label': 'admin.organization.categories.global'
  },
  {
    'id': 'interface',
    'label': 'admin.organization.categories.interface'
  },
  {
    'id': 'users',
    'label': 'admin.organization.categories.users'
  },
  {
    'id': 'content',
    'label': 'admin.organization.categories.content'
  },
  {
    'id': 'btn',
    'label': 'admin.organization.categories.btn'
  },
  {
    'id': 'operations',
    'label': 'admin.organization.categories.operations'
  },
  {
    'id': 'applications',
    'label': 'admin.organization.categories.applications'
  },
  {
    'id': 'tools',
    'label': 'admin.organization.categories.tools'
  },
  {
    'id': 'webmaster',
    'label': 'admin.organization.categories.webmaster'
  }
]

export const adminPages: AdminPage[] = [
  {
    'id': 'dashboard',
    'category': 'home',
    'path': '/admin/dashboard',
    'name': 'admin-dashboard',
    'label': 'admin.organization.pages.dashboard',
    'view': 'AdminDashboardView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'points-modules',
    'category': 'global',
    'path': '/admin/points-modules',
    'name': 'admin-points-modules',
    'label': 'admin.organization.pages.points-modules',
    'view': 'AdminModulesView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'points-timezone',
    'category': 'global',
    'path': '/admin/btn/points-timezone',
    'name': 'admin-points-timezone',
    'label': 'admin.organization.pages.points-timezone',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'points-timezone'
    }
  },
  {
    'id': 'exports',
    'category': 'global',
    'path': '/admin/exports',
    'name': 'admin-data-exports',
    'label': 'admin.organization.pages.exports',
    'view': 'AdminDataExportsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'navigation',
    'category': 'interface',
    'path': '/admin/navigation',
    'name': 'admin-navigation',
    'label': 'admin.organization.pages.navigation',
    'view': 'AdminNavigationView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'homepage-display',
    'category': 'interface',
    'path': '/admin/homepage-display',
    'name': 'admin-homepage-display',
    'label': 'admin.organization.pages.homepage-display',
    'view': 'AdminRuntimeSettingsView',
    'moderator': false,
    'props': {
      'section': 'homepage-display'
    }
  },
  {
    'id': 'footer',
    'category': 'interface',
    'path': '/admin/footer',
    'name': 'admin-footer',
    'label': 'admin.organization.pages.footer',
    'view': 'AdminFooterSettingsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'users',
    'category': 'users',
    'path': '/admin/users',
    'name': 'admin-users',
    'label': 'admin.organization.pages.users',
    'view': 'AdminUsersView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'community-groups',
    'category': 'users',
    'path': '/admin/community-groups',
    'name': 'admin-community-groups',
    'label': 'admin.organization.pages.community-groups',
    'view': 'AdminCommunityGroupsView',
    'moderator': false,
    'props': {
      'scope': 'groups'
    }
  },
  {
    'id': 'ip-query-comments',
    'category': 'content',
    'path': '/admin/ip-query-comments',
    'name': 'admin-ip-query-comments',
    'label': 'admin.organization.pages.ip-query-comments',
    'view': 'AdminIpQueryCommentsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'comment-policy',
    'category': 'content',
    'path': '/admin/comment-policy',
    'name': 'admin-comment-policy',
    'label': 'admin.organization.pages.comment-policy',
    'view': 'AdminAiModerationView',
    'moderator': false,
    'props': {
      'scope': 'comments'
    }
  },
  {
    'id': 'content-moderation',
    'category': 'content',
    'path': '/admin/content-moderation',
    'name': 'admin-content-moderation',
    'label': 'admin.organization.pages.content-moderation',
    'view': 'AdminContentModerationView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'ai-moderation',
    'category': 'content',
    'path': '/admin/ai-moderation',
    'name': 'admin-ai-moderation',
    'label': 'admin.organization.pages.ai-moderation',
    'view': 'AdminAiModerationView',
    'moderator': false,
    'props': {
      'scope': 'provider'
    }
  },
  {
    'id': 'moderation-queue',
    'category': 'content',
    'path': '/admin/moderation-queue',
    'name': 'admin-moderation-queue',
    'label': 'admin.organization.pages.moderation-queue',
    'view': 'AdminAiModerationView',
    'moderator': false,
    'props': {
      'scope': 'queue'
    }
  },
  {
    'id': 'communication',
    'category': 'btn',
    'path': '/admin/btn/communication',
    'name': 'admin-communication',
    'label': 'admin.organization.pages.communication',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'communication'
    }
  },
  {
    'id': 'reporting',
    'category': 'btn',
    'path': '/admin/btn/reporting',
    'name': 'admin-reporting',
    'label': 'admin.organization.pages.reporting',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'reporting'
    }
  },
  {
    'id': 'site-features',
    'category': 'btn',
    'path': '/admin/site-features',
    'name': 'admin-site-features',
    'label': 'admin.organization.pages.site-features',
    'view': 'AdminRuntimeSettingsView',
    'moderator': false,
    'props': {
      'section': 'features'
    }
  },
  {
    'id': 'rule-delivery',
    'category': 'btn',
    'path': '/admin/btn/rule-delivery',
    'name': 'admin-rule-delivery',
    'label': 'admin.organization.pages.rule-delivery',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'rule-delivery'
    }
  },
  {
    'id': 'rules',
    'category': 'btn',
    'path': '/admin/rules',
    'name': 'admin-rules',
    'label': 'admin.organization.pages.rules',
    'view': 'AdminRulesView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'modules',
    'category': 'btn',
    'path': '/admin/modules',
    'name': 'admin-modules',
    'label': 'admin.organization.pages.modules',
    'view': 'AdminModulesView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'github-sync',
    'category': 'btn',
    'path': '/admin/github-sync',
    'name': 'admin-github-sync',
    'label': 'admin.organization.pages.github-sync',
    'view': 'AdminGithubSyncView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'reporting-risk',
    'category': 'btn',
    'path': '/admin/btn/reporting-risk',
    'name': 'admin-reporting-risk',
    'label': 'admin.organization.pages.reporting-risk',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'reporting-risk'
    }
  },
  {
    'id': 'ban-submissions',
    'category': 'btn',
    'path': '/admin/ban-submissions',
    'name': 'admin-ban-submissions',
    'label': 'admin.organization.pages.ban-submissions',
    'view': 'AdminBanSubmissionsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'ip-warning',
    'category': 'btn',
    'path': '/admin/btn/ip-warning',
    'name': 'admin-ip-warning',
    'label': 'admin.organization.pages.ip-warning',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'ip-warning'
    }
  },
  {
    'id': 'measure',
    'category': 'btn',
    'path': '/admin/btn/measure',
    'name': 'admin-measure',
    'label': 'admin.organization.pages.measure',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'measure'
    }
  },
  {
    'id': 'security',
    'category': 'btn',
    'path': '/admin/btn/security',
    'name': 'admin-security',
    'label': 'admin.organization.pages.security',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'security'
    }
  },
  {
    'id': 'query',
    'category': 'btn',
    'path': '/admin/btn/query',
    'name': 'admin-query',
    'label': 'admin.organization.pages.query',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'query'
    }
  },
  {
    'id': 'userapps',
    'category': 'btn',
    'path': '/admin/userapps',
    'name': 'admin-userapps',
    'label': 'admin.organization.pages.userapps',
    'view': 'AdminUserAppsView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'auto-accounts',
    'category': 'btn',
    'path': '/admin/btn/auto-accounts',
    'name': 'admin-auto-accounts',
    'label': 'admin.organization.pages.auto-accounts',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'auto-accounts'
    }
  },
  {
    'id': 'ranking',
    'category': 'btn',
    'path': '/admin/btn/ranking',
    'name': 'admin-ranking',
    'label': 'admin.organization.pages.ranking',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'ranking'
    }
  },
  {
    'id': 'traffic-statistics',
    'category': 'btn',
    'path': '/admin/traffic-statistics',
    'name': 'admin-traffic-statistics',
    'label': 'admin.organization.pages.traffic-statistics',
    'view': 'AdminRuntimeSettingsView',
    'moderator': false,
    'props': {
      'section': 'statistics'
    }
  },
  {
    'id': 'version-alerts',
    'category': 'btn',
    'path': '/admin/version-alerts',
    'name': 'admin-version-alerts',
    'label': 'admin.organization.pages.version-alerts',
    'view': 'AdminVersionAlertsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'retention',
    'category': 'btn',
    'path': '/admin/btn/retention',
    'name': 'admin-retention',
    'label': 'admin.organization.pages.retention',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'retention'
    }
  },
  {
    'id': 'maintenance',
    'category': 'btn',
    'path': '/admin/btn/maintenance',
    'name': 'admin-maintenance',
    'label': 'admin.organization.pages.maintenance',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'maintenance'
    }
  },
  {
    'id': 'mail-identities',
    'category': 'btn',
    'path': '/admin/btn/mail-identities',
    'name': 'admin-mail-identities',
    'label': 'admin.organization.pages.mail-identities',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'mail-identities'
    }
  },
  {
    'id': 'tasks',
    'category': 'btn',
    'path': '/admin/tasks',
    'name': 'admin-tasks',
    'label': 'admin.organization.pages.tasks',
    'view': 'AdminTaskStatusView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'announcements',
    'category': 'operations',
    'path': '/admin/announcements',
    'name': 'admin-announcements',
    'label': 'admin.organization.pages.announcements',
    'view': 'AdminAnnouncementsView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'messages',
    'category': 'operations',
    'path': '/admin/messages',
    'name': 'admin-messages',
    'label': 'admin.organization.pages.messages',
    'view': 'AdminMessagesView',
    'moderator': true,
    'props': {}
  },
  {
    'id': 'membership-transactions',
    'category': 'operations',
    'path': '/admin/membership-transactions',
    'name': 'admin-membership-transactions',
    'label': 'admin.organization.pages.membership-transactions',
    'view': 'AdminMembershipsView',
    'moderator': false,
    'props': {
      'scope': 'transactions'
    }
  },
  {
    'id': 'membership-licenses',
    'category': 'operations',
    'path': '/admin/membership-licenses',
    'name': 'admin-membership-licenses',
    'label': 'admin.organization.pages.membership-licenses',
    'view': 'AdminMembershipsView',
    'moderator': false,
    'props': {
      'scope': 'licenses'
    }
  },
  {
    'id': 'membership-reconciliation',
    'category': 'operations',
    'path': '/admin/membership-reconciliation',
    'name': 'admin-membership-reconciliation',
    'label': 'admin.organization.pages.membership-reconciliation',
    'view': 'AdminMembershipsView',
    'moderator': false,
    'props': {
      'scope': 'reconciliation'
    }
  },
  {
    'id': 'membership-policy',
    'category': 'operations',
    'path': '/admin/membership-policy',
    'name': 'admin-membership-policy',
    'label': 'admin.organization.pages.membership-policy',
    'view': 'AdminMembershipsView',
    'moderator': false,
    'props': {
      'scope': 'policy'
    }
  },
  {
    'id': 'membership-providers',
    'category': 'applications',
    'path': '/admin/membership-providers',
    'name': 'admin-membership-providers',
    'label': 'admin.organization.pages.membership-providers',
    'view': 'AdminMembershipsView',
    'moderator': false,
    'props': {
      'scope': 'providers'
    }
  },
  {
    'id': 'flarum-integration',
    'category': 'applications',
    'path': '/admin/flarum-integration',
    'name': 'admin-flarum-integration',
    'label': 'admin.organization.pages.flarum-integration',
    'view': 'AdminFlarumIntegrationView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'community-integration',
    'category': 'applications',
    'path': '/admin/community-integration',
    'name': 'admin-community-integration',
    'label': 'admin.organization.pages.community-integration',
    'view': 'AdminCommunityGroupsView',
    'moderator': false,
    'props': {
      'scope': 'integration'
    }
  },
  {
    'id': 'homepage-statistics',
    'category': 'tools',
    'path': '/admin/homepage-statistics',
    'name': 'admin-homepage-statistics',
    'label': 'admin.organization.pages.homepage-statistics',
    'view': 'AdminHomepageStatisticsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'mail',
    'category': 'webmaster',
    'path': '/admin/mail',
    'name': 'admin-mail',
    'label': 'admin.organization.pages.mail',
    'view': 'AdminMailSettingsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'contact-template',
    'category': 'webmaster',
    'path': '/admin/contact-template',
    'name': 'admin-contact-template',
    'label': 'admin.organization.pages.contact-template',
    'view': 'AdminRuntimeSettingsView',
    'moderator': false,
    'props': {
      'section': 'contact-template'
    }
  },
  {
    'id': 'user-contact-emails',
    'category': 'webmaster',
    'path': '/admin/user-contact-emails',
    'name': 'admin-user-contact-emails',
    'label': 'admin.organization.pages.user-contact-emails',
    'view': 'AdminUserContactEmailsView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'storage',
    'category': 'webmaster',
    'path': '/admin/storage',
    'name': 'admin-storage',
    'label': 'admin.organization.pages.storage',
    'view': 'AdminStorageView',
    'moderator': false,
    'props': {}
  },
  {
    'id': 'cache',
    'category': 'webmaster',
    'path': '/admin/btn/cache',
    'name': 'admin-cache',
    'label': 'admin.organization.pages.cache',
    'view': 'AdminBtnConfigView',
    'moderator': true,
    'props': {
      'group': 'cache'
    }
  },
  {
    'id': 'database-config',
    'category': 'webmaster',
    'path': '/admin/database-config',
    'name': 'admin-database-config',
    'label': 'admin.organization.pages.database-config',
    'view': 'AdminDatabaseConfigView',
    'moderator': false,
    'props': {}
  }
]

export const adminLegacyRedirects = [
  {
    'path': '/admin/settings',
    'name': 'admin-settings',
    'to': '/admin/traffic-statistics'
  },
  {
    'path': '/admin/btn',
    'name': 'admin-btn',
    'to': '/admin/btn/communication'
  },
  {
    'path': '/admin/memberships',
    'name': 'admin-memberships',
    'to': '/admin/membership-transactions'
  },
  {
    'path': '/admin/afdian',
    'name': 'admin-afdian',
    'to': '/admin/membership-providers'
  },
  {
    'path': '/admin/geoip-city-blacklist',
    'name': 'admin-geoip-city-blacklist',
    'to': '/admin/rules?tab=geoip'
  }
]

export const moderatorRouteNames = new Set(adminPages.filter((page) => page.moderator).map((page) => page.name))
