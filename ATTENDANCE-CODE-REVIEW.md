# volunteer-attendance 模块代码审查报告

审查范围：`volunteer-backend/volunteer-attendance/**`（8 个 Java 文件 + pom.xml，无 XML mapper、无独立 resources）
关联范围：`volunteer-server` 的 `application*.yml`、`db/migration/V1__baseline_full_schema.sql`、`WebMvcConfig`、`MyBatisPlusConfig`、`AuthWebMvcConfig/AccessControlInterceptor`、`common/GlobalExceptionHandler`

未修改任何文件。未执行构建。

---

## 模块地图（供后续定位）

| 文件 | 关键成员 |
|---|---|
| `volunteer-backend/volunteer-attendance/src/main/java/com/volunteer/attendance/controller/AttendanceController.java` | `/api/attendance/**` 共 18 个端点 |
| `.../controller/DurationController.java` | `POST /api/duration/{id}/approve` |
| `.../service/AttendanceService.java` | `checkIn` / `checkOut` / `checkInByQRCode` / `ByFace` / `ByNFC` / `ByBluetooth` / `applySupplementary` / `approveSupplementary` / `rejectSupplementary` / `approveDuration` / `createViolation` / `releaseRestriction` |
| `.../service/GeoLocationService.java` | `calculateDistance` / `calculateDistanceFromGps` / `parseGps` |
| `.../service/SupplementaryAnalysisService.java` | `analyzeSupplementaryReason`（LLM 评分） |
| `.../entity/Attendance.java`、`.../entity/ViolationRecord.java` | 继承 `BaseEntity`（`@TableId(AUTO)`、`@TableLogic deleted`） |
| `.../mapper/AttendanceMapper.java` | `sumApprovedDurationHours`、`countApprovedActivities`（注解 SQL） |
| `.../event/AttendanceEvent.java` | `CHECKED_IN` / `DURATION_APPROVED` |

---

## P0

