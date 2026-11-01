# OAuth 2.0 授权码流程 —— 设计方案

> 状态：**已实现**（见 `OAuth授权码流程使用说明.md` 为部署与接入文档）
> 目标：让第三方应用在**玩家明确授权**的前提下，读取该玩家自己的游戏数据。

---

## 1. 为什么不能用 client_credentials 读用户数据

现有的 `client_credentials` 只回答一个问题：**「这个应用是谁」**。

它证明不了第二件事：**「哪个玩家同意了」**。

如果用它直接访问 `/api/game/inventory`，服务端无从知道该返回哪个玩家的背包。硬要支持，就只能让客户端传 `playerId` —— 那么任何拿到 `client_secret` 的应用都能遍历全服玩家的背包、灵石、宗门身份。

**这是本次改造最重要的一条红线：**

> `client_credentials` 签出的 token **永远不能**映射到任何用户身份。
> 只有授权码流程签出的 token 才携带「用户」维度。

---

## 2. 两种 token 的区分

必须让系统能一眼分清两种 token，否则过滤器的判断会出错。

| | 客户端凭证型 | 用户授权型 |
|---|---|---|
| 用途 | 读公共数据 | 读**某用户**的数据 |
| `sub` | `oauth2:<clientId>` | `user:<userId>` |
| `token_type` | `oauth2` | `oauth2` |
| `grant_type` | `client_credentials` | `authorization_code` |
| 携带 userId | **否** | **是**（`uid` claim） |

判断依据用 `uid` claim 是否存在 —— 存在即用户型，不存在即客户端型。
**不要**用 scope 名来判断身份类型（scope 是可被请求的，身份不是）。

---

## 3. 完整流程

```
① 第三方把用户浏览器重定向到：
   GET /api/auth/oauth/authorize
       ?response_type=code
       &client_id=<clientId>
       &redirect_uri=<已登记的回调地址>
       &scope=read
       &state=<防CSRF随机串>

② 服务端校验 client_id / redirect_uri / scope 合法性
   → 若用户未登录：跳登录页，登录后回到本页
   → 渲染「同意页」：应用名 + 申请的数据范围 + 拒绝/同意按钮

③ 用户点「同意」
   POST /api/auth/oauth/authorize/confirm
   → 服务端生成一次性 code（30 秒~10 分钟有效，绑 client_id + redirect_uri + userId + scope）
   → 302 跳回 redirect_uri?code=xxx&state=<原样带回>

④ 第三方后端（不是浏览器）用 code 换 token：
   POST /api/auth/oauth/token
   { grant_type=authorization_code, code, redirect_uri, client_id, client_secret }
   → { access_token, refresh_token, expires_in, scope }

⑤ 第三方带 access_token 访问：
   GET /api/game/inventory
   Authorization: Bearer <access_token>
   → 过滤器识别出 uid → 映射成该用户 → 现有端点无需改动
```

---

## 4. 安全要点（逐条对应实现）

这一节是重点。授权码流程的每个环节都有经典攻击，逐条列出防护方式。

### 4.1 redirect_uri 必须精确匹配

**攻击**：把 `redirect_uri` 改成 `https://evil.com`，把 code 骗到攻击者服务器。

**防护**：client 注册时登记完整回调地址；授权时做**字符串完全相等**比较。
不允许前缀匹配、不允许通配、不允许省略 `https`。

> 常见错误是「以登记值开头即可」，那会让
> `https://app.com.evil.com` 通过（以 `https://app.com` 开头）。
> 必须 `equals`。

### 4.2 code 必须一次性

**攻击**：code 泄露后被重放。

**防护**：code 存内存 Map，换取时**先 remove 再校验**。
重复使用 → 401 invalid_grant，并把该 code 已签发过的 token 一并吊销（RFC 6749 §4.1.2 建议）。

### 4.3 code 必须绑定 client_id 与 redirect_uri

**攻击**：A 应用拿到 B 应用的 code 去换 token。

**防护**：换取时比对 code 生成时记录的 `client_id` 与 `redirect_uri`，不一致即拒绝。

### 4.4 code 有效期要短

**防护**：10 分钟。过期即失效。

### 4.5 state 参数必须原样回传

**攻击**：CSRF —— 攻击者诱导受害者访问自己的授权回调，把受害者的账号绑到攻击者的第三方账号上。

**防护**：`state` 由第三方生成，服务端**原样带回**（服务端不校验 state 内容，那是客户端的责任，但必须保证不改动、不丢弃）。

### 4.6 用户必须明确同意，且能看到授权范围

**防护**：不能跳过同意页直接发 code。同意页要显示具体 scope 的**人类可读描述**，不是 `read` 这种技术名词。

### 4.7 用户型 token 的权限边界

这是最容易出事的地方。**用户型 OAuth token 不能等同于玩家的完整登录凭据。**

玩家的 JWT 能调 107 个端点（包括消费灵石、买卖物品、解散宗门等**写操作**）。
如果 OAuth token 直接复用它，第三方就能替玩家花钱、退宗门。

