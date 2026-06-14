
## 3.2 MySQL 连接信息

| 项目 | 值 |
|------|-----|
| Host（局域网） | 192.168.31.182 |
| Port | 3306 |
| 容器名 | hive-mysql |

**管理员账户**
- 用户名：`root`
- 密码：`Hwx@1120930`

**Hive 元数据用户**
- 用户名：`hive`
- 密码：`Hwx@1120930`
- 数据库：`metastore`
- 认证插件：`mysql_native_password`（已从 `caching_sha2_password` 修改）

**DBeaver 驱动属性：** `allowPublicKeyRetrieval=true`, `useSSL=false`

