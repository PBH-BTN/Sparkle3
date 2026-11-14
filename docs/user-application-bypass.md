# 用户应用 BTN Bypass

用户应用列表中，未启用 BTN Bypass 的应用显示“申请 BTN Bypass”，在新标签页打开 https://bbs.pbh-btn.com/d/58。
已启用的应用显示关联 IP 数量，点击后展示最近一小时心跳关联的完整 IP 列表及 GeoIP 属地。
已暂停应用不返回豁免 IP；没有记录时显示空状态。IPv4 与 IPv6 地址去重展示，GeoIP 不可用时仍展示 IP。

- GET /api/ui/userapps/{appId}/bypass-ips：只允许应用所有者访问，沿用登录身份校验。
- 响应为对象列表，每项包含 ip 和 peerGeoip；IP 数据不公开给其他用户，不包含应用密钥。
- 地址有效范围沿用允许列表生成时的最近一小时心跳窗口。

BTN Bypass 允许列表的注释保留用户名、UID、应用名和同步时间，不再包含邮箱字段。
更新后的注释在下一次允许列表生成后生效，已导出的旧文件需重新生成及同步。
