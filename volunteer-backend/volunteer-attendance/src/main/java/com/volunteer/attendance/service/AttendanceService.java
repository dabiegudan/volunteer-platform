package com.volunteer.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.entity.AttendanceCredential;
import com.volunteer.attendance.event.AttendanceEvent;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.attendance.entity.ViolationRecord;
import com.volunteer.attendance.mapper.AttendanceCredentialMapper;
import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.attendance.mapper.ViolationRecordMapper;
import com.volunteer.common.exception.BusinessException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {
    
    private void setCheckInGpsCoordinates(Attendance attendance, String gps) {
        if (gps != null && !gps.isEmpty()) {
            try {
                String[] parts = gps.split(",");
                if (parts.length == 2) {
                    attendance.setCheckInGpsLat(new BigDecimal(parts[0].trim()));
                    attendance.setCheckInGpsLng(new BigDecimal(parts[1].trim()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse GPS coordinates: {}", gps);
            }
        }
    }
    
    private void setCheckOutGpsCoordinates(Attendance attendance, String gps) {
        if (gps != null && !gps.isEmpty()) {
            try {
                String[] parts = gps.split(",");
                if (parts.length == 2) {
                    attendance.setCheckOutGpsLat(new BigDecimal(parts[0].trim()));
                    attendance.setCheckOutGpsLng(new BigDecimal(parts[1].trim()));
                }
            } catch (Exception e) {
                log.warn("Failed to parse GPS coordinates: {}", gps);
            }
        }
    }
    
    private final AttendanceMapper attendanceMapper;
    private final ActivityMapper activityMapper;
    private final ActivityRegistrationMapper registrationMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final StringRedisTemplate redisTemplate;
    private final GeoLocationService geoLocationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceCredentialMapper attendanceCredentialMapper;

    /**
     * 二维码令牌「比对」脚本（不改动 Redis 内容）。
     *
     * <h2>为什么不是「比对并删除」</h2>
     * <p>二维码的本质是<b>一个活动一块码、多人共享扫码</b>：现场所有报名者扫的是同一张码。
     * 因此令牌必须在校验通过后<b>继续保持有效</b>，直到自然过期；把令牌删掉等于只允许
     * 第一个扫码的人签到（这正是历史实现 {@code opsForValue().getAndDelete()} 造成的
     * 「只有第一个人能签到」缺陷）。</p>
     *
     * <p>重放风险由别处承担：{@code sys_attendance} 上有
     * {@code uk_attendance_activity_user (activity_id, user_id)} 唯一索引，同一用户无法
     * 重复签到；令牌本身也有 5 分钟 TTL。因此这里只需要「与 Redis 中的值精确比对」，
     * 用 Lua 保证比对与读取在同一次 Redis 调用内完成。</p>
     */
    private static final RedisScript<Long> VERIFY_QR_CODE_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]); if v and v == ARGV[1] then return 1 else return 0 end",
            Long.class);
    
    @Value("${attendance.max-distance-meters:500}")
    private double maxDistanceMeters;

    /**
     * 允许提前签到的分钟数。
     *
     * <p><b>修复的缺陷：</b>历史实现把 {@code minusMinutes(30)} 写死在
     * {@code validateCheckInAllowed} 里，既无法按活动调整，也与 {@code system_config}
     * 中的 {@code attendance.check_in_advance}（种子值 30）没有关联。现改为配置项，
     * 默认值与 {@code system_config} 种子值一致。</p>
     */
    @Value("${attendance.check-in-advance-minutes:30}")
    private long checkInAdvanceMinutes;

    /**
     * 允许延迟签退的分钟数（自活动结束时间起算）。
     *
     * <p><b>修复的缺陷：</b>历史实现把 {@code plusHours(2)} 写死，而
     * {@code system_config.attendance.check_in_delay} 的种子值是 60（分钟）——
     * 实际窗口比配置值的两倍还宽，且改配置无任何效果。现改为配置项，默认 60 分钟。</p>
     */
    @Value("${attendance.check-in-delay-minutes:60}")
    private long checkInDelayMinutes;

    /** 早退判定容差（分钟）：实际服务时长比活动应服务时长少超过该值才算早退。 */
    private static final long EARLY_LEAVE_TOLERANCE_MINUTES = 30L;

    /** 单次考勤可认可的时长上限（小时），与签退时的 12 小时上限保持一致。 */
    private static final BigDecimal MAX_DURATION_HOURS = new BigDecimal("12");
    
    public boolean isUserRestricted(Long userId) {
        List<ViolationRecord> activeViolations = violationRecordMapper.selectList(
                new LambdaQueryWrapper<ViolationRecord>()
                        .eq(ViolationRecord::getUserId, userId)
                        .eq(ViolationRecord::getStatus, "ACTIVE")
                        .gt(ViolationRecord::getRestrictionEndTime, LocalDateTime.now())
        );
        return !activeViolations.isEmpty();
    }

    private Activity validateCheckInAllowed(Long activityId, Long userId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        String status = activity.getStatus();
        if (!ActivityStatusEnum.RECRUITING.name().equals(status)
                && !ActivityStatusEnum.ONGOING.name().equals(status)
                && !ActivityStatusEnum.PUBLISHED.name().equals(status)) {
            throw new BusinessException("活动当前状态不允许签到");
        }

        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null
                && now.isBefore(activity.getStartTime().minusMinutes(checkInAdvanceMinutes))) {
            throw new BusinessException("活动尚未到签到时间");
        }
        if (activity.getEndTime() != null
                && now.isAfter(activity.getEndTime().plusMinutes(checkInDelayMinutes))) {
            throw new BusinessException("活动已超过签到时间");
        }

        requireApprovedRegistration(activityId, userId);
        return activity;
    }

    private ActivityRegistration requireApprovedRegistration(Long activityId, Long userId) {
        ActivityRegistration registration = registrationMapper.selectOne(
                new LambdaQueryWrapper<ActivityRegistration>()
                        .eq(ActivityRegistration::getActivityId, activityId)
                        .eq(ActivityRegistration::getUserId, userId)
                        .eq(ActivityRegistration::getStatus, "APPROVED")
        );
        if (registration == null) {
            throw new BusinessException("仅已通过报名审核的用户可以签到");
        }
        return registration;
    }
    
    @Transactional
    public ViolationRecord createViolation(Long userId, Long activityId, String violationType, 
                                            String violationReason, String restrictionType, 
                                            int restrictionDays) {
        ViolationRecord violation = new ViolationRecord();
        violation.setUserId(userId);
        violation.setActivityId(activityId);
        violation.setViolationType(violationType);
        violation.setViolationReason(violationReason);
        violation.setRestrictionType(restrictionType);
        violation.setRestrictionEndTime(LocalDateTime.now().plusDays(restrictionDays));
        violation.setStatus("ACTIVE");
        violationRecordMapper.insert(violation);
        return violation;
    }
    
    @Transactional
    public void releaseRestriction(Long violationId) {
        ViolationRecord violation = violationRecordMapper.selectById(violationId);
        if (violation != null) {
            violation.setStatus("RELEASED");
            violationRecordMapper.updateById(violation);
        }
    }
    
    public List<ViolationRecord> getUserViolations(Long userId) {
        return violationRecordMapper.selectList(
                new LambdaQueryWrapper<ViolationRecord>()
                        .eq(ViolationRecord::getUserId, userId)
                        .orderByDesc(ViolationRecord::getCreateTime)
        );
    }

    public Page<ViolationRecord> getViolationPage(Integer pageNum, Integer pageSize,
                                                  Long userId, String status) {
        Page<ViolationRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ViolationRecord> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ViolationRecord::getUserId, userId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ViolationRecord::getStatus, status);
        }
        wrapper.orderByDesc(ViolationRecord::getCreateTime);
        return violationRecordMapper.selectPage(page, wrapper);
    }

    public Page<Attendance> getActivityAttendances(Long activityId, Integer pageNum,
                                                   Integer pageSize, String status) {
        Page<Attendance> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attendance::getActivityId, activityId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Attendance::getStatus, status);
        }
        wrapper.orderByDesc(Attendance::getCreateTime);
        return attendanceMapper.selectPage(page, wrapper);
    }
    
    /**
     * 取出本次活动实际生效的签到半径（米）。
     *
     * <h2>修复的缺陷</h2>
     * <p>活动表与活动模板都有 {@code check_in_radius}（{@code Activity.checkInRadius}，
     * DDL 见 {@code V1__baseline_full_schema.sql} 的 {@code activity.check_in_radius}），
     * 但**全仓库没有任何地方读取它** —— 所有距离判定都直接用全局配置
     * {@code attendance.max-distance-meters}（默认 500）。于是"为某个活动单独设置签到半径"
     * 这个功能完全失效，室内小场地活动也必须放宽到 500 米、大型场馆活动又可能不够用。
     *
     * <p>现在优先用活动自身的半径；为空或非正数时回退到全局配置。
     */
    private double effectiveMaxDistanceMeters(Activity activity) {
        if (activity != null && activity.getCheckInRadius() != null && activity.getCheckInRadius() > 0) {
            return activity.getCheckInRadius();
        }
        return maxDistanceMeters;
    }

    /**
     * 带活动上下文的距离校验：使用活动自身的签到半径，并在结果中回传生效半径。
     */
    private Map<String, Object> validateLocation(Activity activity, String userGps, String activityGps) {
        Map<String, Object> result = new HashMap<>();
        double distance = geoLocationService.calculateDistanceFromGps(userGps, activityGps);
        double limit = effectiveMaxDistanceMeters(activity);
        result.put("distance", distance);
        result.put("maxDistance", limit);
        result.put("isValid", distance >= 0 && distance <= limit);
        result.put("unit", "meters");
        return result;
    }

    public Map<String, Object> validateLocation(String userGps, String activityGps) {
        Map<String, Object> result = new HashMap<>();
        double distance = geoLocationService.calculateDistanceFromGps(userGps, activityGps);
        boolean isValid = distance >= 0 && distance <= maxDistanceMeters;
        
        result.put("distance", distance);
        result.put("maxDistance", maxDistanceMeters);
        result.put("isValid", isValid);
        result.put("unit", "meters");
        
        return result;
    }

    /**
     * 按活动真实坐标做位置预校验（前端在签到前调用）。
     *
     * <h2>修复的缺陷</h2>
     * <p>Controller 原先硬编码传 {@code null} 作为活动坐标：{@code parseGps(null)} 抛
     * {@code IllegalArgumentException}，被 {@code GeoLocationService.calculateDistanceFromGps}
     * 吞掉后返回 {@code -1}，于是 {@code isValid = distance >= 0 && ...} <b>恒为 false</b>。
     * 结果是该接口在任何输入下都不可能通过，前端的位置预检被永久阻断（且错误信息
     * 显示「距离 -1.00 米」，具有误导性）。现在改为读取活动自身的经纬度：
     * <ul>
     *   <li>活动未配置坐标 → 明确报「该活动未设置签到位置」，而不是返回 -1；</li>
     *   <li>请求 GPS 非法 → 由 {@link #validateGpsCoordinates(String)} 报出可读原因。</li>
     * </ul>
     *
     * @param activityId 活动 ID
     * @param userGps    客户端上报的 {@code lat,lng}
     * @return 与 {@link #validateLocation(String, String)} 同结构的结果（含 distance/isValid）
     */
    public Map<String, Object> validateLocationForActivity(Long activityId, String userGps) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
        if (activityGps == null) {
            throw new BusinessException("该活动未设置签到位置，无法进行位置校验");
        }

        validateGpsCoordinates(userGps);

        Map<String, Object> result = validateLocation(activity, userGps, activityGps);
        result.put("activityId", activityId);
        result.put("activityGps", activityGps);
        return result;
    }
    
    @Transactional
    public Attendance checkIn(Long activityId, Long userId, String location, String gps) {
        log.info("用户签到, userId: {}, activityId: {}", userId, activityId);
        if (isUserRestricted(userId)) {
            throw new BusinessException("您当前处于限制状态，无法参与活动");
        }
        
        Activity activity = validateCheckInAllowed(activityId, userId);
        ActivityRegistration registration = requireApprovedRegistration(activityId, userId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法签到");
        }
        
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (existing != null) {
            throw new BusinessException("您已签到，无需重复签到");
        }

        String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
        if (activityGps == null) {
            throw new BusinessException("活动未配置位置信息，无法进行GPS签到");
        }
        
        // 验证GPS坐标有效性
        validateGpsCoordinates(gps);
        
        Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
        boolean isValid = (Boolean) locationValidation.get("isValid");
        if (!isValid) {
            double distance = (Double) locationValidation.get("distance");
            throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
        }

        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setRegistrationId(registration.getId());
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setCheckInLocation(location);
        setCheckInGpsCoordinates(attendance, gps);
        attendance.setCheckInMode("LOCATION");
        attendance.setIsSupplementary(0);
        attendance.setStatus("NORMAL");
        
        // 判断是否迟到
        boolean isLate = false;
        if (activity.getStartTime() != null) {
            Duration lateDuration = Duration.between(activity.getStartTime(), attendance.getCheckInTime());
            isLate = lateDuration.toMinutes() > 30;
        }
        attendance.setIsLate(isLate ? 1 : 0);
        
        attendanceMapper.insert(attendance);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.CHECKED_IN);
        
        log.info("用户签到成功, userId: {}, activityId: {}, 迟到: {}", userId, activityId, isLate);
        return attendance;
    }
    
    private void validateGpsCoordinates(String gps) {
        if (gps == null || gps.trim().isEmpty()) {
            throw new BusinessException("GPS坐标不能为空");
        }
        String[] parts = gps.split(",");
        if (parts.length != 2) {
            throw new BusinessException("无效的GPS格式，应为: lat,lon");
        }
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lon = Double.parseDouble(parts[1].trim());
            if (lat < -90 || lat > 90) {
                throw new BusinessException("纬度必须在-90到90之间");
            }
            if (lon < -180 || lon > 180) {
                throw new BusinessException("经度必须在-180到180之间");
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("无效的GPS坐标");
        }
    }

    private void validateCredential(String value, String fieldName, int maxLength, String regex) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(fieldName + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(fieldName + "长度超出限制");
        }
        if (regex != null && !trimmed.matches(regex)) {
            throw new BusinessException(fieldName + "格式无效");
        }
    }

    /**
     * 校验凭据属于当前用户。
     *
     * <h2>修复的缺陷（P0）</h2>
     * <p>人脸 / NFC / 蓝牙签到此前只调用 {@link #validateCredential}（非空 + 长度 + 正则），
     * <b>从不与任何人脸底库、门禁卡或蓝牙信标比对</b>。因此任何已报名用户只要提交
     * {@code {"activityId":1,"faceData":"x"}} 即可签到成功，考勤数据可被任意伪造。</p>
     *
     * <h2>现在的判定规则</h2>
     * <ol>
     *   <li>把提交值规范化后取 SHA-256，确保比对不依赖明文（也不把明文写日志）；</li>
     *   <li>按 {@code (credential_type, credential_hash)} 查有效绑定记录；</li>
     *   <li>记录必须存在且 {@code user_id} 等于当前登录用户，否则拒绝。</li>
     * </ol>
     * <p>结合 {@code uk_credential_type_hash} 唯一索引，「同一凭据登记到两个账号」会在
     * 登记阶段就被数据库拒绝，因此提交他人凭据无法通过第 3 步。</p>
     *
     * @param rawValue 客户端提交的原始凭据
     * @param type     凭据类型，取值见 {@link AttendanceCredential}
     * @param fieldName 用于错误提示的字段名
     */
    private void requireBoundCredential(String rawValue, String type, String fieldName, Long userId) {
        validateCredential(rawValue, fieldName, 4096, null);

        String hash = sha256Hex(rawValue.trim());
        AttendanceCredential credential = attendanceCredentialMapper.selectOne(
                new LambdaQueryWrapper<AttendanceCredential>()
                        .eq(AttendanceCredential::getCredentialType, type)
                        .eq(AttendanceCredential::getCredentialHash, hash)
                        .eq(AttendanceCredential::getStatus, 1)
                        .last("LIMIT 1"));

        if (credential == null) {
            throw new BusinessException("凭据未登记或已停用，请先在「我的-签到凭据」中登记后再签到");
        }
        if (!credential.getUserId().equals(userId)) {
            // 不暴露「该凭据属于他人」这一信息，避免被用来探测凭据是否已登记
            log.warn("凭据归属校验失败: type={}, credentialId={}", type, credential.getId());
            throw new BusinessException("凭据与当前账号不匹配，无法签到");
        }

        credential.setLastUsedTime(LocalDateTime.now());
        attendanceCredentialMapper.updateById(credential);
    }

    /**
     * 登记当前用户的签到凭据（人脸 / NFC / 蓝牙）。
     *
     * <p>凭据规范化后以 SHA-256 入库；{@code uk_credential_type_hash} 唯一索引保证
     * 同一凭据不能被登记到两个账号，因此这是「我的凭据只能我用」的根因约束。
     * 重复登记同一凭据视为幂等成功。</p>
     *
     * @param userId       当前登录用户
     * @param type         凭据类型，取值见 {@link AttendanceCredential}
     * @param rawValue     凭据原文（人脸模板串 / 卡号 / 设备号）
     * @param displayName  展示名，可为空
     * @return 登记结果摘要（不含凭据原文与哈希）
     */
    @Transactional
    public Map<String, Object> registerCredential(Long userId, String type, String rawValue, String displayName) {
        String normalizedType = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!AttendanceCredential.TYPE_FACE.equals(normalizedType)
                && !AttendanceCredential.TYPE_NFC.equals(normalizedType)
                && !AttendanceCredential.TYPE_BLUETOOTH.equals(normalizedType)) {
            throw new BusinessException("不支持的凭据类型，仅支持 FACE / NFC / BLUETOOTH");
        }
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new BusinessException("凭据内容不能为空");
        }
        if (rawValue.trim().length() > 4096) {
            throw new BusinessException("凭据内容长度超出限制");
        }

        String hash = sha256Hex(rawValue.trim());
        AttendanceCredential existing = attendanceCredentialMapper.selectOne(
                new LambdaQueryWrapper<AttendanceCredential>()
                        .eq(AttendanceCredential::getCredentialType, normalizedType)
                        .eq(AttendanceCredential::getCredentialHash, hash)
                        .last("LIMIT 1"));

        if (existing != null) {
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException("该凭据已被其它账号登记，请联系管理员处理");
            }
            if (existing.getStatus() == null || existing.getStatus() != 1) {
                existing.setStatus(1);
                attendanceCredentialMapper.updateById(existing);
            }
        } else {
            AttendanceCredential credential = new AttendanceCredential();
            credential.setUserId(userId);
            credential.setCredentialType(normalizedType);
            credential.setCredentialHash(hash);
            credential.setDisplayName(displayName == null || displayName.isBlank()
                    ? defaultCredentialDisplayName(normalizedType)
                    : displayName.trim());
            credential.setStatus(1);
            attendanceCredentialMapper.insert(credential);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("credentialType", normalizedType);
        result.put("registered", true);
        result.put("message", "凭据登记成功");
        return result;
    }

    private String defaultCredentialDisplayName(String type) {
        switch (type) {
            case AttendanceCredential.TYPE_FACE:
                return "人脸识别";
            case AttendanceCredential.TYPE_NFC:
                return "NFC 卡";
            case AttendanceCredential.TYPE_BLUETOOTH:
                return "蓝牙设备";
            default:
                return type;
        }
    }

    private String sha256Hex(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private void publishAttendanceEvent(Attendance attendance, AttendanceEvent.EventType eventType) {
        eventPublisher.publishEvent(new AttendanceEvent(
                attendance.getId(),
                attendance.getActivityId(),
                attendance.getUserId(),
                attendance.getDurationHours(),
                eventType
        ));
    }
    
    @Transactional
    public Attendance checkOut(Long activityId, Long userId, String location, String gps) {
        log.info("用户签退, userId: {}, activityId: {}", userId, activityId);
        Attendance attendance = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (attendance == null) {
            throw new BusinessException("未找到签到记录");
        }

        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
        if (activityGps == null) {
            throw new BusinessException("活动未配置位置信息，无法进行GPS签退");
        }
        
        validateGpsCoordinates(gps);
        
        Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
        boolean isValid = (Boolean) locationValidation.get("isValid");
        if (!isValid) {
            double distance = (Double) locationValidation.get("distance");
            throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
        }
        
        LocalDateTime checkOutTime = LocalDateTime.now();
        // 校验签退时间必须晚于签到时间
        if (checkOutTime.isBefore(attendance.getCheckInTime())) {
            throw new BusinessException("签退时间不能早于签到时间");
        }
        
        attendance.setCheckOutTime(checkOutTime);
        attendance.setCheckOutLocation(location);
        setCheckOutGpsCoordinates(attendance, gps);
        attendance.setCheckOutMode("LOCATION");
        
        // 判断是否早退
        // 【修复的缺陷】原实现把"签退时刻早于活动结束时刻 30 分钟以上"当作早退：
        //   Duration earlyDuration = Duration.between(checkOutTime, activity.getEndTime());
        //   isEarly = earlyDuration.toMinutes() > 30;
        // 这会误伤所有"活动提前结束 / 提前收尾"的场景 —— 用户已经足额服务满整场活动，
        // 只要活动本身提前 31 分钟结束，签退就被判早退并扣减 10% 时长。
        // 早退的正确语义是"实际服务时长短于活动应服务时长 30 分钟以上"，
        // 因此改为用 (活动起止时长 − 实际服务时长) 判定。
        boolean isEarly = false;
        if (activity.getStartTime() != null && activity.getEndTime() != null) {
            long scheduledSeconds = Duration.between(
                    activity.getStartTime(), activity.getEndTime()).getSeconds();
            if (scheduledSeconds > 0) {
                long shortfallSeconds = scheduledSeconds - Duration.between(
                        attendance.getCheckInTime(), checkOutTime).getSeconds();
                isEarly = shortfallSeconds > EARLY_LEAVE_TOLERANCE_MINUTES * 60L;
            }
        }
        attendance.setIsEarly(isEarly ? 1 : 0);
        
        // 使用秒级精度计算时长
        Duration duration = Duration.between(attendance.getCheckInTime(), checkOutTime);
        double hours = duration.toSeconds() / 3600.0;
        
        // 校验时长是否合理（单次活动不超过12小时）
        if (hours > 12) {
            throw new BusinessException("活动时长异常，请联系管理员核实");
        }
        
        // 迟到早退扣减10%时长
        boolean isLate = attendance.getIsLate() != null && attendance.getIsLate() == 1;
        if (isLate || isEarly) {
            hours = hours * 0.9;
        }
        
        attendance.setDurationHours(BigDecimal.valueOf(hours).setScale(2, java.math.RoundingMode.HALF_UP));
        attendance.setStatus("PENDING_AUDIT");
        
        attendanceMapper.updateById(attendance);
        log.info("用户签退成功, userId: {}, activityId: {}, 时长: {} 小时, 迟到: {}, 早退: {}", 
                userId, activityId, hours, isLate, isEarly);
        return attendance;
    }

    private String formatGps(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return null;
        }
        return lat.toString() + "," + lng.toString();
    }

    public Map<String, Object> generateQRCode(Long activityId) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }

        String qrCodeToken = UUID.randomUUID().toString();
        String key = "qrcode:" + activityId;
        Duration expiresIn = Duration.ofMinutes(5);
        redisTemplate.opsForValue().set(key, qrCodeToken, expiresIn);

        String qrCodePayload = buildQrCodePayload(activityId, qrCodeToken);
        String qrCodeImage = "data:image/png;base64," + Base64.getEncoder()
                .encodeToString(generateQRCodeImage(qrCodePayload, 320, 320));

        Map<String, Object> result = new HashMap<>();
        result.put("activityId", activityId);
        result.put("activityTitle", activity.getTitle());
        result.put("qrCodeToken", qrCodeToken);
        result.put("qrCodePayload", qrCodePayload);
        result.put("qrCodeImage", qrCodeImage);
        result.put("expiresInSeconds", expiresIn.toSeconds());
        result.put("expireTime", LocalDateTime.now().plus(expiresIn));
        return result;
    }

    private String buildQrCodePayload(Long activityId, String qrCodeToken) {
        return "{\"type\":\"ATTENDANCE_CHECKIN\",\"activityId\":" + activityId
                + ",\"qrCodeToken\":\"" + qrCodeToken + "\"}";
    }

    private byte[] generateQRCodeImage(String content, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            log.error("生成签到二维码图片失败", e);
            throw new BusinessException("二维码图片生成失败");
        }
    }
    
    @Transactional
    public Attendance checkInByQRCode(Long activityId, String qrCodeToken, Long userId, String location, String gps) {
        if (isUserRestricted(userId)) {
            throw new BusinessException("您当前处于限制状态，无法参与活动");
        }
        
        String key = "qrcode:" + activityId;
        
        Activity activity = validateCheckInAllowed(activityId, userId);
        ActivityRegistration registration = requireApprovedRegistration(activityId, userId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (existing != null) {
            throw new BusinessException("您已签到");
        }
        
        // 验证GPS坐标有效性（如果提供了GPS）
        if (gps != null && !gps.isEmpty()) {
            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
            if (activityGps != null) {
                validateGpsCoordinates(gps);
                Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
                boolean isValid = (Boolean) locationValidation.get("isValid");
                if (!isValid) {
                    double distance = (Double) locationValidation.get("distance");
                    throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
                }
            }
        }

        // 【关键顺序】先完成全部业务校验，最后才校验二维码令牌。
        //
        // 历史实现使用 redisTemplate.opsForValue().getAndDelete(key)：GETDEL 会把 key 整个
        // 删掉，且原先位于所有校验之前。由此产生两个后果：
        //   1) 【功能恒失效】只有第一个扫码者能签到，后续所有人都会得到
        //      「二维码无效或已过期」——活动签到本质上是多人共享一个二维码；
        //   2) 【令牌被烧毁】任何一个校验失败（未报名/已签到/超范围）都会永久删除令牌，
        //      组织者必须重新生成二维码，且现场所有人都无法再签到。
        // 现在改为在全部校验通过后做「只比对、不删除」的令牌校验：多人可依次扫码，
        // 同一用户的重复签到由 attendance 表唯一索引兜底，令牌本身靠 TTL 自然失效。
        verifyQRCodeToken(key, qrCodeToken);
        
        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setRegistrationId(registration.getId());
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setCheckInLocation(location);
        setCheckInGpsCoordinates(attendance, gps);
        attendance.setCheckInMode("QR_CODE");
        attendance.setIsSupplementary(0);
        attendance.setStatus("NORMAL");
        
        // 判断是否迟到
        boolean isLate = false;
        if (activity.getStartTime() != null) {
            Duration lateDuration = Duration.between(activity.getStartTime(), attendance.getCheckInTime());
            isLate = lateDuration.toMinutes() > 30;
        }
        attendance.setIsLate(isLate ? 1 : 0);
        
        attendanceMapper.insert(attendance);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.CHECKED_IN);
        log.info("二维码签到成功, userId: {}, activityId: {}", userId, activityId);
        return attendance;
    }

    /**
     * 校验二维码令牌是否与 Redis 中当前值一致。
     *
     * <p>只比对、不删除：二维码为「一活动一码、多人共享」，删除会导致只有第一个扫码者
     * 能签到。重复签到由 {@code uk_attendance_activity_user} 唯一索引拦截。</p>
     *
     * @throws BusinessException 令牌为空、不匹配、已过期，或 Redis 不可用
     */
    private void verifyQRCodeToken(String key, String qrCodeToken) {
        if (qrCodeToken == null || qrCodeToken.isBlank()) {
            throw new BusinessException("二维码令牌不能为空");
        }
        Long matched;
        try {
            matched = redisTemplate.execute(
                    VERIFY_QR_CODE_SCRIPT,
                    java.util.Collections.singletonList(key),
                    qrCodeToken);
        } catch (RuntimeException e) {
            log.error("二维码令牌校验失败（Redis 不可用）, key={}", key, e);
            throw new BusinessException("签到服务暂时不可用，请稍后重试");
        }
        if (matched == null || matched == 0L) {
            throw new BusinessException("二维码无效或已过期");
        }
    }

    @Transactional
    public Attendance checkInByFace(Long activityId, Long userId, String faceData, String location, String gps) {
        log.info("人脸识别签到, userId: {}, activityId: {}", userId, activityId);
        // 【P0 修复】必须与已登记的人脸凭据比对，且该凭据属于当前用户；
        // 历史上这里只做非空/长度校验，任意字符串都能签到成功。
        requireBoundCredential(faceData, AttendanceCredential.TYPE_FACE, "faceData", userId);
        
        if (isUserRestricted(userId)) {
            throw new BusinessException("您当前处于限制状态，无法参与活动");
        }
        
        Activity activity = validateCheckInAllowed(activityId, userId);
        ActivityRegistration registration = requireApprovedRegistration(activityId, userId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法签到");
        }
        
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (existing != null) {
            throw new BusinessException("您已签到，无需重复签到");
        }
        
        // 验证GPS坐标有效性（如果提供了GPS）
        if (gps != null && !gps.isEmpty()) {
            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
            if (activityGps != null) {
                validateGpsCoordinates(gps);
                Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
                boolean isValid = (Boolean) locationValidation.get("isValid");
                if (!isValid) {
                    double distance = (Double) locationValidation.get("distance");
                    throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
                }
            }
        }
        
        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setRegistrationId(registration.getId());
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setCheckInLocation(location);
        setCheckInGpsCoordinates(attendance, gps);
        attendance.setCheckInMode("FACE_RECOGNITION");
        attendance.setIsSupplementary(0);
        attendance.setStatus("NORMAL");
        
        // 判断是否迟到
        boolean isLate = false;
        if (activity.getStartTime() != null) {
            Duration lateDuration = Duration.between(activity.getStartTime(), attendance.getCheckInTime());
            isLate = lateDuration.toMinutes() > 30;
        }
        attendance.setIsLate(isLate ? 1 : 0);
        
        attendanceMapper.insert(attendance);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.CHECKED_IN);
        log.info("人脸识别签到成功, userId: {}, activityId: {}", userId, activityId);
        return attendance;
    }

    @Transactional
    public Attendance checkInByNFC(Long activityId, Long userId, String nfcCardId, String location, String gps) {
        log.info("NFC签到, userId: {}, activityId: {}, nfcCardId: {}", userId, activityId, nfcCardId);
        // 【P0 修复】卡号必须已登记且归属当前用户
        requireBoundCredential(nfcCardId, AttendanceCredential.TYPE_NFC, "nfcCardId", userId);
        validateCredential(nfcCardId, "nfcCardId", 128, "^[A-Za-z0-9:_-]+$");
        
        if (isUserRestricted(userId)) {
            throw new BusinessException("您当前处于限制状态，无法参与活动");
        }
        
        Activity activity = validateCheckInAllowed(activityId, userId);
        ActivityRegistration registration = requireApprovedRegistration(activityId, userId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法签到");
        }
        
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (existing != null) {
            throw new BusinessException("您已签到，无需重复签到");
        }
        
        // 验证GPS坐标有效性（如果提供了GPS）
        if (gps != null && !gps.isEmpty()) {
            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
            if (activityGps != null) {
                validateGpsCoordinates(gps);
                Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
                boolean isValid = (Boolean) locationValidation.get("isValid");
                if (!isValid) {
                    double distance = (Double) locationValidation.get("distance");
                    throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
                }
            }
        }
        
        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setRegistrationId(registration.getId());
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setCheckInLocation(location);
        setCheckInGpsCoordinates(attendance, gps);
        attendance.setCheckInMode("NFC");
        attendance.setIsSupplementary(0);
        attendance.setStatus("NORMAL");
        
        // 判断是否迟到
        boolean isLate = false;
        if (activity.getStartTime() != null) {
            Duration lateDuration = Duration.between(activity.getStartTime(), attendance.getCheckInTime());
            isLate = lateDuration.toMinutes() > 30;
        }
        attendance.setIsLate(isLate ? 1 : 0);
        
        attendanceMapper.insert(attendance);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.CHECKED_IN);
        log.info("NFC签到成功, userId: {}, activityId: {}", userId, activityId);
        return attendance;
    }

    @Transactional
    public Attendance checkInByBluetooth(Long activityId, Long userId, String bluetoothDeviceId, String location, String gps) {
        log.info("蓝牙签到, userId: {}, activityId: {}, deviceId: {}", userId, activityId, bluetoothDeviceId);
        // 【P0 修复】设备号必须已登记且归属当前用户
        requireBoundCredential(bluetoothDeviceId, AttendanceCredential.TYPE_BLUETOOTH, "bluetoothDeviceId", userId);
        validateCredential(bluetoothDeviceId, "bluetoothDeviceId", 128, "^[A-Za-z0-9:._-]+$");
        
        if (isUserRestricted(userId)) {
            throw new BusinessException("您当前处于限制状态，无法参与活动");
        }
        
        Activity activity = validateCheckInAllowed(activityId, userId);
        ActivityRegistration registration = requireApprovedRegistration(activityId, userId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        if (ActivityStatusEnum.ARCHIVED.name().equals(activity.getStatus())) {
            throw new BusinessException("活动已归档，无法签到");
        }
        
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
                        .isNull(Attendance::getCheckOutTime)
        );
        
        if (existing != null) {
            throw new BusinessException("您已签到，无需重复签到");
        }
        
        // 验证GPS坐标有效性（如果提供了GPS）
        if (gps != null && !gps.isEmpty()) {
            String activityGps = formatGps(activity.getLocationGpsLat(), activity.getLocationGpsLng());
            if (activityGps != null) {
                validateGpsCoordinates(gps);
                Map<String, Object> locationValidation = validateLocation(activity, gps, activityGps);
                boolean isValid = (Boolean) locationValidation.get("isValid");
                if (!isValid) {
                    double distance = (Double) locationValidation.get("distance");
                    throw new BusinessException(String.format("您距离活动地点 %.2f 米，超出允许范围 %.0f 米", distance, effectiveMaxDistanceMeters(activity)));
                }
            }
        }
        
        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setRegistrationId(registration.getId());
        attendance.setCheckInTime(LocalDateTime.now());
        attendance.setCheckInLocation(location);
        setCheckInGpsCoordinates(attendance, gps);
        attendance.setCheckInMode("BLUETOOTH");
        attendance.setIsSupplementary(0);
        attendance.setStatus("NORMAL");
        
        // 判断是否迟到
        boolean isLate = false;
        if (activity.getStartTime() != null) {
            Duration lateDuration = Duration.between(activity.getStartTime(), attendance.getCheckInTime());
            isLate = lateDuration.toMinutes() > 30;
        }
        attendance.setIsLate(isLate ? 1 : 0);
        
        attendanceMapper.insert(attendance);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.CHECKED_IN);
        log.info("蓝牙签到成功, userId: {}, activityId: {}", userId, activityId);
        return attendance;
    }
    
    @Transactional
    public Attendance applySupplementary(Long activityId, Long userId, String reason, String proof) {
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getActivityId, activityId)
                        .eq(Attendance::getUserId, userId)
        );
        
        if (existing != null) {
            throw new BusinessException("已存在考勤记录，无法重复申请");
        }
        
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        
        // 补签只能在活动结束后申请
        if (activity.getEndTime() == null || LocalDateTime.now().isBefore(activity.getEndTime())) {
            throw new BusinessException("活动尚未结束，无法申请补签");
        }
        
        Attendance attendance = new Attendance();
        attendance.setActivityId(activityId);
        attendance.setUserId(userId);
        attendance.setIsSupplementary(1);
        attendance.setSupplementaryReason(reason);
        attendance.setSupplementaryProof(proof);
        attendance.setStatus("PENDING");
        attendanceMapper.insert(attendance);
        
        log.info("补签申请提交成功, userId: {}, activityId: {}", userId, activityId);
        return attendance;
    }
    
    @Transactional
    public void approveSupplementary(Long attendanceId) {
        log.info("审核通过补签, attendanceId: {}", attendanceId);
        Attendance attendance = attendanceMapper.selectById(attendanceId);
        if (attendance == null) {
            throw new BusinessException("考勤记录不存在");
        }

        if (!"PENDING".equals(attendance.getStatus())) {
            throw new BusinessException("补签状态不允许审核");
        }

        // 【修复的缺陷】原实现只把 status 置为 APPROVED，从不写 durationHours，
        // 而全平台时长统计一律以 status='APPROVED' AND duration_hours > 0 为准
        // （AttendanceMapper 的时长聚合 → CreditService 勋章 HOURS、ReportService 报表），
        // 结果是**补签审核通过的用户志愿时长永远为 0**，且因为 status 已变成 APPROVED，
        // 该记录也不会再进入任何待办列表，用户与管理员都无从察觉。
        // 这里按活动计划起止时间补出应得时长；活动缺少起止时间时显式报错，
        // 而不是留下一条"已通过但时长为空"的脏记录。
        if (attendance.getDurationHours() == null
                || attendance.getDurationHours().compareTo(BigDecimal.ZERO) <= 0) {
            Activity activity = activityMapper.selectById(attendance.getActivityId());
            if (activity == null || activity.getStartTime() == null || activity.getEndTime() == null) {
                throw new BusinessException("活动缺少起止时间，无法核定补签时长，请改用「时长审核」手工核定");
            }
            long seconds = Duration.between(activity.getStartTime(), activity.getEndTime()).getSeconds();
            if (seconds <= 0) {
                throw new BusinessException("活动起止时间不合法，无法核定补签时长，请改用「时长审核」手工核定");
            }
            BigDecimal hours = BigDecimal.valueOf(seconds / 3600.0)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            if (hours.compareTo(MAX_DURATION_HOURS) > 0) {
                hours = MAX_DURATION_HOURS;
            }
            attendance.setDurationHours(hours);
        }

        attendance.setStatus("APPROVED");
        attendance.setApprovalTime(LocalDateTime.now());
        attendanceMapper.updateById(attendance);
        log.info("补签审核通过, attendanceId: {}, 核定时长: {} 小时",
                attendanceId, attendance.getDurationHours());
    }
    
    @Transactional
    public void rejectSupplementary(Long attendanceId, String reason) {
        log.info("审核拒绝补签, attendanceId: {}", attendanceId);
        Attendance attendance = attendanceMapper.selectById(attendanceId);
        if (attendance == null) {
            throw new BusinessException("考勤记录不存在");
        }
        
        if (!"PENDING".equals(attendance.getStatus())) {
            throw new BusinessException("补签状态不允许审核");
        }
        
        attendance.setStatus("REJECTED");
        attendance.setRejectReason(reason);
        attendance.setApprovalTime(LocalDateTime.now());
        attendanceMapper.updateById(attendance);
        log.info("补签审核拒绝, attendanceId: {}", attendanceId);
    }
    
    @Transactional
    public void approveDuration(Long attendanceId, BigDecimal adjustedHours) {
        log.info("审核时长, attendanceId: {}, 核定小时数: {}", attendanceId, adjustedHours);
        Attendance attendance = attendanceMapper.selectById(attendanceId);
        if (attendance == null) {
            throw new BusinessException("考勤记录不存在");
        }

        if (!"PENDING_AUDIT".equals(attendance.getStatus())) {
            throw new BusinessException("考勤状态不允许审核");
        }

        // 【修复的缺陷】原实现把 adjustedHours 原样写入 DECIMAL(6,2) 列，没有任何边界校验：
        // 负数、0、999999、超过两位小数都会被接受，负时长会直接冲减用户的累计志愿时长
        // （CreditService 的勋章/等级/统计全部基于 duration_hours 求和）。
        if (adjustedHours != null) {
            if (adjustedHours.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("核定时长必须大于 0 小时");
            }
            if (adjustedHours.compareTo(MAX_DURATION_HOURS) > 0) {
                throw new BusinessException("核定时长不能超过 " + MAX_DURATION_HOURS + " 小时");
            }
            if (adjustedHours.stripTrailingZeros().scale() > 2) {
                throw new BusinessException("核定时长最多保留两位小数");
            }
        }

        // 【修复的缺陷】原实现是"先 select 判状态、再 updateById"的读-判-写：
        // 两个管理员并发审核同一条记录时都会通过状态检查，各自发出 DURATION_APPROVED 事件，
        // 造成重复发放志愿时长学分、重复调整信用分。这里改为带状态条件的原子更新，
        // 更新影响行数为 0 即代表已被他人抢先处理。
        BigDecimal finalHours = adjustedHours != null ? adjustedHours : attendance.getDurationHours();
        int updated = attendanceMapper.update(null,
                new LambdaUpdateWrapper<Attendance>()
                        .eq(Attendance::getId, attendanceId)
                        .eq(Attendance::getStatus, "PENDING_AUDIT")
                        .set(Attendance::getStatus, "APPROVED")
                        .set(Attendance::getApprovalTime, LocalDateTime.now())
                        .set(Attendance::getDurationHours, finalHours));
        if (updated == 0) {
            throw new BusinessException("该考勤记录已被其他管理员处理，请刷新后重试");
        }

        attendance.setStatus("APPROVED");
        attendance.setDurationHours(finalHours);
        publishAttendanceEvent(attendance, AttendanceEvent.EventType.DURATION_APPROVED);
        log.info("时长审核通过, attendanceId: {}, 时长: {} 小时", attendanceId, attendance.getDurationHours());
    }
}
