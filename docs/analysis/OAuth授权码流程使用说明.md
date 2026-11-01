# OAuth 授权码流程 —— 使用说明

> 已完成实现。本文是给**部署者**和**第三方开发者**看的使用文档；
> 设计取舍与安全分析见 `OAuth授权码流程设计方案.md`。

---

## 一、两种流程，别混用

| | client_credentials | authorization_code |
|---|---|---|
| 证明什么 | 是哪个应用 | **哪个玩家同意了** |
| token 里带 uid | 否 | 是 |
| 能读的数据 | 只有公共数据 | 该玩家授权范围内的数据 |
| 用途 | 拉排行榜、物品图鉴 | 读别人的背包、替他操作 |

**红线**：`client_credentials` 签出的 token **永远**没有用户身份。
用它读 `/api/game/inventory` 会直接 403。这是设计如此，不是 bug。

---

## 二、部署者：登记第三方应用

编辑 `config/application.yml`：

```yaml
oauth2:
  enabled: true
  clients:
    - client_id: my_web_app
      client_secret: 换成32位以上随机字符串
      name: 修仙助手                    # 同意页上给玩家看的名字
      redirect_uris:
        - https://app.example.com/oauth/callback
      scopes:
        - public:read
        - player:read
        - inventory:read
      enabled: true
```

改完重启即可。

### 回调地址必须完全一致

校验用的是字符串**全等**，不是前缀。以下都会被拒绝：

```
https://app.example.com.evil.com/oauth/callback   ← 虽然以你的域名开头
https://app.example.com/oauth/callback?x=1        ← 多带参数
https://app.example.com/oauth/callback/           ← 多一个斜杠
```

这是刻意的。授权码流程最常见的漏洞就是回调地址校验太松，
导致授权码被引到攻击者服务器。

---

## 三、可用的 scope

按**资源域**切分，每个域分只读/读写。

| scope | 说明 | 风险 |
|---|---|---|
| `public:read` | 排行榜、物品图鉴、地图、玩家名录 | 低 |
| `player:read` | 等级、境界、属性、位置 | 低 |
| `cultivation:read` | 修为、功法、突破记录 | 低 |
| `inventory:read` | 背包、装备、已学技能 | 中 |
| `economy:read` | 坊市行情、自己的挂单 | 中 |
| `sect:read` | 宗门信息、成员列表 | 中 |
| `social:read` | 好友、队伍 | 中 |
| `cultivation:write` | 打坐、领修炼收益 | 高 |
| `inventory:write` | 装备/卸下/强化、使用消耗品、学技能 | 高 |
| `economy:write` | 上架、购买、撤单（动灵石） | 高 |
| `sect:write` | 申请入宗、退宗、捐献 | 高 |
| `social:write` | 发私信、组队邀请 | 高 |

**写权限不自动包含读权限**。要让应用既读又写背包，
必须同时申请 `inventory:read` 和 `inventory:write` ——
这样玩家在同意页能清楚看到"它能读我的背包"。

### 永远不开放的操作

即使 scope 拉满，以下端点也会被硬性拦截：

- `game/player/data/restore`、`game/player/data/export` —— 覆盖/导出存档
- `game/item/add` —— 凭空发物品
- `game/sect/kick`、`game/sect/appoint` —— 踢人、任命（影响其他玩家）
- `game/sect/approve`、`game/sect/reject` —— 审批
- `game/sect/transfer`、`game/sect/dissolve` —— 转让、解散

理由：这些操作不可逆，或会让第三方获得超出一名普通玩家的能力。

---

## 四、第三方接入流程

### 步骤 1：把用户重定向到授权页

```
https://你的服务器/webadmin/?response_type=code
  &client_id=my_web_app
  &redirect_uri=https://app.example.com/oauth/callback
  &scope=player:read inventory:read
  &state=<随机串>
```

`state` 由你生成并保存在会话里，用于防 CSRF。服务端会原样带回。

玩家必须先登录游戏账号，未登录时会提示。

### 步骤 2：玩家在同意页确认

页面会显示应用名和每一项权限的具体含义，玩家点「同意」或「拒绝」。

### 步骤 3：回到你的回调地址

同意：
```
https://app.example.com/oauth/callback?code=xxx&state=你传来的state
```

拒绝：
```
https://app.example.com/oauth/callback?error=access_denied&state=...
```

**先校验 state 与你自己保存的一致**，不一致说明是 CSRF，直接丢弃。

### 步骤 4：用 code 换 token（后端调用，不要在前端做）

