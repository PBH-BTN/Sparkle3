import { extend } from "flarum/common/extend";
import app from "flarum/forum/app";
import UserCard from "flarum/forum/components/UserCard";
import type User from "flarum/common/models/User";
import type ItemList from "flarum/common/utils/ItemList";
import type Mithril from "mithril";

import SparkleBtnStats from "./components/SparkleBtnStats";
import type { SparkleBtnData } from "../common/types";

export { default as extend } from "./extend";

type UserWithSparkle = User & {
  sparkleBtn?: () => SparkleBtnData | null;
};

app.initializers.add("sparkle-btn-flarum", () => {
  extend(
    UserCard.prototype as any,
    "contentItems",
    function (this: UserCard, items: ItemList<Mithril.Children>) {
      const user = this.attrs.user as UserWithSparkle;
      if (!user.sparkleBtn?.()?.btnUserId) return;

      items.add("sparkle-btn-stats", <SparkleBtnStats user={user} />, 70);
    },
  );
});
