# 后端接口 API 文档

> 本文档只回答一个问题：**一个 HTTP 请求应该长什么样，服务端会回什么**。
> 数据表与字段见《数据库设计文档》，角色能力边界见《角色功能详细文档》，
> 容器拓扑、端口与启动方式见《项目启动与配置指南》，系统设计见《一体式志愿者服务平台设计方案》。

所有接口清单均由源码注解提取后逐条核对，共 **277 个端点 / 37 个 Controller**。

## 一、通用约定

### 1.1 请求地址

| 场景 | 基础地址 | 说明 |
| --- | --- | --- |
| 通过 Nginx 访问（容器编排） | `http://localhost:8080` | `docker-compose.yml` 把宿主 8080 映射到 backend 容器 8081，Nginx 的 80 端口另有一套入口 |
| 直连后端（本机开发） | `http://localhost:8081` | `server.port=${SERVER_PORT:8081}` |
| 接口前缀 | 每个路径都以 `/api` 开头 | 项目**没有**设置 `server.servlet.context-path`，`/api` 来自各 Controller 类上的 `@RequestMapping` |

### 1.2 认证方式

除下表列出的匿名接口外，所有接口都要求请求头携带 JWT：

```http
Authorization: Bearer <token>
```

- token 由 `POST /api/auth/login` 下发，载荷含 `userId`、`username`（`subject`），签名算法为 HMAC-SHA，密钥取自 `jwt.secret`（环境变量 `JWT_SECRET` 宽松绑定，或本机 `application-local.yml`；见《项目启动与配置指南》§2.6），有效期 `JWT_EXPIRATION`（默认 86400000 毫秒）。
- 过滤器 `JwtAuthenticationFilter` 校验通过后，把 `userId`、`username` 写入请求属性，Controller 用 `@RequestAttribute("userId")` 取用，因此**业务代码里看不到 token 解析**。
- token 异常时的响应不经过统一返回体，是裸 JSON：

| 情况 | HTTP | 响应体 |
| --- | --- | --- |
| token 过期 | 401 | `{"code":401,"message":"token已过期"}` |
| token 无效/签名错误 | 401 | `{"code":401,"message":"token无效"}` |
| 未携带 token 且接口需要登录 | 401 | 由 Spring Security 的 `AuthenticationEntryPoint` 处理 |

**匿名接口白名单**（`SecurityConfig` 中的 `permitAll` 匹配器，共 10 个端点命中）：

