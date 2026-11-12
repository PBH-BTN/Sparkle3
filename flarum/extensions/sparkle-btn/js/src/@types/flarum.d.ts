declare module "flarum/common/Component" {
  import type Mithril from "mithril";

  export interface ComponentAttrs extends Mithril.Attributes {
    [key: string]: unknown;
  }

  export default class Component<
    Attrs extends ComponentAttrs = ComponentAttrs,
  > {
    attrs: Attrs;
    element: HTMLElement;

    oninit(vnode: Mithril.Vnode<Attrs, this>): void;
    view(vnode: Mithril.Vnode<Attrs, this>): Mithril.Children;
  }
}

declare module "flarum/common/extend" {
  export function extend(
    object: Record<string, unknown>,
    methods: string | string[],
    callback: (...args: any[]) => void,
  ): void;
}

declare module "flarum/common/extenders" {
  interface ModelExtender {
    attribute<T>(
      name: string,
      transform?: ((value: unknown) => T) | null,
    ): ModelExtender;
    hasOne(name: string): ModelExtender;
    hasMany(name: string): ModelExtender;
  }

  interface AdminExtender {
    setting(config: () => unknown, priority?: number): AdminExtender;
    permission(
      config: () => unknown,
      type: string,
      priority?: number,
    ): AdminExtender;
  }

  const Extend: {
    Model: new (model: unknown) => ModelExtender;
    Admin: new () => AdminExtender;
  };

  export default Extend;
}

declare module "flarum/common/models/User" {
  export default class User {
    id(): string;
  }
}

declare module "flarum/common/utils/ItemList" {
  export default class ItemList<T = unknown> {
    add(key: string, content: T, priority?: number): this;
    get(key: string): T;
    has(key: string): boolean;
  }
}

declare module "flarum/forum/components/UserCard" {
  export default class UserCard {
    attrs: {
      user: import("flarum/common/models/User").default;
      [key: string]: unknown;
    };
  }
}

declare module "flarum/forum/app" {
  const app: any;
  export default app;
}

declare module "flarum/admin/app" {
  const app: any;
  export default app;
}
