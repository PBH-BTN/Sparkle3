import { extend } from "flarum/common/extend";
import app from "flarum/forum/app";
import Switch from "flarum/common/components/Switch";
import type User from "flarum/common/models/User";
import type ItemList from "flarum/common/utils/ItemList";
import type Mithril from "mithril";

import SparkleBtnStats from "./components/SparkleBtnStats";
import type { SparkleBtnData } from "../common/types";

export { default as extend } from "./extend";

type UserWithSparkle = User & {
  sparkleBtn?: () => SparkleBtnData | null;
};

/**
 * Both components can be lazy loaded by Flarum 2 (the settings page is a
 * lazy route), so they are extended by module path instead of importing the
 * classes. `extend` attaches the callback when the module is registered.
 */
app.initializers.add("sparkle-btn-flarum", () => {
  extend(
    "flarum/forum/components/UserCard",
    "contentItems",
    function (this: any, items: ItemList<Mithril.Children>) {
      const user = this.attrs.user as UserWithSparkle;
      if (!user.sparkleBtn?.()?.btnUserId) return;

      items.add("sparkle-btn-stats", <SparkleBtnStats user={user} />, 70);
    },
  );

  extend(
    "flarum/forum/components/SettingsPage",
    "privacyItems",
    function (this: any, items: ItemList<Mithril.Children>) {
      const user: User | undefined = this.user;
      if (!user || !user.exists) return;

      items.add(
        "sparkle-btn-show-card",
        <Switch
          state={user.preferences()?.sparkleBtnShowCard !== false}
          loading={this.sparkleBtnSaving}
          onchange={(value: boolean) => {
            this.sparkleBtnSaving = true;

            user
              .savePreferences({ sparkleBtnShowCard: value })
              .finally(() => {
                this.sparkleBtnSaving = false;
                m.redraw();
              });
          }}
        >
          {app.translator.trans(
            "sparkle-btn-flarum.forum.settings.show_card_label",
          )}
          <span className="helpText">
            {app.translator.trans(
              "sparkle-btn-flarum.forum.settings.show_card_help",
            )}
          </span>
        </Switch>,
        60,
      );
    },
  );
});
