import Extend from "flarum/common/extenders";
import app from "flarum/admin/app";

export default [
  new Extend.Admin()
    .setting(
      () => ({
        setting: "sparkle-btn.endpoint",
        type: "url",
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.endpoint_label",
        ),
        help: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.endpoint_help",
        ),
        placeholder:
          "https://sparkle.example.com/api/integrations/flarum/user-by-github",
      }),
      120,
    )
    .setting(
      () => ({
        setting: "sparkle-btn.token",
        type: "password",
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.token_label",
        ),
        help: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.token_help",
        ),
        autocomplete: "new-password",
      }),
      110,
    )
    .setting(
      () => ({
        setting: "sparkle-btn.sync_interval_minutes",
        type: "number",
        min: 5,
        max: 10080,
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.sync_interval_label",
        ),
        help: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.sync_interval_help",
        ),
      }),
      100,
    )
    .setting(
      () => ({
        setting: "sparkle-btn.batch_size",
        type: "number",
        min: 1,
        max: 500,
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.batch_size_label",
        ),
        help: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.batch_size_help",
        ),
      }),
      90,
    )
    .setting(
      () => ({
        setting: "sparkle-btn.request_timeout",
        type: "number",
        min: 1,
        max: 60,
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.timeout_label",
        ),
        help: app.translator.trans(
          "sparkle-btn-flarum.admin.settings.timeout_help",
        ),
      }),
      80,
    )
    .permission(
      () => ({
        icon: "fas fa-satellite-dish",
        label: app.translator.trans(
          "sparkle-btn-flarum.admin.permissions.view_user_data_label",
        ),
        permission: "sparkleBtn.viewUserData",
      }),
      "view",
      80,
    ),
];