```bash
curl -X POST https://你的服务器/api/auth/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "code": "<上一步拿到的 code>",
    "client_id": "my_web_app",
    "client_secret": "<你的密钥>",
    "redirect_uri": "https://app.example.com/oauth/callback"
  }'
```

返回：

```json
{
  "access_token": "...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "player:read inventory:read",
  "refresh_token": "..."
}
```

授权码 **只能用一次**，10 分钟内有效。

### 步骤 5：调用游戏 API

```bash
curl https://你的服务器/api/game/inventory \
  -H "Authorization: Bearer <access_token>"
```

服务端会把这个 token 映射到授权的那个玩家，返回**他的**背包。
你不需要（也不能）指定玩家 id。

### 步骤 6：刷新 token

```bash
curl -X POST https://你的服务器/api/auth/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "refresh_token",
    "refresh_token": "<上次拿到的>",
    "client_id": "my_web_app",
    "client_secret": "<你的密钥>"
  }'
```

**每次刷新都会换一个新的 refresh_token，旧的立即失效。**
务必保存新的那个。

重放已用过的 refresh_token 会被判定为泄露，导致整条授权被吊销，
用户需要重新授权。所以刷新失败时不要重试同一个旧 token。

---

## 五、玩家侧：查看与撤销

玩家在 WebUI 的「已授权应用」页面能看到：

- 哪些应用能访问他的数据
- 每一项权限是什么、风险等级
- 授权时间、最近使用时间

点「撤销授权」后，该应用持有的 access_token 与 refresh_token **立即失效**
（不是等过期）。

---

## 六、安全设计要点

| 攻击 | 防护 |
|---|---|
| 回调地址换成外域 | 登记表全等匹配 |
| `app.com.evil.com` 绕前缀 | 同上（不做前缀匹配） |
| 授权码重放 | 消费时先 remove，一次性 |
| 拿别的应用的 code | 绑定 client_id，换取时校验 |
| 拿 code 配别的 redirect | 绑定 redirect_uri，换取时校验 |
| 未同意就发码 | 必须经过 confirm，且 `approved=false` 不发码 |
| 客户端凭据冒充玩家 | token 必须有 uid claim 才算用户身份 |
| read 权限执行写操作 | 按 HTTP 方法映射到 `:read`/`:write` |
| 撤销后仍能用 | 校验时查 jti 是否在有效授权里 |
| refresh_token 泄露 | 轮换 + 重放检测吊销整条链 |
| 用别人的 grant_id 撤销 | 校验 grant 归属 |
| 内省端点当探针 | 必须带客户端凭据 |

---

## 七、已知限制

1. **授权记录已持久化，但会话不持久化。**

   落库的是「玩家授权过谁」这件事（`oauth_grants` 表）。
   access_token 的 jti 与 refresh_token 都在内存里，重启即失效。

   所以服务器重启后的实际表现是：

   - ✅ 「已授权应用」页面仍然正确，玩家能看到并撤销
   - ⚠️ 第三方持有的 token 失效，需要重新走一次授权码流程

   这个取舍是刻意的：持久化 refresh_token 意味着数据库一旦泄露就能长期冒用，
   代价大于收益。

2. **写权限尚未实际开放业务验证**。scope 映射和拦截已实现并有测试，
   但没有真实第三方用写权限跑过。首次开放前建议先在测试环境验证。

3. **`client_credentials` 只能访问公共数据**，这是刻意的。
   如果你需要机器对机器读取玩家数据，那应该用授权码流程，
   或者给该服务建一个专属的服务账号。

---

## 八、测试覆盖

| 测试类 | 数量 | 覆盖 |
|---|---|---|
| `OAuthServiceTest` | 17 | 签发/解析/scope/内省/密钥打码 |
| `OAuthScopeEscalationTest` | 6 | scope 越权收窄 |
| `OAuthEndpointTest` | 26 | 全部端点行为，含授权码全流程 |
| `OAuthAuthorizationFlowTest` | 14 | 授权码攻击面 |
| `OAuthScopeEnforcementTest` | 23 | 细粒度 scope 是否真的拦住 |
| `OAuthGrantPersistenceTest` | 10 | 落库/重启恢复/撤销不复活 |

合计 96 个。

### 关于持久化的两个易错点（已被测试锁定）

- **恢复时必须保留原始 `created_at`**：重置成 now 会让「已授权应用」页显示
  "刚刚授权"，玩家会以为有陌生应用刚拿到权限。
- **撤销用 `revoked` 标记而非删行**：删行会丢失"曾经授权过"的事实，
  排查"玩家说没授权但应用拿到了 token"时无从追溯。真删由 `purgeExpired`
  在 refresh_token 过期后执行。