**防护：按 scope 分级 + 方法级限制。**

| scope | 允许 | 禁止 |
|---|---|---|
| `read` | 所有 `@GET` 端点 | 任何 `@POST`/`@DELETE` |
| `write` | 同上 + 白名单内的写操作 | 破坏性操作（删号、解散宗门、清空背包） |

实现方式：过滤器设置 `requestContext.setProperty("oauthScope", scope)`，
由一个 `OAuthScopeFilter` 在 `@PreMatching` 阶段按 HTTP 方法拦截。

**并且在第一版里，`write` scope 不开放给第三方** —— 只实现 `read`。
理由：读数据是明确需求，写数据会牵动经济系统，风险与需求不成比例。
需要时再逐个开放，每开放一个都写越权测试。

### 4.8 refresh_token 也要一次性轮换

**防护**：每次刷新都发新的 refresh_token，旧的立即失效（rotation）。
如果检测到旧 refresh_token 被重放，说明可能泄露，吊销整条链。

### 4.9 必须能撤销授权

**防护**：玩家要能在某处看到「哪些应用能读我的数据」并一键撤销。
撤销后该用户的所有 OAuth token 立即失效。

---

## 5. 实现清单

| # | 内容 | 文件 |
|---|---|---|
| 1 | 客户端注册表（含 redirect_uri 白名单） | `OAuthClientRegistry`（新） |
| 2 | 授权码存储 + 一次性消费 | `OAuthAuthorizationCodeStore`（新） |
| 3 | 用户型 token 签发（带 uid claim） | `OAuthService` 扩展 |
| 4 | refresh_token 轮换 | `OAuthService` 扩展 |
| 5 | `/authorize` 校验 + 同意页数据 | `OAuthResource` |
| 6 | `/authorize/confirm` 发 code | `OAuthResource` |
| 7 | `/token` 支持 authorization_code | `OAuthResource` |
| 8 | `/revoke` 撤销 | `OAuthResource` |
| 9 | 过滤器识别 OAuth token → 映射用户 | `JwtAuthFilter` 扩展 |
| 10 | scope 方法级限制 | `OAuthScopeFilter`（新） |
| 11 | 用户已授权应用列表 + 撤销 | `AuthResource` / 新端点 |
| 12 | 配置项：client 的 redirect_uri 登记 | `application.yml` |

---

## 6. 配置形态

`application.yml` 需要能登记第三方应用及其回调地址：

```yaml
oauth2:
  enabled: true
  # 授权码流程的客户端登记表
  clients:
    - client_id: demo_web
      client_secret: <随机密钥>
      name: "修仙助手"            # 同意页显示的名字
      redirect_uris:
        - https://demo.example.com/oauth/callback
      scopes: read                # 允许申请的 scope
  # 内置的机器对机器客户端（原 client_credentials，保持不变）
  client_id: xiuxian_oauth_client
  client_secret: xiuxian_oauth_secret_please_change
  scopes: read write
```

现有的 `client_id` / `client_secret` 保持原样，继续只服务 `client_credentials`，
**不允许**它走授权码流程 —— 避免原有配置意外获得读用户数据的能力。

---

## 7. 测试计划

越权测试比功能测试更重要。每条攻击都要有对应的测试。

| 攻击 | 测试 |
|---|---|
| redirect_uri 换成外域 | `redirectUriMustMatchExactly` |
| redirect_uri 用 `app.com.evil.com` 绕前缀匹配 | `redirectUriPrefixTrickRejected` |
| code 重放 | `codeIsSingleUse` |
| code 换给错误的 client | `codeBoundToClient` |
| code 超过 10 分钟 | `expiredCodeRejected` |
| 未同意就发 code | `noCodeWithoutConsent` |
| 用 client_credentials token 读用户数据 | `clientTokenCannotReadUserData` |
| 用 read token 发 POST | `readScopeCannotWrite` |
| refresh_token 重放 | `refreshTokenRotation` |
| 撤销后 token 立即失效 | `revokeInvalidatesTokens` |

---

## 8. 分阶段落地

这个改动不小，建议分两步，每步都可独立验证：

**第一阶段：读能力（本次做）**
- 授权码流程完整走通
- 只开放 `read` scope
- 用户能在后台看到并撤销已授权应用
- 全套越权测试

**第二阶段：写能力（以后再说）**
- 逐个开放写端点，每个都要单独评估
- 需要幂等性、审计日志、频率限制

---

## 9. 需要你确认的几件事

1. **前端同意页**：是放在现有 WebUI 里（Vue 页面），还是由后端直接返回一个极简 HTML 页面？
   后端返回 HTML 更快落地，但样式与现有界面不一致。

2. **第三方应用是谁**：能否给我一个具体的回调地址示例？我据此写配置样例。

3. **是否同意「第一版只开放 read」**：我建议这样，因为写操作会动经济系统。

4. **撤销入口放哪**：玩家后台的「安全设置」页，还是独立的「已授权应用」页？
