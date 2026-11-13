import app from "flarum/admin/app";

export { default as extend } from "./extend";

app.initializers.add("sparkle-btn-flarum", () => {
  // Settings and permissions are registered through the admin extender.
});
