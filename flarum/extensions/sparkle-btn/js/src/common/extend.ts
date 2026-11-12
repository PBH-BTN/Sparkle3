import Extend from "flarum/common/extenders";
import User from "flarum/common/models/User";
import type { SparkleBtnData } from "./types";

export default [
  new Extend.Model(User).attribute<SparkleBtnData | null>("sparkleBtn"),
];
