# 对象存储同步

在应用的“设置 → 对象存储同步”中填写 Endpoint、Region、Bucket、Prefix、寻址方式、Access key ID、Secret access key，以及可选的 Session token。Prefix 默认是 `personal-ledger`。保存设置不会访问网络。点击“测试连接”只列举此前缀下最多一个对象，不写入数据；点击“立即同步到对象存储”才传输账目。没有自动或后台同步。

| 服务 | Endpoint 示例 | Region 示例 | Bucket 示例 | Prefix 示例 | 寻址方式 |
| --- | --- | --- | --- | --- | --- |
| 阿里云 OSS | `https://oss-cn-hangzhou.aliyuncs.com` | `cn-hangzhou` | `my-private-ledger` | `personal` | Virtual host |
| MinIO 风格服务 | `https://storage.example.com` | `us-east-1` | `my-private-ledger` | `personal` | Path style |

Endpoint 必须是 HTTPS，且不能包含账号密码、路径、查询参数或片段。Prefix 是相对路径，可留空，不接受 `..`。示例均为占位值，不能当作真实服务或凭证使用。

请使用**私有 Bucket** 和专用、最小权限凭证：仅允许列举指定前缀，以及读取和写入该前缀下的对象。不需要 Bucket 管理或对象删除权限。长期 AccessKey 保存在移动设备中有泄露风险；优先使用短期凭证。Secret 与 Session token 使用 Android Keystore 加密保存在应用私有配置中。远端账目 JSON 本身没有客户端加密，存储服务的私有访问控制必须正确配置。

账目以 `prefix/v1/records/<recordId>/revisions/<revisionId>.json` 保存。修订 ID 是内容哈希，对象只追加，不覆盖或删除。删除账目会保留墓碑修订。多个设备同时改动同一条账目时，应用展示全部版本供选择；所有冲突完成后才更新本机账目。历史对象会随使用增长，目前没有远端垃圾回收。Git 同步独立运行，仍由用户手动触发。