### [P0] 定位校验接口 `/api/attendance/location/validate` 恒返回 `isValid=false`、`distance=-1`，是彻底失效的死接口

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\controller\AttendanceController.java:33-40`

```java
33:    @Operation(summary = "验证位置")
34:    @PostMapping("/location/validate")
35:    public Result<Map<String, Object>> validateLocation(
36:            @RequestBody Map<String, Object> params
37:    ) {
38:        String userGps = (String) params.get("userGps");
39:        return Result.success(attendanceService.validateLocation(userGps, null));
40:    }
```

配合 `F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:193-204`

```java
193:    public Map<String, Object> validateLocation(String userGps, String activityGps) {
194:        Map<String, Object> result = new HashMap<>();
195:        double distance = geoLocationService.calculateDistanceFromGps(userGps, activityGps);
196:        boolean isValid = distance >= 0 && distance <= maxDistanceMeters;
```

以及 `.../service/GeoLocationService.java:25-34`

```java
25:    public double calculateDistanceFromGps(String gps1, String gps2) {
26:        try {
27:            double[] coords1 = parseGps(gps1);
28:            double[] coords2 = parseGps(gps2);
...
30:        } catch (Exception e) {
31:            log.warn("Failed to calculate distance from GPS: {} and {}", gps1, gps2, e);
32:            return -1;
```

**触发条件 / 根因**：控制器硬编码 `activityGps = null`（第 39 行）。`parseGps(null)` 在 `GeoLocationService.java:41-44` 抛 `IllegalArgumentException`：

```java
41:    public double[] parseGps(String gps) {
42:        if (gps == null || gps.trim().isEmpty()) {
43:            throw new IllegalArgumentException("GPS coordinates cannot be empty");
44:        }
```

该异常被 `calculateDistanceFromGps` 的 `catch (Exception e)`（`GeoLocationService.java:30-33`）吞掉并返回 `-1`，于是：

- `distance = -1.0`（第 195 行）
- `isValid = (-1 >= 0) && ... = false`（第 196 行，装箱为 `Boolean.FALSE`，可正常返回，**不会抛异常**）

因此该端点**在任何输入下都不可能返回 `isValid=true`**（`activityGps` 恒为 `null`）。需要澄清一处易被误判的点：`validateLocation` 第 200 行写入的是装箱后的 `Boolean.FALSE`，**不是 `null`**，所以第 243/347/490/559/628/697 行的 `boolean isValid = (Boolean) locationValidation.get("isValid");` 在这些路径上被 `if (activityGps != null)` 或 `isValid == false` 提前拦截，**运行期不会抛 NPE / ClassCastException**（此处纠正常见误判）。真正的缺陷是功能恒失效 + 无权限控制，而不是 500。

**影响**：
- 前端位置预检接口恒返回 `distance=-1, isValid=false`，所有依赖它的签到前置流程被永久阻断（业务不可用）；若前端据此禁用签到按钮，则整个定位签到功能不可用。
- 该端点**没有任何权限注解**（不像 `generateQRCode` 有 `@RequiresAccess("organizer")`），任何登录用户可调用；它也不校验活动是否存在、调用者是否报名，返回的 `maxDistance` 会向任意用户暴露全局围栏阈值。
- 若前端忽略 `isValid` 直接调签到，则该接口形同虚设，白跑一次 Haversine 计算。

**最小修复方案**：让该端点接收 `activityId`，由服务端查活动 GPS，而不是传 `null`：

```java
@PostMapping("/location/validate")
public Result<Map<String, Object>> validateLocation(@RequestBody Map<String, Object> params) {
    String userGps = (String) params.get("userGps");
    Long activityId = Long.valueOf(params.get("activityId").toString());
    return Result.success(attendanceService.validateLocation(userGps, activityId));
}
```
并重载服务方法从 `activityMapper.selectById(activityId)` 取 `locationGpsLat/Lng`；若活动未配置 GPS，返回明确的业务错误而不是 `isValid=false`。同时在控制器加 `@RequiresAccess` 或校验报名关系（当前该方法**没有任何权限注解**，任何登录用户可调用）。

**确认程度**：已确认缺陷

---

## P1

### [P1] 地理围栏完全信任客户端上报 GPS，签到半径配置未生效，防作弊缺失

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:234-247`

```java
234:        String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
235:        if (activityGps == null) {
236:            throw new BusinessException("活动未配置位置信息，无法进行GPS签到");
237:        }
238:        
239:        // 验证GPS坐标有效性
240:        validateGpsCoordinates(gps);
241:        
242:        Map<String, Object> locationValidation = validateLocation(gps, activityGps);
```

**触发条件 / 根因**：
1. `gps` 来自 `@RequestBody`（`AttendanceController.java:61`），由客户端自由构造；服务端只做格式/范围校验（`validateGpsCoordinates`，`AttendanceService.java:275-295`），不校验该坐标与签到设备、IP、历史轨迹的关联。客户端只需提交活动坐标即可通过。
2. 距离阈值取自全局配置 `AttendanceService.java:83-84`：

```java
83:    @Value("${attendance.max-distance-meters:500}")
84:    private double maxDistanceMeters;
```
而数据库中**每个活动**都有独立半径 `activity.check_in_radius`（`V1__baseline_full_schema.sql:276`：`check_in_radius SMALLINT DEFAULT 500 COMMENT '签到半径(米)'`），实体也已映射（`volunteer-backend/volunteer-activity/src/main/java/com/volunteer/activity/entity/Activity.java:94`：`private Integer checkInRadius;`）。全仓库检索 `maxDistanceMeters|max-distance-meters|check_in_radius|checkInRadius` 证明：**`checkInRadius` 在 Java 代码中零消费点**，`sys_config` 的 `attendance.check_in_radius`（`V1__baseline_full_schema.sql:1884`）也从未被读取。
3. 二维码/人脸/NFC/蓝牙四类签到把 GPS 校验写成"有则校验"的可选逻辑，客户端直接**不传 GPS** 即可完全跳过围栏：

`AttendanceService.java:485-496`（QR）、`554-565`（Face）、`623-634`（NFC）、`692-703`（Bluetooth），示例：

```java
485:        if (gps != null && !gps.isEmpty()) {
486:            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
487:            if (activityGps != null) {
```

**影响**：任何用户在宿舍即可完成"定位签到"，地理围栏形同虚设；活动组织者配置的每活动签到半径被静默忽略（配置界面能改、后端不用，属于"配置欺骗"）。

**最小修复方案**：
1. 把阈值改为按活动读取，全局值仅作兜底：
```java
double limit = activity.getCheckInRadius() != null ? activity.getCheckInRadius() : maxDistanceMeters;
```
2. 四类可选 GPS 的签到分支改为强制（与 `checkIn` 一致）：
```java
validateGpsCoordinates(gps);
String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
if (activityGps == null) throw new BusinessException("活动未配置位置信息，无法签到");
```
3. 服务端侧补充弱反作弊：记录 `distance` 到已存在但从未写入的 `check_in_distance` / `check_out_distance` 列，并对连续签到做速度合理性检查。（客户端 GPS 无法从根本上防伪造，需在业务上明示该风险并叠加二维码/NFC 等物理凭据的**绑定校验**，见 P1 二维码/NFC 条目。）

**确认程度**：已确认缺陷（第 2、3 点）；第 1 点客户端伪造属设计层面缺陷

---

### [P1] 二维码签到：token 全局一次性（`getAndDelete`），只有第一个签到者能成功；且校验失败即烧毁 token

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:452-521`

```java
452:    @Transactional
453:    public Attendance checkInByQRCode(Long activityId, String qrCodeToken, Long userId, String location, String gps) {
454:        if (isUserRestricted(userId)) {
455:            throw new BusinessException("您当前处于限制状态，无法参与活动");
456:        }
457:        
458:        String key = "qrcode:" + activityId;
459:        
460:        // 原子操作：获取并删除token，避免竞态条件
461:        String storedToken = redisTemplate.opsForValue().getAndDelete(key);
462:        
463:        if (storedToken == null || !storedToken.equals(qrCodeToken)) {
464:            throw new BusinessException("二维码无效或已过期");
465:        }
```

配合 `.../AttendanceService.java:403-427`（生成，`attendanceController.java:77-82` 组织者接口）：

```java
409:        String qrCodeToken = UUID.randomUUID().toString();
410:        String key = "qrcode:" + activityId;
411:        Duration expiresIn = Duration.ofMinutes(5);
412:        redisTemplate.opsForValue().set(key, qrCodeToken, expiresIn);
```

**触发条件 / 根因**：
- Redis key 是 `qrcode:{activityId}`，**每个活动只有一个 token 槽位**，且用 `getAndDelete` 取值即删除。第一位扫码者之后，`storedToken == null`，所有后续签到者收到"二维码无效或已过期"。
- `getAndDelete` 发生在**所有业务校验之前**（第 461 行早于第 467 行 `validateCheckInAllowed`、第 485 行 GPS 校验）。任何校验失败（超距离、未报名、被限制、位置未配置）都会永久烧毁 token，组织者必须重新生成。
- 与 `RateLimitInterceptor`（`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-server\src\main\java\com\volunteer\config\RateLimitInterceptor.java:24`，`MAX_REQUESTS_PER_MINUTE = 5`，按 IP+URI 计数）叠加后，同一 WiFi 出口下的群体签到会被放大为大量失败。

**影响**：二维码签到只能服务一个人，多人活动场景下功能不可用；且一次误操作会作废整场活动的签到码。

**最小修复方案**：签到码语义应为"限时有效、可多人使用、可作废"：
1. 生成时写入 `qrcode:{activityId}` 并在 TTL 内**不删除**；用 `redisTemplate.opsForValue().get(key)` 做等值比较（比较是幂等的，无需删除）。
2. 若确实需要一次性（防转发），改为**每人一次性**：key 用 `qrcode:{activityId}:{userId}`，或在校验通过、DB 插入成功后再删除 token。
3. 顺序调整为：先做 `validateCheckInAllowed` / 报名 / GPS 校验，最后才消费 token。

**确认程度**：已确认缺陷

---

### [P1] 人脸 / NFC / 蓝牙签到没有任何身份凭据校验，等价于无条件签到

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:523-590`、`592-659`、`661-728`

```java
523:    @Transactional
524:    public Attendance checkInByFace(Long activityId, Long userId, String faceData, String location, String gps) {
525:        log.info("人脸识别签到, userId: {}, activityId: {}", userId, activityId);
526:        validateCredential(faceData, "faceData", 4096, null);
527:        
528:        if (isUserRestricted(userId)) {
```

```java
595:        validateCredential(nfcCardId, "nfcCardId", 128, "^[A-Za-z0-9:_-]+$");
```
```java
664:        validateCredential(bluetoothDeviceId, "bluetoothDeviceId", 128, "^[A-Za-z0-9:._-]+$");
```

配合 `.../AttendanceService.java:297-308`

```java
297:    private void validateCredential(String value, String fieldName, int maxLength, String regex) {
298:        if (value == null || value.trim().isEmpty()) {
299:            throw new BusinessException(fieldName + "不能为空");
300:        }
...
305:        if (regex != null && !trimmed.matches(regex)) {
306:            throw new BusinessException(fieldName + "格式无效");
307:        }
```

**触发条件 / 根因**：`validateCredential` 只校验"非空 + 长度 + 字符集正则"，**不与任何服务端存储比对**：
- `faceData` 的 regex 传 `null`（第 526 行），即任意 4096 字符以内的字符串都通过；没有调用任何人脸库/特征比对服务，也没有与该用户已注册人脸比对。
- `nfcCardId`（第 595 行）只做正则校验，不校验该卡是否属于该用户、是否属于该活动、是否已绑定。
- `bluetoothDeviceId`（第 664 行）同理，且**没有任何蓝牙信号/距离验证**。

**影响**：任何已报名用户 POST `{"activityId":1,"faceData":"x"}` 或 `{"activityId":1,"nfcCardId":"a"}` 即可完成签到，且可完全不传 `gps`（见 P1 地理围栏条目）。人脸/NFC/蓝牙三种签到方式与直接调 `/checkin` 无差别，构成签到方式降级绕过。

**最小修复方案**：
1. 为 `faceData` 接入真实比对：与 `sys_user.face_feature`（或独立人脸服务）做 1:1 比对，失败抛业务异常；当前实现应显式标注"占位"并要求组织者开启时才允许该模式。
2. NFC 卡片建立 `user_nfc_card(user_id, card_id, status)` 绑定表，校验 `card_id` 属于当前 `userId` 且已启用。
3. 蓝牙设备改为校验服务端下发的、与活动绑定的设备标识，并要求 `gps` 非空（强制走围栏校验）。

**确认程度**：已确认缺陷（校验逻辑缺失）

---

### [P1] 任一 organizer 可操作任意他人活动的考勤：`@RequiresAccess("organizer")` 只校验角色、不校验归属

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\controller\AttendanceController.java:148-166`

```java
148:    @Operation(summary = "获取活动考勤记录")
149:    @GetMapping("/activity/{activityId}")
150:    @RequiresAccess("organizer")
151:    public Result<PageResult<Attendance>> getActivityAttendances(
...
160:    @Operation(summary = "通过补签")
161:    @PostMapping("/supplementary/{id}/approve")
162:    @RequiresAccess("organizer")
163:    public Result<Void> approveSupplementary(@PathVariable Long id) {
164:        attendanceService.approveSupplementary(id);
165:        return Result.success();
166:    }
```

同类的还有 `AttendanceController.java:168-176`（reject，`@RequiresAccess("organizer")`）与 `AttendanceController.java:77-82`（`generateQRCode`，`@RequiresAccess("organizer")`）。

权限判定实现：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-auth\src\main\java\com\volunteer\auth\interceptor\AccessControlInterceptor.java:134-140`

```java
134:        if ("organizer".equals(normalizedLevel)
135:                && !isAdmin
136:                && !userService.checkIsOrganizer(userId)) {
137:            log.warn("Access denied: user {} requires organizer role", userId);
138:            sendErrorResponse(response, "权限不足");
139:            return false;
140:        }
```

**触发条件 / 根因**：`checkAccessLevel` 只判断"是不是 organizer"，完全没有把 URL 中的 `activityId` / `id` 与当前用户的 `organizerId` 比对。服务层同样没有归属校验：`AttendanceService.approveSupplementary(Long attendanceId)`（`AttendanceService.java:765-781`）只按主键加载记录并检查状态，不校验调用者。

**影响**：
- 横向越权读取：组织者 A 可拉取组织者 B 的活动全部考勤（含姓名/学号关联的用户 ID、GPS 坐标、补签理由、凭证 URL）。
- 横向越权写入：组织者 A 可审批 B 的活动的补签申请（且 `approveSupplementary` 无归属校验，直接置 `APPROVED`）。
- 组织者 A 可为 B 的活动生成签到二维码（`AttendanceController.java:80`），进而把签到码导流到自己控制的活动上。
- 同时 `approveSupplementary` **没有禁止审批自己的申请**：组织者给自己报名并申请补签后，可自行通过。

**最小修复方案**：在服务层入口做归属校验（不要依赖注解）：

```java
private Activity requireOwnedActivity(Long activityId, Long operatorId, boolean isAdmin) {
    Activity a = activityMapper.selectById(activityId);
    if (a == null) throw new BusinessException("活动不存在");
    if (!isAdmin && !Objects.equals(a.getOrganizerId(), operatorId)) {
        throw new BusinessException("无权操作该活动的考勤");
    }
    return a;
}
```
`getActivityAttendances` / `approveSupplementary` / `rejectSupplementary` / `generateQRCode` 均需从 `@RequestAttribute("userId") Long userId` 取操作者并调用该校验；`approveSupplementary` 额外拒绝 `attendance.getUserId().equals(operatorId)` 的自审。更彻底的做法是改用 `requiresPermission` 细粒度权限码 + `activity:attendance:approve` 并结合归属数据权限。

**确认程度**：已确认缺陷

---

## P2

### [P2] 补签审核通过后时长恒为 NULL，志愿时长永远为 0（`status` 被误用为补签状态机）

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:730-781`

```java
752:        Attendance attendance = new Attendance();
753:        attendance.setActivityId(activityId);
754:        attendance.setUserId(userId);
755:        attendance.setIsSupplementary(1);
756:        attendance.setSupplementaryReason(reason);
757:        attendance.setSupplementaryProof(proof);
758:        attendance.setStatus("PENDING");
759:        attendanceMapper.insert(attendance);
```

```java
765:    @Transactional
766:    public void approveSupplementary(Long attendanceId) {
...
773:        if (!"PENDING".equals(attendance.getStatus())) {
774:            throw new BusinessException("补签状态不允许审核");
775:        }
776:        
777:        attendance.setStatus("APPROVED");
778:        attendance.setApprovalTime(LocalDateTime.now());
779:        attendanceMapper.updateById(attendance);
```

**触发条件 / 根因**：
- `applySupplementary`（第 752-759 行）**不设置 `checkInTime` / `checkOutTime` / `durationHours`**。
- `approveSupplementary`（第 777 行）只改状态，**从不计算或写入 `durationHours`**，也没有从活动时长 `activity.getDurationHours()` 回填。
- 而所有时长统计都以 `status='APPROVED' AND duration_hours` 为准。`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\mapper\AttendanceMapper.java:14-17`：

```java
14:    @Select("SELECT COALESCE(SUM(duration_hours), 0) " +
15:            "FROM attendance " +
16:            "WHERE user_id = #{userId} AND status = 'APPROVED' AND deleted = 0")
17:    BigDecimal sumApprovedDurationHours(@Param("userId") Long userId);
```

消费方：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-report\src\main\java\com\volunteer\report\service\CreditService.java:175-181`（`getTotalDuration` → 勋章 `HOURS` 条件）。
- 另一个消费方 `F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-report\src\main\java\com\volunteer\report\service\ReportService.java:55-60`：

```java
55:        for (Attendance attendance : attendances) {
56:            if (attendance.getDurationHours() != null) {
57:                totalDuration = totalDuration.add(attendance.getDurationHours());
58:                activityCount++;
59:            }
60:        }
```

- 同时 `supplementary_status` 字段在实体（`Attendance.java:56`）与表（`V1__baseline_full_schema.sql:509`）中都存在且带索引 `idx_attendance_supplementary`，但**代码从未读写**，补签状态被硬塞进 `status` 字段，与正常签到的 `status`（NORMAL/PENDING_AUDIT）语义冲突。

**影响**：补签审核"通过"后用户志愿时长为 0，证书/学分/勋章统计全部漏算；`ReportService` 的 `activityCount` 也不计入。属于典型"以为生效其实没生效"的静默数据错误。

**最小修复方案**：
```java
// approveSupplementary 中，在置状态前补齐时长
attendance.setCheckInTime(activity.getStartTime());
attendance.setCheckOutTime(activity.getEndTime());
BigDecimal hours = activity.getDurationHours() != null
        ? activity.getDurationHours()
        : BigDecimal.valueOf(Duration.between(activity.getStartTime(), activity.getEndTime()).toSeconds() / 3600.0)
            .setScale(2, RoundingMode.HALF_UP);
attendance.setDurationHours(hours);
attendance.setSupplementaryStatus("APPROVED");
attendance.setStatus("APPROVED");
```
并统一：补签审核态写入 `supplementary_status`，`status` 只表达考勤结果。审核通过后应 `publishAttendanceEvent(attendance, DURATION_APPROVED)` 或在时长入账路径中触发学分/勋章重算。

**确认程度**：已确认缺陷

---

### [P2] 时长审核 `approveDuration` 无上下限、无精度、无正负校验，且并发可重复发放学分

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:802-822`

```java
802:    @Transactional
803:    public void approveDuration(Long attendanceId, BigDecimal adjustedHours) {
...
810:        if (!"PENDING_AUDIT".equals(attendance.getStatus())) {
811:            throw new BusinessException("考勤状态不允许审核");
812:        }
813:        
814:        if (adjustedHours != null) {
815:            attendance.setDurationHours(adjustedHours);
816:        }
817:        attendance.setStatus("APPROVED");
818:        attendance.setApprovalTime(LocalDateTime.now());
819:        attendanceMapper.updateById(attendance);
820:        publishAttendanceEvent(attendance, AttendanceEvent.EventType.DURATION_APPROVED);
```

入口：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\controller\DurationController.java:22-37`

```java
22:    @Operation(summary = "审核时长")
23:    @PostMapping("/{id}/approve")
24:    @RequiresAccess("admin")
25:    public Result<Void> approveDuration(@PathVariable Long id,
26:                                        @RequestParam(required = false) BigDecimal adjustedHours,
27:                                        @RequestBody(required = false) Map<String, Object> body) {
28:        attendanceService.approveDuration(id, adjustedHours != null ? adjustedHours : readDecimal(body, "adjustedHours"));
```

**触发条件 / 根因**：
1. `adjustedHours` 可以是负数、`0`、`99999`、小数位任意（`new BigDecimal("1.23456789")`，`DurationController.java:36`）。第 814-816 行直接落地，无 `>= 0`、无上限、无 `setScale(2)`。
2. 数据库列 `duration_hours DECIMAL(6,2)`（`V1__baseline_full_schema.sql:513`）上限 9999.99；传入更大值会由 MySQL 严格模式报错或被截断。负数会污染 `sumApprovedDurationHours`，`CreditService.getTotalDuration` 用 `setScale(0, DOWN)` 转 int，负值会让勋章进度倒退。
3. 并发/重复：状态检查与更新之间没有乐观锁（`Attendance` 无 `@Version` 字段，`BaseEntity` 也没有），两个并发 admin 请求可同时通过第 810 行检查，各自执行第 819 行并各发一次 `DURATION_APPROVED` 事件 → `AttendanceBusinessEventListener.handleDurationApproved`（`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-report\src\main\java\com\volunteer\report\event\AttendanceBusinessEventListener.java:48-71`）重复调用 `creditService.addCredit` / `updateReliabilityScore`，学分与信誉分双发。

**影响**：管理员误操作或恶意可任意篡改志愿时长；并发审核导致学分重复累加。

**最小修复方案**：
```java
if (adjustedHours != null) {
    if (adjustedHours.signum() < 0) throw new BusinessException("时长不能为负数");
    if (adjustedHours.compareTo(BigDecimal.valueOf(12)) > 0) throw new BusinessException("单次活动时长不得超过12小时");
    attendance.setDurationHours(adjustedHours.setScale(2, RoundingMode.HALF_UP));
}
```
并把状态流转改为条件更新，保证幂等：
```java
int updated = attendanceMapper.update(null, new LambdaUpdateWrapper<Attendance>()
        .eq(Attendance::getId, attendanceId)
        .eq(Attendance::getStatus, "PENDING_AUDIT")
        .set(Attendance::getStatus, "APPROVED")
        .set(Attendance::getDurationHours, hours)
        .set(Attendance::getApprovalTime, LocalDateTime.now()));
if (updated != 1) throw new BusinessException("考勤状态已被处理，请刷新后重试");
```
（或在 `Attendance`/`BaseEntity` 上加 `@Version` 乐观锁字段。）

**确认程度**：已确认缺陷（第 1、2 点）；并发重复发学分需运行验证

---

### [P2] 时间窗口硬编码为 `endTime + 2 小时`，与 `sys_config.attendance.check_in_delay = 60` 不一致；签退窗口完全缺失

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:96-119`

```java
109:        LocalDateTime now = LocalDateTime.now();
110:        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime().minusMinutes(30))) {
111:            throw new BusinessException("活动尚未到签到时间");
112:        }
113:        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime().plusHours(2))) {
114:            throw new BusinessException("活动已超过签到时间");
115:        }
```

对比配置种子数据 `F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-server\src\main\resources\db\migration\V1__baseline_full_schema.sql:1884-1886`：

```sql
1884: ('attendance.check_in_radius', '500', 'number', '签到有效半径(米)', 3, 1),
1885: ('attendance.check_in_advance', '30', 'number', '允许提前签到时间(分钟)', 4, 1),
1886: ('attendance.check_in_delay', '60', 'number', '允许延迟签退时间(分钟)', 5, 1),
```

**触发条件 / 根因**：
- `check_in_advance=30` 与第 110 行的 `minusMinutes(30)` 数值巧合一致，但**不是同源配置**——改 `sys_config` 不生效，改代码不改配置，两侧会静默漂移。
- `check_in_delay=60` 明确表示"允许延迟签退 60 分钟"，代码却写成 `plusHours(2)` = 120 分钟，**允许在活动结束后 2 小时内签到**（真正的迟到者可以在活动已结束 1 小时 59 分时"签到"并立即"签退"制造时长）。
- `checkOut`（`AttendanceService.java:320-394`）**完全没有时间窗口和活动状态校验**：不检查活动是否已开始、是否已归档、是否在允许延迟窗口内。用户可在签到后任意时刻签退（只受 `hours > 12` 约束，见 P3）。

**影响**：配置项成为死配置，运营调整无效；签到窗口比设计值宽一倍，可被用于事后补刷考勤（虽然后续有 `PENDING_AUDIT` 人工审核兜底，但审核队列会被无效数据淹没）。

**最小修复方案**：把两个阈值提为配置并在两处统一消费：
```java
@Value("${attendance.check-in-advance-minutes:30}") private long advanceMinutes;
@Value("${attendance.check-in-delay-minutes:60}") private long delayMinutes;
```
第 110 行用 `minusMinutes(advanceMinutes)`，第 113 行用 `plusMinutes(delayMinutes)`，并在 `checkOut` 开头加同样的窗口与状态校验。若要以 `sys_config` 为准，则注入对应的 `SysConfig` 查询服务读取 `attendance.check_in_advance` / `attendance.check_in_delay`。

**确认程度**：已确认缺陷

---

### [P2] 跨模块异常语义被破坏：GPS/活动不存在在 QR 与 Face 路径上返回 HTTP 500

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:484-496`

```java
484:        // 验证GPS坐标有效性（如果提供了GPS）
485:        if (gps != null && !gps.isEmpty()) {
486:            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
487:            if (activityGps != null) {
488:                validateGpsCoordinates(gps);
489:                Map<String, Object> locationValidation = validateLocation(gps, activityGps);
490:                boolean isValid = (Boolean) locationValidation.get("isValid");
491:                if (!isValid) {
492:                    double distance = (Double) locationValidation.get("distance");
493:                    throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, maxDistanceMeters));
494:                }
495:            }
496:        }
```

同样的强转 `(Double)` 出现在 `AttendanceService.java:245`、`349`、`561`、`630`、`699`。

**触发条件 / 根因**：`validateLocation` 把 `distance` 以 `double` 装箱为 `Double` 放入 `Map<String, Object>`（`AttendanceService.java:198`：`result.put("distance", distance);`），`isValid` 同样装箱为 `Boolean`（第 200 行）。当前调用链中两者形态一致，因此**当前不会 NPE**；但这是一处易碎的隐式契约：一旦 `validateLocation` 改为返回 `BigDecimal`（例如为了精度，见 P3 精度条目）或 `distance` 以 `Integer` 返回，第 245/349/490/559/628/697 行的 `(Double)` 会立即抛 `ClassCastException` → 被 `F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-common\src\main\java\com\volunteer\common\exception\GlobalExceptionHandler.java:145-152` 兜底为 HTTP 500「系统繁忙，请稍后重试」，用户无法得知真实原因。同时 `distance` 在 `calculateDistanceFromGps` 失败时是 `-1`（`GeoLocationService.java:32`），错误提示会变成「您距离活动地点 -1.00 米」。

**影响**：错误信息误导；精度与类型契约无编译期保护。

**最小修复方案**：用类型化的返回值替代 `Map<String, Object>`，例如引入 `record LocationValidation(double distanceMeters, boolean valid, double limitMeters)`，让调用方拿到强类型字段；或至少在 `validateLocation` 内对 `distance < 0` 返回明确的"GPS 解析失败"业务错误，而不是把 `-1` 当作距离参与判断。

**确认程度**：改进建议（当前不可达，但为高风险脆弱点）

---

## P3

### [P3] 并发重复签到的读-判-写竞态（DB 唯一索引兜底，但错误语义未处理）

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:223-232`（`checkIn`；QR/Face/NFC/Bluetooth 为 473-482 / 542-551 / 611-620 / 680-689，代码完全相同）

```java
223:        Attendance existing = attendanceMapper.selectOne(
224:                new LambdaQueryWrapper<Attendance>()
225:                        .eq(Attendance::getActivityId, activityId)
226:                        .eq(Attendance::getUserId, userId)
227:                        .isNull(Attendance::getCheckOutTime)
228:        );
229:        
230:        if (existing != null) {
231:            throw new BusinessException("您已签到，无需重复签到");
232:        }
...
268:        attendanceMapper.insert(attendance);
```

**触发条件 / 根因**：`selectOne` 与 `insert` 之间无锁、无乐观锁；两个并发请求可同时通过第 230 行检查。
**为什么当前没有脏数据**：数据库有唯一索引兜底 —— `F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-server\src\main\resources\db\migration\V1__baseline_full_schema.sql:519`：

```sql
519:    UNIQUE KEY uk_attendance_user_activity (user_id, activity_id),
```

因此第二个 `insert` 会抛 `DuplicateKeyException`（`DataIntegrityViolationException` 子类），被 `GlobalExceptionHandler.java:124-129` 统一转成 HTTP 500「数据操作失败，请稍后重试」。
**残余影响**：并发用户看到 500 而不是"您已签到"；日志噪音。
**最小修复方案**：`catch (DuplicateKeyException e) { throw new BusinessException("您已签到，无需重复签到"); }`，或把去重前置为 `INSERT ... ON DUPLICATE KEY` / 分布式锁。
**确认程度**：已确认缺陷（数据正确性由唯一索引保证，问题在错误语义）

### [P3] 早退判定分母用错：拿 `checkOutTime` 与 `endTime` 比，早于活动结束超 30 分钟即判早退，即使已足额服务

**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:364-370`

```java
364:        // 判断是否早退
365:        boolean isEarly = false;
366:        if (activity.getEndTime() != null) {
367:            Duration earlyDuration = Duration.between(checkOutTime, activity.getEndTime());
368:            isEarly = earlyDuration.toMinutes() > 30;
369:        }
370:        attendance.setIsEarly(isEarly ? 1 : 0);
```

**触发条件 / 根因**：只要签退时间早于 `endTime` 31 分钟以上就标记早退，**不看实际服务时长是否达到活动要求**。加上第 381-385 行的叠加扣减：

```java
381:        // 迟到早退扣减10%时长
382:        boolean isLate = attendance.getIsLate() != null && attendance.getIsLate() == 1;
383:        if (isLate || isEarly) {
384:            hours = hours * 0.9;
385:        }
```

迟到者若在 `endTime` 前签退会被**同时**标记 `isLate=1` 与 `isEarly=1`，但只扣 10%（不是 20%）——两个语义相反的条件共用一个 `||`，扣减逻辑与"迟到且早退应加重"的业务预期不符。
**影响**：勤勉且准时的志愿者（例如实际服务满 2 小时、只是比计划结束早 40 分钟离场）被标记早退并扣 10% 时长。
**最小修复方案**：早退应按"实际时长 / 应服务时长"判定：
```java
BigDecimal expected = activity.getDurationHours();
isEarly = expected != null && expected.signum() > 0
        && attendance.getDurationHours() != null
        && attendance.getDurationHours().compareTo(expected) < 0;
```
并把迟到/早退的扣减写成显式规则（如各 10%，可叠加）。
**确认程度**：已确认缺陷（语义错误）

### [P3] 补签审核的出入口状态不一致（`PENDING` vs `supplementary_status`）
**文件**：`AttendanceService.java:758`（写 `status="PENDING"`）、`773` / `791`（按 `status` 判断）、`Attendance.java:56`（`supplementaryStatus` 字段存在但零引用）；表定义 `V1__baseline_full_schema.sql:509-510` 同时定义了 `supplementary_status` 与允许 `PENDING/APPROVED/REJECTED` 的 `status`。
**影响**：`idx_attendance_supplementary (is_supplementary, supplementary_status)` 索引永远查不到数据；同一张表中 `status` 承担两套状态机，后续任何按 `status` 的统计都会把补签和正常考勤混淆。
**最小修复方案**：补签三态写 `supplementary_status`；签退的 `status` 只保留 `NORMAL/LATE/EARLY/PENDING_AUDIT/APPROVED`，并相应调整 `approveSupplementary`/`rejectSupplementary` 的读取字段与 `approveDuration` 的前置条件（`applySupplementary` 不应写 `PENDING` 到 `status`）。
**确认程度**：已确认缺陷

### [P3] `isUserRestricted` 忽略 `restriction_days`，对 `restriction_end_time = null` 的 ACTIVE 记录判定为"不受限"
**文件**：`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-attendance\src\main\java\com\volunteer\attendance\service\AttendanceService.java:86-94`

```java
86:    public boolean isUserRestricted(Long userId) {
87:        List<ViolationRecord> activeViolations = violationRecordMapper.selectList(
88:                new LambdaQueryWrapper<ViolationRecord>()
89:                        .eq(ViolationRecord::getUserId, userId)
90:                        .eq(ViolationRecord::getStatus, "ACTIVE")
91:                        .gt(ViolationRecord::getRestrictionEndTime, LocalDateTime.now())
92:        );
93:        return !activeViolations.isEmpty();
94:    }
```

配合 `createViolation`（`AttendanceService.java:135-148`）：

```java
143:        violation.setRestrictionType(restrictionType);
144:        violation.setRestrictionEndTime(LocalDateTime.now().plusDays(restrictionDays));
145:        violation.setStatus("ACTIVE");
```

**触发条件 / 根因**：
- SQL 语义 `restriction_end_time > now` 在 `NULL` 上结果为 `UNKNOWN`，因此 `ACTIVE` 且 `restriction_end_time IS NULL` 的记录（表定义 `restriction_end_time DATETIME` 可空，`V1__baseline_full_schema.sql:548`；DDL 另有 `restriction_days SMALLINT DEFAULT 0`）**永远不会命中**，被限制用户可正常签到。
- 控制器 `AttendanceController.java:189` 对 `restrictionDays` 的默认值为 `7`，但**允许传入 0 或负数**：`Integer.parseInt(params.get("restrictionDays").toString())` 无校验 → `plusDays(0)` / `plusDays(-1)` 使限制立即失效。
- 缺少 `EXPIRED` 状态流转：过期记录永远停留在 `ACTIVE`。

**影响**：失信限制可被绕过（属 P1 级安全影响的 P3 实现缺陷）。
**最小修复方案**：
```java
.eq(ViolationRecord::getStatus, "ACTIVE")
.and(w -> w.gt(ViolationRecord::getRestrictionEndTime, LocalDateTime.now())
           .or().isNull(ViolationRecord::getRestrictionEndTime))
```
并在 `createViolation` 校验 `restrictionDays >= 1`（`AttendanceController.java:189` 处同时加 `@Min(1)`）。建议改用 `status IN ('ACTIVE') AND (restriction_end_time IS NULL OR restriction_end_time > NOW())`。
**确认程度**：已确认缺陷

### [P3] 时长精度与取整：`double` 运算后 `BigDecimal.valueOf(...).setScale(2, HALF_UP)`
**文件**：`AttendanceService.java:373-387`
```java
373:        Duration duration = Duration.between(attendance.getCheckInTime(), checkOutTime);
374:        double hours = duration.toSeconds() / 3600.0;
...
387:        attendance.setDurationHours(BigDecimal.valueOf(hours).setScale(2, java.math.RoundingMode.HALF_UP));
```
**根因**：`double` 累加/乘法（`hours * 0.9`，第 384 行）引入二进制浮点误差，靠末位 `HALF_UP` 掩盖；`CreditService.getTotalDuration`（`CreditService.java:180`）用 `setScale(0, RoundingMode.DOWN)` 转 int 截断累计值，与单条 `HALF_UP` 的取整口径不一致（每活动先舍入到 2 位再求和截断，误差随活动数累积）。
**影响**：时长统计与预期存在 ±0.01 小时级偏差，长周期累计可能少算 1 小时而影响勋章阈值。
**最小修复方案**：全程用 `Duration.toSeconds()` 的整数秒计算，最终一次 `BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP)`；扣减改用 `multiply(new BigDecimal("0.9"))`；统计端统一 `HALF_UP` 或统一以分钟存储（表中已有闲置列 `actual_duration_minutes SMALLINT`，`V1__baseline_full_schema.sql:514`）。
**确认程度**：已确认缺陷（口径不一致）；实际偏差需运行验证

### [P3] 分页参数校验注解全部失效（控制器缺少 `@Validated`）
**文件**：`AttendanceController.java:153-154`、`219-220`
```java
153:            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
154:            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize,
```
全仓库检索 `Validated` 仅命中 `RateLimitInterceptor` 的类名，**没有任何 `@Validated` 注解**，因此 `@Min/@Max` 不被执行：`pageSize=-1`、`pageSize=1000000` 均可直达 `new Page<>(pageNum, pageSize)`（`AttendanceService.java:169`、`183`），由 `PaginationInnerInterceptor`（`F:\volunteer\volunteer-platform-v2\volunteer-backend\volunteer-server\src\main\java\com\volunteer\config\MyBatisPlusConfig.java:16`）生成 `LIMIT`。分页插件本身生效（P2 结论：**分页未被绕过**），但缺乏入参约束。
**最小修复方案**：在类上加 `@Validated`（或改用 `@Parameter` 外的 `@Min/@Max` + 方法级校验）。
**确认程度**：已确认缺陷

### [P3] 归档状态检查顺序造成"先消费 token 再报错"（属 P1 二维码条目的同源问题）
**文件**：`AttendanceService.java:467-472`（QR）、`532-540`（Face）、`601-609`（NFC）、`670-678`（Bluetooth）；对照 `checkIn` 的 `213-221`
```java
467:        Activity activity = validateCheckInAllowed(activityId, userId);
...
469:        if (activity == null) {
470:            throw new BusinessException("活动不存在");
471:        }
```
`validateCheckInAllowed`（`AttendanceService.java:96-119`）已保证 `activity != null`，且其状态白名单（第 103-107 行：`RECRUITING/ONGOING/PUBLISHED`）**已经排除 `ARCHIVED`**，因此后续的 `ARCHIVED` 检查（如第 219 行、538 行、607 行、676 行）是**不可达的冗余代码**；第 215-217、469-471、534-536、603-605、672-674 行的 `if (activity == null)` 同样不可达。
**影响**：死代码造成误导，掩盖真实的状态判断只有一处这一事实；且 QR 路径在被否决前已烧毁 token（见 P1）。
**最小修复方案**：删除不可达分支，或把状态判断收敛到 `validateCheckInAllowed` 一处并加注释说明。
**确认程度**：已确认缺陷（死代码）

### [P3] 表列未映射 / 单位口径不一（`check_in_distance`、`check_out_distance`、`supplementary_ai_score`、`actual_duration_minutes`）
**文件**：`Attendance.java:14-65` 未声明上述四列；表定义见 `V1__baseline_full_schema.sql:501-502`、`508`、`514`。
`SupplementaryAnalysisService.analyzeSupplementaryReason` 计算出 `totalScore`（`SupplementaryAnalysisService.java:40-51`）但**从不写回** `supplementary_ai_score`，AI 评分只存在于 HTTP 响应里；签到/签退距离也从不落库，导致事后无法审计围栏判定。
**影响**：反作弊审计链缺失；AI 评分结果不可追溯。
**最小修复方案**：在 `Attendance` 中补充 `checkInDistance` / `checkOutDistance` / `supplementaryAiScore` 字段，并在 `checkIn`/`checkOut` 中写入第 245/349 行的 `distance`；`applySupplementary` 中接收并保存 AI 评分。
**确认程度**：已确认缺陷（字段闲置）

### [P3] `applySupplementary` 存在活动结束时间为 null 时的"永久无法补签"路径
**文件**：`AttendanceService.java:747-750`
```java
747:        // 补签只能在活动结束后申请
748:        if (activity.getEndTime() == null || LocalDateTime.now().isBefore(activity.getEndTime())) {
749:            throw new BusinessException("活动尚未结束，无法申请补签");
750:        }
```
**根因**：`endTime` 为 null（活动未填结束时间）时按第 748 行判定为"未结束"，即**永远不能补签**，且错误提示与真实原因不符。
**最小修复方案**：拆分条件——`endTime == null` 时抛"活动未设置结束时间，无法申请补签"，或对未设置结束时间的活动采用 `startTime + durationHours` 作为结束时刻。
**确认程度**：已确认缺陷（提示误导）

### [P3] 二维码内容无签名、`expireTime` 与真实 TTL 双源
**文件**：`AttendanceService.java:409-432`
```java
429:    private String buildQrCodePayload(Long activityId, String qrCodeToken) {
430:        return "{\"type\":\"ATTENDANCE_CHECKIN\",\"activityId\":" + activityId
431:                + ",\"qrCodeToken\":\"" + qrCodeToken + "\"}";
432:    }
```
payload 用手工字符串拼接 JSON；`UUID` 是服务端生成，当前无注入风险，但一旦 token 来源变化（如加入活动标题/组织者备注）即成注入点。`expireTime`（第 425 行）由应用时钟计算，而真实过期由 Redis TTL 控制，两者可漂移。返回体同时给出 `qrCodeToken`（明文）与图片，任何能看到返回包的人可转发 token 签到（与 P1 一次性 token 相互作用）。
**最小修复方案**：改用 Jackson `ObjectMapper.writeValueAsString(Map)` 构造 payload；`expireTime` 直接基于 `Instant.now().plus(expiresIn)` 并注明与 Redis TTL 同源；考虑对 token 加 HMAC 签名。
**确认程度**：改进建议

---

## 未发现问题的检查项（已核查，未发现缺陷）

1. **Haversine 公式本身正确**：`GeoLocationService.calculateDistance`（`GeoLocationService.java:12-23`）的 `a`/`c` 计算与 `EARTH_RADIUS = 6371.0`（第 10 行）、`* 1000` 转米均正确，`Math.atan2(sqrt(a), sqrt(1-a))` 是数值稳定写法。
2. **阈值配置来源与消费一致**：`attendance.max-distance-meters` 定义于 `application.yml:174-175` 与 `application-dev.yml:98-99`，由 `AttendanceService.java:83` 唯一消费，名称拼写一致，默认值 500 一致。
3. **`@Transactional` 位于真实调用路径**：`checkIn` / `checkOut` / `checkInByQRCode` / `ByFace` / `ByNFC` / `ByBluetooth` / `applySupplementary` / `approveSupplementary` / `rejectSupplementary` / `approveDuration` / `createViolation` / `releaseRestriction` 全部是 **public** 且由控制器经 Spring 代理调用，**无自调用（self-invocation）**、无 `private @Transactional`、类非 `final`；抛出的 `BusinessException extends RuntimeException`（`common/exception/BusinessException.java:6`），默认 `rollbackFor = RuntimeException` 生效，无 checked exception 吞掉回滚的问题。
4. **MyBatis-Plus Mapper 与 XML 一致**：本模块**没有 XML mapper**；`AttendanceMapper` / `ViolationRecordMapper` 仅继承 `BaseMapper` 并附两条注解 `@Select`，namespace/方法名/`@Param` 与 SQL 占位符 `#{userId}` 一致；**无 `${}` 拼接，无 SQL 注入**；两条注解 SQL 均为单表无 JOIN，不存在 `ONLY_FULL_GROUP_BY` 与空集合问题，且已显式带 `deleted = 0`。
5. **分页真实生效**：`PaginationInnerInterceptor(DbType.MYSQL)` 已注册（`volunteer-server/src/main/java/com/volunteer/config/MyBatisPlusConfig.java:13-18`），`selectPage` 路径未被绕过；`@TableLogic deleted`（`common/entity/BaseEntity.java:53`）使逻辑删除条件自动追加。
6. **实体与迁移脚本表结构一致**：`Attendance` 字段（含 `@TableField` 显式列名 `check_in_gps_lat/lng`、`check_out_gps_lat/lng`）与 `V1__baseline_full_schema.sql:486-533` 逐列对应；`ViolationRecord` 与 `V1__baseline_full_schema.sql:537-567` 对应；`status` 取值 `NORMAL` / `PENDING_AUDIT` / `PENDING` / `APPROVED` / `REJECTED` 全部落在 CHECK 约束白名单内（`V1__baseline_full_schema.sql:529`）；`deleted`、`create_time`、`update_time` 由 `MyMetaObjectHandler` 自动填充，与 DDL 默认值不冲突。
7. **`Result` / 全局异常处理一致**：本模块所有端点统一返回 `Result<T>` / `PageResult<T>`；`BusinessException` 由 `GlobalExceptionHandler.java:28-33` 统一转 200 + `code=500` + 业务消息，未发现模块内自定义返回体或自行 `try/catch` 吞异常（唯一 catch 在 `SupplementaryAnalysisService.java:53` 与 `GeoLocationService.java:30`，均有降级逻辑与告警日志）。
8. **无资源泄漏**：`generateQRCodeImage`（`AttendanceService.java:434-450`）使用 `ByteArrayOutputStream`（内存流，无需关闭）；`MatrixToImageWriter.writeToStream` 只写不持有外部连接；无 `Stream`（`java.util.stream`）在方法内开启而未关闭（`AccessControlInterceptor` 的 stream 不在本模块且为终结操作）；无 JDBC 连接手工获取。
9. **缓存未出现共享可变状态**：`AttendanceService` 无实例可变字段（`maxDistanceMeters` 由 `@Value` 在启动时注入后只读）；`StringRedisTemplate` 为线程安全；未发现 `@Cacheable` 误用或非线程安全集合作为字段。
10. **越界/类型转换**：`checkInByFace` / `ByNFC` / `ByBluetooth` 的凭据长度上限（4096 / 128 / 128）已限制；`GPS` 解析在 `AttendanceService.validateGpsCoordinates:275-295` 校验了 `-90..90` / `-180..180` 与 `NumberFormatException`；`AttendanceController` 对 `params.get("activityId")` 的 `toString()` 在字段缺失时会抛 NPE（`AttendanceController.java:48` 等），但被 `GlobalExceptionHandler.java:49-54` 转为 500 且不泄漏细节——属可接受的健壮性边界，未列为缺陷。

---

## 附：需要运行验证的疑点汇总

1. **生产时区一致性**：`application-prod.yml:27` 的 `SPRING_DATASOURCE_URL` 由环境变量注入，未见 `serverTimezone` 强制值，而 `application-dev.yml:20` / `application.yml:47` 为 `serverTimezone=Asia/Shanghai`。`AttendanceService` 全程使用 `LocalDateTime.now()`（JVM/容器时区）与 DB 中 `DATETIME` 比较（`validateCheckInAllowed:109-115`、`checkOut:353-368`、`isUserRestricted:91`）。若容器时区为 UTC 而 `serverTimezone` 为 `Asia/Shanghai`，会出现 8 小时错位，导致签到窗口判定与迟到/早退标记全错。建议在目标环境实际验证 `now` 与 DB `NOW()` 的差值。
2. **`attendance.max-distance-meters` 与 `activity.check_in_radius` 的实际生效值**：需在真实活动（配置了非 500 半径）上验证代码仍使用全局 500 米。
3. **`CreditService` 是否在 `DURATION_APPROVED` 事件路径上重复发放学分**：需并发调用 `POST /api/duration/{id}/approve` 验证。
4. **`applySupplementary` → `approveSupplementary` 后 `duration_hours` 是否为 NULL、用户总时长是否为 0**：可在测试库按 §P2 流程验证。

---

## 附：P0/P1 修复优先级建议

| 顺序 | 条目 | 理由 |
|---|---|---|
| 1 | P0 定位校验接口返回错误 | 阻断前端签到前置流程，一行可修 |
| 2 | P1 二维码 `getAndDelete` | 多人签到功能不可用，改动集中在一个方法 |
| 3 | P1 organizer 越权 | 数据泄露 + 越权审批，需服务层归属校验 |
| 4 | P1 人脸/NFC/蓝牙无凭据校验 | 签到方式可被无条件绕过 |
| 5 | P1 围栏阈值不生效 + GPS 可选 | 反作弊根本短板，需与产品确认可接受的强度 |
| 6 | P2 补签时长恒为 NULL | 静默丢数据，影响学分/证书 |
| 7 | P2 `approveDuration` 无边界 | 时长可被任意篡改 |
| 8 | P2 时间窗口与配置不一致 | 配置治理 + 放宽签到窗口 |
