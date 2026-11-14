# 主前端设计规则

适用范围：`frontend/src` 的用户页面、管理页面和独立页面。修改界面时同时遵循 `AGENTS.md`。

这些规则适用于全部页面。使用 Arco 默认组件的页面通过全局主题变量和卡片规则生效；有自定义样式的页面也必须使用同一组变量，不能只调整首页或局部示例。

## 圆角与形状

- 共用圆角为 `8px`，通过 `frontend/src/styles/main.css` 中的 `--app-radius` 定义。
- 卡片、弹窗、按钮、输入框、标签、图标底座和导航选中项统一使用 `var(--app-radius)`；不要新增单独的像素圆角、胶囊形标签或圆形操作按钮。
- Arco 的 small、medium、large 圆角变量统一映射到该变量，浅色和暗色保持相同形状。
- 头像、单选框、开关、状态圆点和纯装饰圆形保留其语义形状。图表几何形状按数据表达需要处理。
- 已有外框负责裁切的内部区域可以使用直角，例如积分卡片的内部内容，避免嵌套圆角造成缝隙。

## 配色与明暗主题

- 基础背景、浮层和卡片使用 Arco 的 `--color-bg-*`、`--color-fill-*`；边框使用 `--color-border-*`。
- 正文、次要说明和辅助信息分别使用 `--color-text-1`、`--color-text-2`、`--color-text-3`。保持层级清楚，不靠降低整个容器透明度区分内容。
- 主色强调使用 `rgb(var(--primary-6))`。浅色主色背景使用 `--color-primary-light-1`，不要直接使用基础色阶 `rgb(var(--primary-1))` 代替主题背景。
- 成功、危险和警告背景使用 `--color-success-light-1`、`--color-danger-light-1`、`--color-warning-light-1`；状态色应表达实际状态，不作为任意装饰色。
- 首页、积分、个人资料和会员汇总等重点区域共用 `--app-feature-bg`、`--app-feature-text`、`--app-feature-muted`，不再分别定义固定蓝绿或橙色渐变及白色文字。
- 不新增固定白底、固定黑字或只适合浅色模式的阴影。品牌图标等确需固定前景色的场景，必须和背景一起检查对比度。
- 主题由 `useTheme` 控制：根元素的 `data-theme` 和 body 的 `arco-theme` 一起切换；不要在页面内另建主题状态。
- Canvas 图表不能直接解析 CSS 变量，应沿用 `EChart.vue` 的主题转换和主题变化重绘机制。

## 组件与布局

- 优先复用 Arco 组件及 `surface-card`、`table-card`、`page-stack`、`filter-actions` 等共用样式。
- 主题切换只改变颜色，保持页面尺寸、间距和交互位置稳定。
- 保持 hover、focus、选中、禁用状态清晰；调整按钮背景时一起检查这些状态。
- 新增或调整布局检查 720px、980px 附近和窄屏显示。保留键盘操作、可访问名称及可见焦点。
- 新文案同步中英文，加载、空、失败状态遵循同一套样式。

## 验证

- 运行 `pnpm --dir frontend typecheck` 和 `pnpm --dir frontend build`。
- 浏览器检查浅色、暗色及主题切换，覆盖卡片、按钮、输入框、标签、弹窗和表格，并检查窄屏布局。
- 登录后的页面需要真实会话验证；仅验证登录页不能代表所有页面已验证。
- 不手改 `components.d.ts`、`build/generated/frontend` 等生成产物。仅提交任务相关源代码；设计规则文档单独提交。