| 匹配器 | 命中端点 |
| --- | --- |
| `OPTIONS /**` | 全部预检请求 |
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/register` | 注册 |
| `/api/captcha/**` | 滑块验证码 |
| `GET /api/auth/user/check-student-id` | 学号是否可用 |
| `GET /api/college/list` | 学院列表 |
| `GET /api/activity/page`、`GET /api/activity/list` | 活动列表 |
| `GET /api/activity/{id}`（正则 `^/api/activity/\d+$`） | 活动详情 |
| `/api/actuator/health`、`/api/actuator/health/**`、`/api/actuator/info` | 健康检查 |
| `/doc.html`、`/swagger-resources/**`、`/webjars/**`、`/v3/api-docs/**`、`/swagger-ui/**` | 接口文档 |

> 其余请求一律经过 `anyRequest().authenticated()`。特别注意 `POST /api/auth/refresh` **不在白名单内**，刷新 token 时必须带上即将过期的旧 token。

### 1.3 访问级别

登录只是第一道门，第二道门是 `AccessControlInterceptor`。它读取方法上的注解，优先级为 **`@RequiresPermission` > `@RequiresAccess` > 无注解直接放行**。

| 级别 | 判定条件 | 不满足时的响应 |
| --- | --- | --- |
| `admin` | `checkIsAdmin` 为真 | 403 `Result.error("权限不足")` |
| `organizer` | 管理员或 `checkIsOrganizer` 为真 | 403 `Result.error("权限不足")` |
| `full` | `hasFullAccess`（已绑定学号的完整访问权限）为真 | 403 `Result.error("请先绑定学号以获得完整访问权限")` |
| `visitor`（默认值） | 只要已登录即可 | 403 `Result.error("请先登录")` |
| `@RequiresAccess(requireStudentId = true)` | 需已绑定学号，管理员豁免 | 403 `Result.error("请先绑定学号")` |
| `@RequiresPermission({"a","b"})` | 命中任一权限码即可（OR 语义） | 403 `Result.error("权限不足")` |

当前 277 个端点中，带 `@RequiresAccess` 的共 67 个：admin 44 个、organizer 20 个、visitor 3 个；其余端点只要求登录。

> `@RequiresPermission` 注解已经实现（`com.volunteer.auth.annotation.RequiresPermission`），但**当前代码库中没有任何端点使用它**，权限码体系因此尚未在接口层生效。

### 1.4 统一响应体

除文件流接口（返回 `ResponseEntity<byte[]>`）和直接写 `HttpServletResponse` 的导出接口外，所有接口返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

- `Result<T>`：`code` 为 `Integer`、`message` 为 `String`、`data` 为泛型负载。
- 分页接口的 `data` 是 `PageResult<T>`：`list`、`total`、`pageNum`、`pageSize`、`totalPages`。
- **业务失败也返回 HTTP 200**：`BusinessException` 被全局处理器转成 `code=500`（或业务自定义码）的 `Result`，HTTP 状态码仍是 200。判断成败请看 `code`，不要只看 HTTP 状态码。

### 1.5 异常到 HTTP 状态码的映射

`GlobalExceptionHandler` 对未捕获异常的统一处理：

| 异常 | HTTP | code | message |
| --- | --- | --- | --- |
| `BusinessException` | 200 | 业务自定义（默认 500） | 异常自带文案 |
| `IllegalArgumentException` | 400 | 400 | `参数错误: {原因}` |
| `IllegalStateException` | 400 | 400 | `状态错误: {原因}` |
| `MethodArgumentNotValidException`（`@Valid` 校验失败） | 400 | 400 | `参数校验失败`，`data` 为 `{字段: 提示}` |
| `BindException` | 400 | 400 | `参数绑定失败`，`data` 为 `{字段: 提示}` |
| `MissingServletRequestParameterException` | 400 | 400 | `缺少必需参数: {参数名}` |
| `MethodArgumentTypeMismatchException` | 400 | 400 | `参数类型错误: {参数名}` |
| `MaxUploadSizeExceededException` | 400 | 400 | `文件大小超限，请上传小于50MB的文件` |
| `NoHandlerFoundException` | 404 | 404 | `接口不存在` |
| `HttpRequestMethodNotSupportedException` | 405 | 405 | `不支持的请求方法: {方法}` |
| `OptimisticLockingFailureException` | 409 | 409 | `数据已被其他用户修改，请刷新后重试` |
| `DataAccessException` | 500 | 500 | `数据操作失败，请稍后重试` |
| `TransactionException` | 500 | 500 | `操作失败，事务已回滚` |
| `NullPointerException` | 500 | 500 | `系统内部错误，请稍后重试` |
| `Exception` | 500 | 500 | `系统繁忙，请稍后重试`（堆栈只进服务端日志） |

### 1.6 端点总量

| 方法 | 数量 |
| --- | --- |
| `GET` | 132 |
| `POST` | 115 |
| `PUT` | 19 |
| `DELETE` | 11 |
| **合计** | **277** |

| 访问级别 | 数量 |
| --- | --- |
| 匿名（白名单） | 10 |
| 仅登录 | 200 |
| `admin` | 44 |
| `organizer` | 20 |
| `visitor`（默认） | 3 |
| **合计** | **277** |

## 二、接口清单

按 Maven 模块分组。`来源` 列给出处理方法的精确位置，便于直接跳转代码；`请求参数` 列由方法签名提取，`[注解]` 表示 Spring 的绑定方式。

### 2.1 volunteer-auth —— 认证、用户、角色与学院

`27` 个端点，来自 5 个 Controller。

#### AuthController　`/api/auth`

<sub>`volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/auth/login` | [login](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L8) | 匿名 | `Result<LoginResponse>` | LoginRequest request [@RequestBody] |
| `POST` | `/api/auth/refresh` | [refreshToken](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L29) | 仅登录 | `Result<LoginResponse>` | String authorization [@RequestHeader]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/auth/register` | [register](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L15) | 匿名 | `Result<Map<String, Object>>` | RegisterRequest request [@RequestBody] |
| `POST` | `/api/auth/user/bind-student-id` | [bindStudentId](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L77) | visitor | `Result<Map<String, Object>>` | Long userId [@RequestAttribute]; StudentIdBindRequest request [@RequestBody] |
| `GET` | `/api/auth/user/check-student-id` | [checkStudentId](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L94) | 匿名 | `Result<Map<String, Object>>` | String studentId [@RequestParam] |
| `GET` | `/api/auth/user/info` | [getUserInfo](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L41) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `PUT` | `/api/auth/user/profile` | [updateProfile](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L134) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute]; Map<String, Object> profile [@RequestBody] |
| `GET` | `/api/auth/user/profile/status` | [getProfileStatus](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/AuthController.java#L110) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute] |

#### CaptchaController　`/api/captcha`

<sub>`volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CaptchaController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/captcha` | [generateCaptcha](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CaptchaController.java#L13) | 匿名 | `Result<Map<String, Object>>` | — |
| `GET` | `/api/captcha/slider` | [generateSliderCaptcha](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CaptchaController.java#L7) | 匿名 | `Result<Map<String, Object>>` | — |
| `POST` | `/api/captcha/slider/verify` | [verifySliderCaptcha](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CaptchaController.java#L19) | 匿名 | `Result<Boolean>` | String captchaId [@RequestParam]; Integer sliderX [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/captcha/verify` | [verifyCaptchaLegacy](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CaptchaController.java#L28) | 匿名 | `Result<Boolean>` | String captchaId [@RequestParam]; Integer sliderX [@RequestParam]; Map<String, Object> body [@RequestBody] |

#### CollegeController　`/api/college`

<sub>`volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CollegeController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/college/assign/{userId}/{collegeId}` | [assignCollege](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CollegeController.java#L15) | admin | `Result<Void>` | Long userId [@PathVariable]; Long collegeId [@PathVariable] |
| `GET` | `/api/college/check-bind` | [checkCollegeBind](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CollegeController.java#L22) | 仅登录 | `Result<Boolean>` | HttpServletRequest request |
| `GET` | `/api/college/list` | [getCollegeList](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/CollegeController.java#L8) | 匿名 | `Result<List<SysOrganization>>` | — |

#### RoleController　`/api/role`

<sub>`volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/RoleController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/role` | [createRole](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/RoleController.java#L18) | admin | `Result<SysRole>` | SysRole role [@RequestBody] |
| `GET` | `/api/role/page` | [getRolePage](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/RoleController.java#L8) | admin | `Result<PageResult<SysRole>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String keyword [@RequestParam] |
| `PUT` | `/api/role/{id}/permissions` | [assignPermissions](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/RoleController.java#L25) | admin | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody] |

#### UserController　`/api/user`

<sub>`volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/user` | [createUser](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L27) | admin | `Result<SysUser>` | SysUser user [@RequestBody] |
| `PUT` | `/api/user` | [updateUser](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L34) | admin | `Result<Void>` | SysUser user [@RequestBody] |
| `GET` | `/api/user/page` | [getUserPage](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L8) | admin | `Result<PageResult<SysUser>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String username [@RequestParam]; String realName [@RequestParam] |
| `DELETE` | `/api/user/{id}` | [deleteUser](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L60) | admin | `Result<Void>` | Long id [@PathVariable] |
| `GET` | `/api/user/{id}` | [getUserById](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L20) | admin | `Result<SysUser>` | Long id [@PathVariable] |
| `PUT` | `/api/user/{id}/password` | [resetPassword](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L51) | admin | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody] |
| `PUT` | `/api/user/{id}/status` | [updateUserStatus](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L42) | admin | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/user/{userId}/role/{roleId}` | [assignRole](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L68) | admin | `Result<Void>` | Long userId [@PathVariable]; Long roleId [@PathVariable] |
| `GET` | `/api/user/{userId}/roles` | [getUserRoles](volunteer-backend/volunteer-auth/src/main/java/com/volunteer/auth/controller/UserController.java#L76) | admin | `Result<List<SysRole>>` | Long userId [@PathVariable] |

### 2.2 volunteer-activity —— 活动、报名与组队

`30` 个端点，来自 4 个 Controller。

#### ActivityController　`/api/activity`

<sub>`volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/activity` | [createActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L43) | organizer | `Result<Activity>` | Activity activity [@RequestBody]; SysUser currentUser [@RequestAttribute] |
| `GET` | `/api/activity/audit/page` | [getAuditActivityPage](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L25) | 仅登录 | `Result<PageResult<Activity>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; SysUser currentUser [@RequestAttribute] |
| `GET` | `/api/activity/list` | [getActivityListWithCountdown](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L133) | 匿名 | `Result<PageResult<ActivityListDTO>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String category [@RequestParam]; String keyword [@RequestParam]; String status [@RequestParam]; LocalDateTime startTime [@RequestParam]; LocalDateTime endTime [@RequestParam]; String sortBy [@RequestParam]; String sortOrder [@RequestParam]; String province [@RequestParam]; String city [@RequestParam]; String district [@RequestParam]; String street [@RequestParam]; String timeSlot [@RequestParam]; SysUser currentUser [@RequestAttribute] |
| `GET` | `/api/activity/page` | [getActivityPage](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L7) | 匿名 | `Result<PageResult<Activity>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String category [@RequestParam]; String keyword [@RequestParam]; String status [@RequestParam]; LocalDateTime startTime [@RequestParam]; LocalDateTime endTime [@RequestParam]; String sortBy [@RequestParam]; String sortOrder [@RequestParam]; SysUser currentUser [@RequestAttribute] |
| `POST` | `/api/activity/publish/{id}` | [publishActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L62) | organizer | `Result<Void>` | Long id [@PathVariable]; SysUser currentUser [@RequestAttribute] |
| `GET` | `/api/activity/registrations` | [getUserRegistrations](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L156) | 仅登录 | `Result<PageResult<UserRegistrationDTO>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestAttribute] |
| `DELETE` | `/api/activity/{id}` | [deleteActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L101) | organizer | `Result<Void>` | Long id [@PathVariable]; SysUser currentUser [@RequestAttribute] |
| `GET` | `/api/activity/{id}` | [getActivityById](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L35) | 仅登录 | `Result<Activity>` | Long id [@PathVariable]; SysUser currentUser [@RequestAttribute] |
| `PUT` | `/api/activity/{id}` | [updateActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L51) | organizer | `Result<Void>` | Long id [@PathVariable]; Activity activity [@RequestBody]; SysUser currentUser [@RequestAttribute] |
| `POST` | `/api/activity/{id}/approve` | [approveActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L69) | 仅登录 | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody]; SysUser currentUser [@RequestAttribute] |
| `POST` | `/api/activity/{id}/audit` | [auditActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L87) | 仅登录 | `Result<Void>` | Long id [@PathVariable]; String status [@RequestParam]; String comment [@RequestParam]; Map<String, Object> body [@RequestBody]; SysUser currentUser [@RequestAttribute] |
| `DELETE` | `/api/activity/{id}/register` | [cancelRegistration](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L126) | 仅登录 | `Result<Void>` | Long id [@PathVariable]; Long userId [@RequestAttribute] |
| `POST` | `/api/activity/{id}/register` | [registerActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L109) | (默认 visitor) | `Result<ActivityRegistration>` | Long id [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/activity/{id}/registration` | [getUserRegistration](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L167) | 仅登录 | `Result<UserRegistrationDTO>` | Long id [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/activity/{id}/registrations` | [getActivityRegistrations](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L115) | 仅登录 | `Result<PageResult<ActivityRegistration>>` | Long id [@PathVariable]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam] |
| `POST` | `/api/activity/{id}/reject` | [rejectActivity](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityController.java#L78) | 仅登录 | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody]; SysUser currentUser [@RequestAttribute] |

#### ActivityImportController　`/api/activity`

<sub>`volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/activity/import/log/{logId}/failures` | [downloadFailures](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java#L63) | organizer | `ResponseEntity<byte[]>` | Long logId [@PathVariable] |
| `POST` | `/api/activity/{activityId}/import/confirm` | [confirmImport](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java#L38) | organizer | `Result<ImportResultDTO>` | Long activityId [@PathVariable]; ImportConfirmRequest request [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/activity/{activityId}/import/logs` | [getImportLogs](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java#L51) | organizer | `Result<PageResult<ActivityImportLog>>` | Long activityId [@PathVariable]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam] |
| `POST` | `/api/activity/{activityId}/import/preview` | [previewImport](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java#L25) | organizer | `Result<ImportPreviewDTO>` | Long activityId [@PathVariable]; MultipartFile file [@RequestParam]; Long userId [@RequestAttribute] |
| `GET` | `/api/activity/{activityId}/import/template` | [downloadTemplate](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/ActivityImportController.java#L9) | organizer | `ResponseEntity<byte[]>` | Long activityId [@PathVariable] |

#### RegistrationController　`/api/registration`

<sub>`volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/RegistrationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/registration/my` | [getMyRegistrations](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/RegistrationController.java#L7) | 仅登录 | `Result<PageResult<UserRegistrationDTO>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestAttribute] |
| `POST` | `/api/registration/{id}/approve` | [approveRegistration](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/RegistrationController.java#L19) | organizer | `Result<Void>` | Long id [@PathVariable] |
| `POST` | `/api/registration/{id}/reject` | [rejectRegistration](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/RegistrationController.java#L27) | organizer | `Result<Void>` | Long id [@PathVariable]; String reason [@RequestParam]; Map<String, Object> body [@RequestBody] |

#### TeamController　`/api/team`

<sub>`volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/team` | [createTeam](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L8) | (默认 visitor) | `Result<Team>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/team/activity/{activityId}` | [getActivityTeams](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L41) | 仅登录 | `Result<List<Team>>` | Long activityId [@PathVariable] |
| `POST` | `/api/team/{teamId}/disband` | [disbandTeam](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L34) | 仅登录 | `Result<Void>` | Long teamId [@PathVariable]; Long userId [@RequestAttribute] |
| `POST` | `/api/team/{teamId}/join` | [joinTeam](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L20) | 仅登录 | `Result<Void>` | Long teamId [@PathVariable]; Long userId [@RequestAttribute] |
| `POST` | `/api/team/{teamId}/leave` | [leaveTeam](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L27) | 仅登录 | `Result<Void>` | Long teamId [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/team/{teamId}/members` | [getTeamMembers](volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/controller/TeamController.java#L47) | 仅登录 | `Result<List<TeamMember>>` | Long teamId [@PathVariable] |

### 2.3 volunteer-attendance —— 考勤与时长

`20` 个端点，来自 2 个 Controller。

#### AttendanceController　`/api/attendance`

<sub>`volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/attendance/activity/{activityId}` | [getActivityAttendances](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L124) | organizer | `Result<PageResult<Attendance>>` | Long activityId [@PathVariable]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam] |
| `POST` | `/api/attendance/bluetooth/checkin` | [checkInByBluetooth](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L98) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/checkin` | [checkIn](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L28) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/checkout` | [checkOut](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L40) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/face/checkin` | [checkInByFace](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L72) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/location/validate` | [validateLocation](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L8) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> params [@RequestBody] |
| `POST` | `/api/attendance/nfc/checkin` | [checkInByNFC](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L85) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/qrcode/checkin` | [checkInByQRCode](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L59) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/qrcode/{activityId}` | [generateQRCode](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L53) | organizer | `Result<Map<String, Object>>` | Long activityId [@PathVariable] |
| `GET` | `/api/attendance/restriction/check` | [checkRestriction](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L202) | 仅登录 | `Result<Boolean>` | Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/supplementary` | [applySupplementary](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L111) | 仅登录 | `Result<Attendance>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/supplementary/analyze` | [analyzeSupplementary](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L17) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> params [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/attendance/supplementary/{id}/approve` | [approveSupplementary](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L136) | organizer | `Result<Void>` | Long id [@PathVariable] |
| `POST` | `/api/attendance/supplementary/{id}/reject` | [rejectSupplementary](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L144) | organizer | `Result<Void>` | Long id [@PathVariable]; String reason [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/attendance/violation` | [createViolation](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L154) | admin | `Result<ViolationRecord>` | Map<String, Object> params [@RequestBody] |
| `GET` | `/api/attendance/violation/my` | [getMyViolations](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L184) | 仅登录 | `Result<List<ViolationRecord>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/attendance/violation/page` | [getViolationPage](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L191) | admin | `Result<PageResult<ViolationRecord>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestParam]; String status [@RequestParam] |
| `GET` | `/api/attendance/violation/user/{userId}` | [getUserViolations](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L178) | admin | `Result<List<ViolationRecord>>` | Long userId [@PathVariable] |
| `POST` | `/api/attendance/violation/{violationId}/release` | [releaseRestriction](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java#L170) | admin | `Result<Void>` | Long violationId [@PathVariable] |

#### DurationController　`/api/duration`

<sub>`volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/DurationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/duration/{id}/approve` | [approveDuration](volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/DurationController.java#L8) | admin | `Result<Void>` | Long id [@PathVariable]; BigDecimal adjustedHours [@RequestParam]; Map<String, Object> body [@RequestBody] |

### 2.4 volunteer-report —— 报表、统计与激励体系

`161` 个端点，来自 21 个 Controller。

#### OperationLogController　`/api/audit`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/OperationLogController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/audit/page` | [getLogPage](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/OperationLogController.java#L8) | admin | `Result<PageResult<SysOperationLog>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long operatorId [@RequestParam]; String opType [@RequestParam]; String opModule [@RequestParam] |

#### CommendationController　`/api/commendation`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/commendation` | [createCommendation](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L31) | admin | `Result<Commendation>` | Commendation commendation [@RequestBody] |
| `GET` | `/api/commendation/admin/page` | [getCommendationPageAdmin](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L19) | admin | `Result<PageResult<Commendation>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestParam]; String status [@RequestParam] |
| `GET` | `/api/commendation/page` | [getCommendationPage](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L7) | 仅登录 | `Result<PageResult<Commendation>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String level [@RequestParam]; Long userId [@RequestAttribute] |
| `DELETE` | `/api/commendation/{id}` | [deleteCommendation](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L54) | admin | `Result<Void>` | Long id [@PathVariable] |
| `GET` | `/api/commendation/{id}` | [getCommendationById](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L61) | 仅登录 | `Result<Commendation>` | Long id [@PathVariable] |
| `PUT` | `/api/commendation/{id}/approve` | [approveCommendation](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L38) | admin | `Result<Void>` | Long id [@PathVariable]; Long approverId [@RequestAttribute] |
| `PUT` | `/api/commendation/{id}/reject` | [rejectCommendation](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CommendationController.java#L46) | admin | `Result<Void>` | Long id [@PathVariable]; String remark [@RequestBody] |

#### ConfigController　`/api/config`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ConfigController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/config/page` | [getConfigPage](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ConfigController.java#L8) | admin | `Result<PageResult<SystemConfig>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String keyword [@RequestParam]; String configType [@RequestParam] |
| `PUT` | `/api/config/{key}` | [updateConfig](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ConfigController.java#L19) | admin | `Result<SystemConfig>` | String key [@PathVariable]; SystemConfig payload [@RequestBody] |

#### CreditController　`/api/credit`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/credit/add` | [addCredit](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L118) | admin | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestParam]; Integer amount [@RequestParam]; String source [@RequestParam]; Long sourceId [@RequestParam]; String description [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/credit/checkin` | [checkin](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L40) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `POST` | `/api/credit/consume` | [consumeCredit](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L139) | admin | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestParam]; Integer amount [@RequestParam]; String source [@RequestParam]; Long sourceId [@RequestParam]; String description [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/credit/exchange` | [exchangeProduct](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L90) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long productId [@RequestParam]; Integer quantity [@RequestParam]; String shippingAddress [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `GET` | `/api/credit/medals` | [getMyMedals](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L28) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/credit/orders` | [getOrders](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L109) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute]; String status [@RequestParam] |
| `GET` | `/api/credit/overview` | [getCreditOverview](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L6) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/credit/products` | [getProducts](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L80) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | String category [@RequestParam]; Integer minCredit [@RequestParam]; Integer maxCredit [@RequestParam]; String keyword [@RequestParam] |
| `POST` | `/api/credit/task/claim` | [claimTaskReward](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L67) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long taskId [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `POST` | `/api/credit/task/progress` | [updateTaskProgress](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L50) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long taskId [@RequestParam]; Integer increment [@RequestParam]; Map<String, Object> body [@RequestBody] |
| `GET` | `/api/credit/tasks` | [getDailyTasks](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L34) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/credit/transactions` | [getTransactions](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/CreditController.java#L16) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; String type [@RequestParam]; String startDate [@RequestParam]; String endDate [@RequestParam]; int page [@RequestParam]; int size [@RequestParam] |

#### DashboardController　`/api/dashboard`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DashboardController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/dashboard` | [getDashboardData](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DashboardController.java#L7) | 仅登录 | `Result<Map<String, Object>>` | — |

#### DiagnosisController　`/api/diagnosis`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/diagnosis/dashboard` | [getDashboardSummary](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L117) | 仅登录 | `ResponseEntity<Map<String, Object>>` | — |
| `POST` | `/api/diagnosis/diagnose` | [diagnoseActivity](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L50) | 仅登录 | `ResponseEntity<ActivityHealth>` | Long activityId [@RequestParam] |
| `GET` | `/api/diagnosis/health` | [getActivityHealth](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L56) | 仅登录 | `ResponseEntity<ActivityHealth>` | Long activityId [@RequestParam] |
| `GET` | `/api/diagnosis/health/status` | [getActivitiesByStatus](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L62) | 仅登录 | `ResponseEntity<List<ActivityHealth>>` | String status [@RequestParam] |
| `GET` | `/api/diagnosis/metric` | [getMetrics](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L30) | 仅登录 | `ResponseEntity<Map<String, Double>>` | Long activityId [@RequestParam]; String date [@RequestParam] |
| `POST` | `/api/diagnosis/metric` | [recordMetric](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L6) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam]; String metricType [@RequestParam]; Double value [@RequestParam] |
| `GET` | `/api/diagnosis/metric/history` | [getMetricHistory](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L39) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long activityId [@RequestParam]; String metricType [@RequestParam]; String startDate [@RequestParam]; String endDate [@RequestParam] |
| `POST` | `/api/diagnosis/metric/increment` | [incrementMetric](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L18) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam]; String metricType [@RequestParam]; Double increment [@RequestParam] |
| `POST` | `/api/diagnosis/report/generate` | [generateReport](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L68) | 仅登录 | `ResponseEntity<DiagnosisReport>` | Long activityId [@RequestParam]; String reportType [@RequestParam]; String periodStart [@RequestParam]; String periodEnd [@RequestParam] |
| `GET` | `/api/diagnosis/report/latest` | [getLatestReport](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L80) | 仅登录 | `ResponseEntity<DiagnosisReport>` | Long activityId [@RequestParam] |
| `GET` | `/api/diagnosis/reports` | [getReports](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L89) | 仅登录 | `ResponseEntity<List<DiagnosisReport>>` | Long activityId [@RequestParam]; String reportType [@RequestParam] |
| `POST` | `/api/diagnosis/suggestion/status` | [updateSuggestionStatus](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L105) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long suggestionId [@RequestParam]; String status [@RequestParam] |
| `GET` | `/api/diagnosis/suggestions` | [getSuggestions](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DiagnosisController.java#L97) | 仅登录 | `ResponseEntity<List<ImprovementSuggestion>>` | Long activityId [@RequestParam]; String status [@RequestParam] |

#### DurationDisputeController　`/api/dispute`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DurationDisputeController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/dispute` | [submitDispute](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DurationDisputeController.java#L7) | 仅登录 | `Result<DurationDispute>` | DurationDispute dispute [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/dispute/page` | [getDisputePage](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DurationDisputeController.java#L15) | 仅登录 | `Result<PageResult<DurationDispute>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestAttribute] |
| `GET` | `/api/dispute/{id}` | [getDisputeById](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DurationDisputeController.java#L26) | 仅登录 | `Result<DurationDispute>` | Long id [@PathVariable] |
| `PUT` | `/api/dispute/{id}/review` | [reviewDispute](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/DurationDisputeController.java#L32) | 仅登录 | `Result<Void>` | Long id [@PathVariable]; Map<String, Object> body [@RequestBody]; Long reviewerId [@RequestAttribute] |

#### ExportController　`/api/export`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/export/activity-stats` | [exportActivityStats](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L9) | 仅登录 | `void` | LocalDate startDate [@RequestParam]; LocalDate endDate [@RequestParam]; String format [@RequestParam]; HttpServletResponse response |
| `GET` | `/api/export/csv` | [exportCsv](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L134) | 仅登录 | `void` | String type [@RequestParam]; LocalDate startDate [@RequestParam]; LocalDate endDate [@RequestParam]; HttpServletResponse response |
| `POST` | `/api/export/task` | [createExportTask](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L219) | 仅登录 | `Result<ExportTask>` | ExportRequestDTO request [@RequestBody]; Long userId [@RequestAttribute]; SysUser currentUser [@RequestAttribute] |
| `POST` | `/api/export/task/{taskId}/cancel` | [cancelTask](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L266) | 仅登录 | `Result<Boolean>` | Long taskId [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/export/task/{taskId}/download` | [downloadTaskFile](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L242) | 仅登录 | `ResponseEntity<byte[]>` | Long taskId [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/export/task/{taskId}/progress` | [getTaskProgress](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L230) | 仅登录 | `Result<ExportProgressDTO>` | Long taskId [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/export/tasks` | [getUserTasks](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L259) | 仅登录 | `Result<List<ExportTask>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/export/user-duration` | [exportUserDuration](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExportController.java#L78) | 仅登录 | `void` | LocalDate startDate [@RequestParam]; LocalDate endDate [@RequestParam]; HttpServletResponse response |

#### ExternalDurationController　`/api/external-duration`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/external-duration/admin/records` | [getRecordsAdmin](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L47) | admin | `Result<PageResult<ExternalPlatformDuration>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestParam]; String status [@RequestParam] |
| `POST` | `/api/external-duration/import` | [importDuration](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L13) | 仅登录 | `Result<ExternalPlatformDuration>` | Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/external-duration/import/batch` | [batchImportDuration](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L28) | 仅登录 | `Result<Integer>` | List<ExternalPlatformDuration> durations [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/external-duration/platforms` | [getSupportedPlatforms](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L7) | 仅登录 | `Result<List<String>>` | — |
| `GET` | `/api/external-duration/records` | [getMyRecords](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L36) | 仅登录 | `Result<PageResult<ExternalPlatformDuration>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestAttribute] |
| `PUT` | `/api/external-duration/{durationId}/audit` | [auditDuration](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ExternalDurationController.java#L58) | admin | `Result<Void>` | Long durationId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long auditorId [@RequestAttribute] |

#### MapController　`/api/map`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/MapController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/map/15min-circle` | [getFifteenMinCircle](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/MapController.java#L16) | 仅登录 | `Result<List<Map<String, Object>>>` | BigDecimal lat [@RequestParam]; BigDecimal lng [@RequestParam] |
| `GET` | `/api/map/points` | [getAllMapPoints](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/MapController.java#L24) | 仅登录 | `Result<List<Map<String, Object>>>` | BigDecimal lat [@RequestParam]; BigDecimal lng [@RequestParam]; ") Double radius [@RequestParam] |
| `GET` | `/api/map/stations` | [getNearbyStations](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/MapController.java#L7) | 仅登录 | `Result<List<Map<String, Object>>>` | BigDecimal lat [@RequestParam]; BigDecimal lng [@RequestParam]; ") Double radius [@RequestParam] |

#### RecommendationController　`/api/recommendation`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/recommendation/action` | [logAction](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L79) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long activityId [@RequestParam]; String action [@RequestParam]; String sessionId [@RequestParam] |
| `POST` | `/api/recommendation/activity/apply` | [recordApply](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L101) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam] |
| `POST` | `/api/recommendation/activity/share` | [recordShare](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L110) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam] |
| `POST` | `/api/recommendation/activity/view` | [recordView](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L92) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam] |
| `GET` | `/api/recommendation/hot` | [getHotActivities](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L119) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Integer limit [@RequestParam] |
| `GET` | `/api/recommendation/list` | [getRecommendations](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L62) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute]; Integer limit [@RequestParam] |
| `GET` | `/api/recommendation/preferences` | [getUserPreferences](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L23) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `POST` | `/api/recommendation/preferences/add` | [addPreference](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L38) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; String activityType [@RequestParam]; String category [@RequestParam]; Integer priority [@RequestParam] |
| `POST` | `/api/recommendation/preferences/remove` | [removePreference](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L51) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; String activityType [@RequestParam] |
| `GET` | `/api/recommendation/profile` | [getUserProfile](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L6) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `POST` | `/api/recommendation/profile` | [updateUserProfile](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L12) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Map<String, Object> updates [@RequestBody] |
| `POST` | `/api/recommendation/refresh` | [refreshRecommendations](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L70) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `POST` | `/api/recommendation/score/availability` | [updateAvailabilityScore](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L126) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Integer increment [@RequestParam] |
| `POST` | `/api/recommendation/score/reliability` | [updateReliabilityScore](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/RecommendationController.java#L137) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Integer increment [@RequestParam] |

#### ReportController　`/api/report`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/report/certificate` | [generateCertificate](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L53) | 仅登录 | `ResponseEntity<byte[]>` | Long userId [@RequestAttribute] |
| `GET` | `/api/report/certificate/verify` | [verifyCertificate](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L65) | 仅登录 | `Result<Boolean>` | Long userId [@RequestParam]; String hash [@RequestParam] |
| `GET` | `/api/report/college` | [getAllCollegeStatistics](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L87) | 仅登录 | `Result<List<Map<String, Object>>>` | — |
| `GET` | `/api/report/college/detail` | [getCollegeStatistics](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L93) | 仅登录 | `Result<Map<String, Object>>` | String collegeName [@RequestParam] |
| `GET` | `/api/report/duration` | [getUserDurationReport](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L9) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/report/duration/detail` | [getUserDurationDetails](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L15) | 仅登录 | `Result<List<UserDuration>>` | Long userId [@RequestAttribute]; String status [@RequestParam] |
| `GET` | `/api/report/export/attendance` | [exportAttendanceExcel](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L29) | 仅登录 | `ResponseEntity<byte[]>` | Long userId [@RequestAttribute] |
| `GET` | `/api/report/export/duration` | [exportDurationExcel](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L41) | 仅登录 | `ResponseEntity<byte[]>` | Long userId [@RequestAttribute] |
| `GET` | `/api/report/organization` | [getOrganizationStatistics](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L74) | 仅登录 | `Result<List<OrganizationStatistic>>` | — |
| `GET` | `/api/report/organization/{id}` | [getOrganizationStatistic](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L80) | 仅登录 | `Result<OrganizationStatistic>` | Long id [@PathVariable] |
| `GET` | `/api/report/summary` | [getReportSummary](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReportController.java#L23) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute] |

#### ReviewController　`/api/review`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/review/activity` | [getActivityReviews](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L46) | 仅登录 | `Result<PageResult<ActivityReview>>` | Long activityId [@RequestParam]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam] |
| `POST` | `/api/review/activity/{activityId}` | [createActivityReview](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L7) | 仅登录 | `Result<ActivityReview>` | Long activityId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/review/activity/{activityId}/stats` | [getActivityRatingStats](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L55) | 仅登录 | `Result<Map<String, Object>>` | Long activityId [@PathVariable] |
| `DELETE` | `/api/review/activity/{reviewId}` | [deleteActivityReview](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L37) | 仅登录 | `Result<Void>` | Long reviewId [@PathVariable]; Long userId [@RequestAttribute] |
| `PUT` | `/api/review/activity/{reviewId}` | [updateActivityReview](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L22) | 仅登录 | `Result<ActivityReview>` | Long reviewId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/review/feedback` | [getMyFeedback](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L104) | 仅登录 | `Result<PageResult<Feedback>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestAttribute] |
| `POST` | `/api/review/feedback` | [createFeedback](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L91) | 仅登录 | `Result<Feedback>` | Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/review/feedback/admin` | [getFeedbackAdmin](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L115) | admin | `Result<PageResult<Feedback>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String status [@RequestParam]; Long userId [@RequestParam] |
| `GET` | `/api/review/feedback/{feedbackId}` | [getFeedback](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L125) | 仅登录 | `Result<Feedback>` | Long feedbackId [@PathVariable] |
| `PUT` | `/api/review/feedback/{feedbackId}/close` | [closeFeedback](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L142) | admin | `Result<Void>` | Long feedbackId [@PathVariable] |
| `PUT` | `/api/review/feedback/{feedbackId}/reply` | [replyFeedback](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L132) | admin | `Result<Feedback>` | Long feedbackId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long replyBy [@RequestAttribute] |
| `GET` | `/api/review/volunteer` | [getVolunteerReviews](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L76) | 仅登录 | `Result<PageResult<VolunteerReview>>` | Long reviewedUserId [@RequestParam]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam] |
| `POST` | `/api/review/volunteer/{reviewedUserId}` | [createVolunteerReview](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L61) | 仅登录 | `Result<VolunteerReview>` | Long reviewedUserId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long reviewerUserId [@RequestAttribute] |
| `GET` | `/api/review/volunteer/{userId}/stats` | [getVolunteerRatingStats](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ReviewController.java#L85) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@PathVariable] |

#### ScheduleController　`/api/schedule`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/schedule/add` | [addCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L65) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; CourseSchedule course [@RequestBody] |
| `POST` | `/api/schedule/conflict/check` | [checkConflict](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L159) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; String activityStartTime [@RequestParam]; String activityEndTime [@RequestParam] |
| `POST` | `/api/schedule/conflict/filter` | [filterActivities](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L177) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Map<Long, String[]> activityTimes [@RequestBody] |
| `POST` | `/api/schedule/delete` | [deleteCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L104) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long courseId [@RequestParam] |
| `POST` | `/api/schedule/delete-all` | [deleteAllCourses](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L113) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/schedule/export` | [exportCourses](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L139) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/schedule/grid` | [getScheduleGrid](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L57) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Integer week [@RequestParam] |
| `POST` | `/api/schedule/import` | [importCourses](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L122) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; String importType [@RequestParam]; String fileName [@RequestParam]; List<CourseSchedule> courses [@RequestBody] |
| `GET` | `/api/schedule/list` | [getUserSchedule](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L35) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/schedule/semester` | [getCurrentSemester](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L20) | 仅登录 | `ResponseEntity<Map<String, Object>>` | — |
| `GET` | `/api/schedule/statistics` | [getStatistics](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L199) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/schedule/time-slots` | [getTimeSlots](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L6) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | — |
| `POST` | `/api/schedule/update` | [updateCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L84) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long courseId [@RequestParam]; CourseSchedule updates [@RequestBody] |
| `GET` | `/api/schedule/week-courses` | [getUserScheduleByWeek](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/ScheduleController.java#L205) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute]; Integer week [@RequestParam] |

#### SkillController　`/api/skill`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/skill/apply` | [applyForSkill](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L33) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long skillId [@RequestParam] |
| `POST` | `/api/skill/approve` | [approveSkill](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L46) | admin | `ResponseEntity<Map<String, Object>>` | Long userSkillId [@RequestParam]; Long approverId [@RequestAttribute]; String certificateNo [@RequestParam] |
| `GET` | `/api/skill/categories` | [getSkillsWithCategories](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L6) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | — |
| `GET` | `/api/skill/certification/applications` | [getApplications](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L104) | admin | `ResponseEntity<List<Map<String, Object>>>` | String status [@RequestParam] |
| `POST` | `/api/skill/certification/apply` | [applyForCertification](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L73) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long skillId [@RequestParam]; String targetLevel [@RequestParam]; List<String> proofFiles [@RequestBody]; String description [@RequestParam] |
| `POST` | `/api/skill/certification/review` | [reviewCertification](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L89) | admin | `ResponseEntity<Map<String, Object>>` | Long applicationId [@RequestParam]; Long reviewerId [@RequestAttribute]; String status [@RequestParam]; String comments [@RequestParam] |
| `GET` | `/api/skill/certification/user` | [getUserApplications](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L111) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/skill/match-percentage` | [getSkillMatchPercentage](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L149) | 仅登录 | `ResponseEntity<Map<String, Object>>` | Long userId [@RequestAttribute]; Long activityId [@RequestParam] |
| `POST` | `/api/skill/matching/add` | [addSkillMatching](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L118) | organizer | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam]; Long skillId [@RequestParam]; String requiredLevel [@RequestParam]; Integer importance [@RequestParam] |
| `POST` | `/api/skill/matching/remove` | [removeSkillMatching](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L132) | organizer | `ResponseEntity<Map<String, Object>>` | Long activityId [@RequestParam]; Long skillId [@RequestParam] |
| `GET` | `/api/skill/matching/{activityId}` | [getSkillMatchings](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L143) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long activityId [@PathVariable] |
| `POST` | `/api/skill/reject` | [rejectSkill](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L60) | admin | `ResponseEntity<Map<String, Object>>` | Long userSkillId [@RequestParam]; Long approverId [@RequestAttribute]; String reason [@RequestParam] |
| `GET` | `/api/skill/search` | [searchSkills](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L12) | 仅登录 | `ResponseEntity<List<Skill>>` | String keyword [@RequestParam] |
| `GET` | `/api/skill/user` | [getUserSkills](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L27) | 仅登录 | `ResponseEntity<List<Map<String, Object>>>` | Long userId [@RequestAttribute] |
| `GET` | `/api/skill/{skillId}` | [getSkillById](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/SkillController.java#L18) | 仅登录 | `ResponseEntity<Skill>` | Long skillId [@PathVariable] |

#### StarCertificationController　`/api/star-certification`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/star-certification/apply` | [applyStarCertification](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java#L19) | 仅登录 | `Result<Integer>` | Long userId [@RequestAttribute] |
| `GET` | `/api/star-certification/info` | [getStarCertificationInfo](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java#L13) | 仅登录 | `Result<UserCredit>` | Long userId [@RequestAttribute] |
| `GET` | `/api/star-certification/level` | [getStarLevel](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java#L7) | 仅登录 | `Result<Integer>` | Long userId [@RequestAttribute] |
| `POST` | `/api/star-certification/refresh` | [refreshStarLevel](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java#L25) | 仅登录 | `Result<Integer>` | Long userId [@RequestAttribute] |
| `GET` | `/api/star-certification/requirements/{targetLevel}` | [getStarRequirements](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StarCertificationController.java#L31) | 仅登录 | `Result<Map<String, Object>>` | int targetLevel [@PathVariable] |

#### StatisticsController　`/api/statistics`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/statistics/activity-category` | [getActivityCategory](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java#L25) | 仅登录 | `Result<Map<String, Object>>` | Long organizationId [@RequestParam] |
| `GET` | `/api/statistics/activity-trend` | [getActivityTrend](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java#L7) | 仅登录 | `Result<List<ActivityTrendDTO>>` | LocalDate startDate [@RequestParam]; LocalDate endDate [@RequestParam]; String period [@RequestParam] |
| `GET` | `/api/statistics/checkin-rate` | [getCheckinRate](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java#L32) | 仅登录 | `Result<Map<String, Object>>` | Long activityId [@RequestParam] |
| `GET` | `/api/statistics/service-duration` | [getServiceDuration](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java#L16) | 仅登录 | `Result<List<ServiceDurationDTO>>` | Long organizationId [@RequestParam]; LocalDate startDate [@RequestParam]; LocalDate endDate [@RequestParam] |
| `GET` | `/api/statistics/volunteer-profile` | [getVolunteerProfile](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatisticsController.java#L39) | 仅登录 | `Result<VolunteerProfileDTO>` | Long userId [@RequestAttribute] |

#### StatsController　`/api/stats`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatsController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/stats/global` | [getGlobalStats](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/StatsController.java#L8) | admin | `Result<Map<String, Object>>` | — |

#### TimeBankController　`/api/time-bank`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/time-bank/account` | [getAccount](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L7) | 仅登录 | `Result<TimeBankAccount>` | Long userId [@RequestAttribute] |
| `POST` | `/api/time-bank/deposit` | [deposit](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L58) | admin | `Result<Void>` | Map<String, Object> body [@RequestBody] |
| `GET` | `/api/time-bank/services` | [getServices](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L22) | 仅登录 | `Result<PageResult<TimeBankServiceEntity>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String category [@RequestParam] |
| `POST` | `/api/time-bank/services` | [publishService](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L31) | 仅登录 | `Result<TimeBankServiceEntity>` | TimeBankServiceEntity service [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/time-bank/services/{serviceId}/redeem` | [redeemService](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L39) | 仅登录 | `Result<Void>` | Long serviceId [@PathVariable]; Long userId [@RequestAttribute] |
| `GET` | `/api/time-bank/transactions` | [getTransactions](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L48) | 仅登录 | `Result<PageResult<TimeBankTransaction>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestAttribute] |
| `POST` | `/api/time-bank/transfer` | [transfer](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L13) | 仅登录 | `Result<Void>` | Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/time-bank/withdraw` | [withdraw](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TimeBankController.java#L70) | admin | `Result<Void>` | Map<String, Object> body [@RequestBody] |

#### TodoController　`/api/todos`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/todos` | [getTodoList](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L7) | 仅登录 | `Result<List<UserTodo>>` | Long userId [@RequestAttribute]; String status [@RequestParam] |
| `POST` | `/api/todos` | [createTodo](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L15) | 仅登录 | `Result<UserTodo>` | Long userId [@RequestAttribute]; Map<String, String> request [@RequestBody] |
| `DELETE` | `/api/todos/completed` | [clearCompleted](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L34) | 仅登录 | `Result<Void>` | Long userId [@RequestAttribute] |
| `DELETE` | `/api/todos/{id}` | [deleteTodo](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L41) | 仅登录 | `Result<Void>` | Long userId [@RequestAttribute]; Long id [@PathVariable] |
| `PUT` | `/api/todos/{id}` | [updateTodo](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L24) | 仅登录 | `Result<UserTodo>` | Long userId [@RequestAttribute]; Long id [@PathVariable]; Map<String, String> request [@RequestBody] |
| `PUT` | `/api/todos/{id}/toggle` | [toggleTodo](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TodoController.java#L50) | 仅登录 | `Result<UserTodo>` | Long userId [@RequestAttribute]; Long id [@PathVariable]; Map<String, Boolean> request [@RequestBody] |

#### TrainingController　`/api/training`

<sub>`volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/training/categories` | [getCategories](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L90) | 仅登录 | `Result<List<String>>` | — |
| `GET` | `/api/training/certifications` | [getCertifications](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L81) | 仅登录 | `Result<PageResult<Certification>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestAttribute] |
| `GET` | `/api/training/courses` | [getCourses](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L7) | 仅登录 | `Result<PageResult<TrainingCourse>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String category [@RequestParam]; String level [@RequestParam] |
| `POST` | `/api/training/courses` | [createCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L24) | organizer | `Result<TrainingCourse>` | TrainingCourse course [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/training/courses/{courseId}/complete` | [completeCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L64) | 仅登录 | `Result<TrainingRecord>` | Long courseId [@PathVariable]; Long userId [@RequestAttribute] |
| `PUT` | `/api/training/courses/{courseId}/progress` | [updateProgress](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L55) | 仅登录 | `Result<TrainingRecord>` | Long courseId [@PathVariable]; Map<String, Object> body [@RequestBody]; Long userId [@RequestAttribute] |
| `POST` | `/api/training/courses/{courseId}/start` | [startCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L47) | 仅登录 | `Result<TrainingRecord>` | Long courseId [@PathVariable]; Long userId [@RequestAttribute] |
| `DELETE` | `/api/training/courses/{id}` | [deleteCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L40) | organizer | `Result<Void>` | Long id [@PathVariable] |
| `GET` | `/api/training/courses/{id}` | [getCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L17) | 仅登录 | `Result<TrainingCourse>` | Long id [@PathVariable] |
| `PUT` | `/api/training/courses/{id}` | [updateCourse](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L33) | organizer | `Result<TrainingCourse>` | Long id [@PathVariable]; TrainingCourse course [@RequestBody] |
| `GET` | `/api/training/records` | [getRecords](volunteer-backend/volunteer-report/src/main/java/com/volunteer/report/controller/TrainingController.java#L72) | 仅登录 | `Result<PageResult<TrainingRecord>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; Long userId [@RequestAttribute] |

### 2.5 volunteer-notification —— 通知与公告

`11` 个端点，来自 2 个 Controller。

#### AnnouncementController　`/api/announcement`

<sub>`volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/announcement` | [createAnnouncement](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L30) | admin | `Result<SystemAnnouncement>` | SystemAnnouncement announcement [@RequestBody]; Long userId [@RequestAttribute] |
| `GET` | `/api/announcement/active` | [getActiveAnnouncements](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L17) | 仅登录 | `Result<List<SystemAnnouncement>>` | — |
| `GET` | `/api/announcement/page` | [getAnnouncementPage](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L7) | 仅登录 | `Result<PageResult<SystemAnnouncement>>` | Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam]; String type [@RequestParam] |
| `DELETE` | `/api/announcement/{id}` | [deleteAnnouncement](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L48) | admin | `Result<Void>` | Long id [@PathVariable] |
| `GET` | `/api/announcement/{id}` | [getAnnouncementById](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L23) | 仅登录 | `Result<SystemAnnouncement>` | Long id [@PathVariable] |
| `PUT` | `/api/announcement/{id}` | [updateAnnouncement](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/AnnouncementController.java#L39) | admin | `Result<Void>` | Long id [@PathVariable]; SystemAnnouncement announcement [@RequestBody] |

#### NotificationController　`/api/notification`

<sub>`volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/notification/page` | [getMessagePage](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java#L7) | 仅登录 | `Result<PageResult<UserMessage>>` | Long userId [@RequestAttribute]; Integer pageNum [@RequestParam]; Integer pageSize [@RequestParam] |
| `POST` | `/api/notification/read/all` | [markAllAsRead](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java#L30) | 仅登录 | `Result<Void>` | Long userId [@RequestAttribute] |
| `GET` | `/api/notification/unread/count` | [getUnreadCount](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java#L17) | 仅登录 | `Result<Long>` | Long userId [@RequestAttribute] |
| `DELETE` | `/api/notification/{messageId}` | [deleteMessage](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java#L37) | 仅登录 | `Result<Void>` | Long messageId [@PathVariable]; Long userId [@RequestAttribute] |
| `POST` | `/api/notification/{messageId}/read` | [markAsRead](volunteer-backend/volunteer-notification/src/main/java/com/volunteer/notification/controller/NotificationController.java#L23) | 仅登录 | `Result<Void>` | Long messageId [@PathVariable]; Long userId [@RequestAttribute] |

### 2.6 volunteer-ai —— AI 能力

`24` 个端点，来自 2 个 Controller。

#### AiController　`/api/ai`

<sub>`volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/ai/activity/draft` | [generateActivityDraft](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L43) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/application/analyze` | [analyzeApplication](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L74) | 仅登录 | `Result<Map<String, Object>>` | Map<String, String> request [@RequestBody] |
| `POST` | `/api/ai/application/batch-analyze` | [batchAnalyzeApplications](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L88) | 仅登录 | `Result<Map<String, Object>>` | List<Map<String, String>> applications [@RequestBody] |
| `POST` | `/api/ai/knowledge/add` | [addKnowledge](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L265) | 仅登录 | `Result<Void>` | Map<String, String> request [@RequestBody] |
| `GET` | `/api/ai/knowledge/search` | [searchKnowledge](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L280) | 仅登录 | `Result<List<Map<String, Object>>>` | String query [@RequestParam]; int maxResults [@RequestParam]; double minScore [@RequestParam] |
| `GET` | `/api/ai/knowledge/search/category` | [searchKnowledgeByCategory](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L291) | 仅登录 | `Result<List<Map<String, Object>>>` | String query [@RequestParam]; String category [@RequestParam]; int maxResults [@RequestParam] |
| `POST` | `/api/ai/polish` | [polishContent](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L225) | 仅登录 | `Result<Map<String, Object>>` | Map<String, String> request [@RequestBody] |
| `POST` | `/api/ai/process` | [processContent](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L253) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `GET` | `/api/ai/recommend` | [getRecommendations](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L13) | 仅登录 | `Result<List<Activity>>` | Long userId [@RequestAttribute]; int limit [@RequestParam] |
| `GET` | `/api/ai/recommend/ai` | [getAIRecommendations](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L23) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute]; int limit [@RequestParam] |
| `GET` | `/api/ai/recommend/detail` | [getRecommendationsWithExplanation](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L33) | 仅登录 | `Result<Map<String, Object>>` | Long userId [@RequestAttribute]; int limit [@RequestParam] |
| `POST` | `/api/ai/schedule/conflicts` | [analyzeConflicts](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L206) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/schedule/generate` | [generateSchedule](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L148) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/schedule/match` | [matchVolunteers](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L167) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/schedule/multi-day` | [generateMultiDaySchedule](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L186) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/sensitive` | [detectSensitiveWords](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L242) | 仅登录 | `Result<Map<String, Object>>` | Map<String, String> request [@RequestBody] |
| `POST` | `/api/ai/sentiment` | [analyzeSentiment](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L54) | 仅登录 | `Result<Map<String, Object>>` | Map<String, String> request [@RequestBody] |
| `POST` | `/api/ai/summary/certificate` | [generateCertificateContent](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L129) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/summary/generate` | [generateSummary](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L99) | 仅登录 | `Result<Map<String, Object>>` | Map<String, Object> request [@RequestBody] |
| `POST` | `/api/ai/summary/reflection` | [generateReflection](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/AiController.java#L115) | 仅登录 | `Result<Map<String, Object>>` | Map<String, String> request [@RequestBody] |

#### ChatController　`/api/ai/chat`

<sub>`volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/ChatController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/api/ai/chat` | [chat](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/ChatController.java#L7) | 仅登录 | `Result<String>` | String message [@RequestBody] |
| `GET` | `/api/ai/chat/faqs` | [getFAQs](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/ChatController.java#L42) | 仅登录 | `Result<Map<String, Object>>` | — |
| `GET` | `/api/ai/chat/health` | [healthCheck](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/ChatController.java#L54) | 仅登录 | `Result<String>` | — |
| `POST` | `/api/ai/chat/history` | [chatWithHistory](volunteer-backend/volunteer-ai/src/main/java/com/volunteer/ai/controller/ChatController.java#L22) | 仅登录 | `Result<String>` | Map<String, Object> request [@RequestBody] |

### 2.7 volunteer-server —— 文件与系统

`4` 个端点，来自 1 个 Controller。

#### FileController　`/api/file`

<sub>`volunteer-backend/volunteer-server/src/main/java/com/volunteer/controller/FileController.java`</sub>

| 方法 | 路径 | 处理函数 | 访问级别 | 返回 | 请求参数 |
| --- | --- | --- | --- | --- | --- |
| `DELETE` | `/api/file/delete` | [deleteFile](volunteer-backend/volunteer-server/src/main/java/com/volunteer/controller/FileController.java#L36) | admin | `Result<Void>` | String objectName [@RequestParam] |
| `GET` | `/api/file/download` | [downloadFile](volunteer-backend/volunteer-server/src/main/java/com/volunteer/controller/FileController.java#L22) | 仅登录 | `ResponseEntity<byte[]>` | String objectName [@RequestParam] |
| `POST` | `/api/file/upload` | [uploadFile](volunteer-backend/volunteer-server/src/main/java/com/volunteer/controller/FileController.java#L7) | 仅登录 | `Result<String>` | MultipartFile file [@RequestParam] |
| `GET` | `/api/file/url` | [getFileUrl](volunteer-backend/volunteer-server/src/main/java/com/volunteer/controller/FileController.java#L15) | 仅登录 | `Result<String>` | String objectName [@RequestParam] |

## 三、核心调用链

下面几条链路串起了主要业务，按顺序读可以最快建立对接口体系的整体印象。

### 3.1 登录并拿到可用的身份

```text
GET  /api/captcha/generate          取滑块验证码          匿名
POST /api/captcha/verify            校验滑块              匿名
POST /api/auth/login                登录，返回 token     匿名
GET  /api/auth/user/info            取当前用户信息        仅登录
GET  /api/auth/user/profile/status  取资料完整度          仅登录
PUT  /api/auth/user/profile         补全资料              仅登录
POST /api/auth/user/bind-student-id 绑定学号 → full 权限   visitor
```

登录前的资料不完整状态会挡住 `full` 级别的接口，因此 `bind-student-id` 是志愿者侧的关键一步。

### 3.2 一次活动的完整生命周期

```text
POST /api/activity                    组织者创建（草稿）
POST /api/activity/{id}/submit       提交审核
POST /api/activity/{id}/audit        审核员通过/驳回
POST /api/activity/{id}/publish      发布上线
GET  /api/activity/page              匿名分页浏览
POST /api/registration/apply         志愿者报名
POST /api/registration/{id}/approve  组织者审核报名
POST /api/team                       志愿者组队
POST /api/attendance/check-in         现场签到
POST /api/attendance/check-out        签退，生成时长
POST /api/dispute                     对时长有异议时申诉（/api/dispute 下的接口）
GET  /api/report/**                   个人/组织报表与证书
```

> 活动状态的取值与流转规则以 `activity` 表的 `status` 字段为准，取值清单见《数据库设计文档》的「关键状态取值」一节。

### 3.3 文件上传与下载

```text
POST /api/file/upload     上传，返回对象键（Result<String>）
GET  /api/file/url        按对象键换临时访问地址
GET  /api/file/download   按对象键下载（ResponseEntity<byte[]>，流式）
DELETE /api/file/delete   删除对象（admin）
```

> **已知缺口**：`upload` / `url` / `download` 三个端点没有任何访问级别注解，任何已登录用户都能读写任意对象键；只有 `delete` 声明了 `@RequiresAccess("admin")`。对象键本身即访问凭据，调用方不应把他人对象键暴露给前端。

### 3.4 异步导出

```text
POST /api/export/task                  创建导出任务，立即返回 ExportTask
GET  /api/export/task/{taskId}/progress 轮询进度
GET  /api/export/task/{taskId}/download 完成后下载
POST /api/export/task/{taskId}/cancel   取消
GET  /api/export/tasks                  查我的任务列表
```

同步导出（`/api/export/csv`、`/api/export/activity-stats`、`/api/export/user-duration`）直接写 `HttpServletResponse`，**不走 `Result` 包装**，返回的是文件流。

## 四、在线接口文档

项目集成了 springdoc-openapi，接口定义由源码注解实时生成，**始终比本文档新**：

| 入口 | 地址（本机直连 8081） | 是否白名单 |
| --- | --- | --- |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` | 是 |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` | 是 |
| 健康检查 | `http://localhost:8081/api/actuator/health` | 是 |
| 指标 | `http://localhost:8081/api/actuator/metrics` | 否 |

注意这两个地址的区别：`management.endpoints.web.base-path` 被显式设为 `/api/actuator`，所以 Actuator 带 `/api` 前缀；而 springdoc 的路径没有被改写，`/v3/api-docs` 与 `/swagger-ui/**` **不带** `/api` 前缀。

> `/api/actuator/health` 是匿名可访问的（`SecurityConfig` 白名单，供容器健康检查使用）。为避免向未认证访问者泄露数据库类型、磁盘路径、Redis 版本等内部信息，`management.endpoint.health.show-details` 默认值为 `never`，响应只返回整体 `status`。需要本地排查时临时设置环境变量 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 再启动。

调用示例：

```bash
# 1. 登录拿 token
curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Admin@2026Safe"}'

# 2. 带 token 调用受保护接口
curl -s http://localhost:8081/api/user/page?pageNum=1&pageSize=10 \
  -H "Authorization: Bearer $TOKEN"

# 3. 走 Nginx（容器编排时，后端只暴露在 8080）
curl -s http://localhost:8080/api/actuator/health
```

## 五、维护约定

1. **接口清单不是手写的**。新增或修改端点后，用 `.gen/extract_endpoints.py` 重新提取（脚本只读源码、输出 JSON），再重新渲染本文件，避免文档与代码漂移。
2. 统一响应体、错误码与异常映射写在 `Result`、`PageResult`、`GlobalExceptionHandler` 三处，改动其一必须同步本文件 1.4 / 1.5 两节。
3. 匿名白名单以 `SecurityConfig` 的 `authorizeHttpRequests` 为唯一依据；新增白名单路径时必须同时确认 `AuthWebMvcConfig` 的拦截器排除列表，两份清单的口径不一致会造成实际权限与预期不符。

---

本文档与源码的一致性由提取脚本保证；如发现描述与运行中的 Swagger 不符，以 Swagger 与源码为准。
