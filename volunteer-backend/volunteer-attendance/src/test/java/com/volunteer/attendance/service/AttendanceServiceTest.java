package com.volunteer.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.activity.entity.Activity;
import com.volunteer.activity.entity.ActivityRegistration;
import com.volunteer.activity.mapper.ActivityMapper;
import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.entity.AttendanceCredential;
import com.volunteer.attendance.mapper.AttendanceCredentialMapper;
import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.attendance.mapper.ViolationRecordMapper;
import com.volunteer.activity.mapper.ActivityRegistrationMapper;
import com.volunteer.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * volunteer-attendance 回归测试。
 *
 * <p>本模块是审查中确认的 P0 缺陷集中区，此前<b>没有任何单元测试</b>。这里锁死三个
 * 已修复的关键行为，防止回归：</p>
 * <ol>
 *   <li><b>位置预校验</b>不再是死接口：活动坐标来自活动自身，未配置坐标时报可读错误，
 *       而不是历史实现里恒返回 {@code isValid=false}（{@code distance=-1}）。</li>
 *   <li><b>二维码令牌</b>只做「比对」不做「消费」：同一张码可被多个已报名用户依次扫描，
 *       历史实现的 {@code getAndDelete()} 使只有第一个扫码者能签到。</li>
 *   <li><b>凭据类签到</b>必须绑定到具体账号：人脸/NFC/蓝牙凭据先登记再签到，
 *       未登记即拒绝且不泄漏凭据原文。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AttendanceService 回归测试(位置校验/二维码令牌/签到凭据)")
class AttendanceServiceTest {

    private static final double MAX_DISTANCE = 500d;

    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ActivityMapper activityMapper;
    @Mock private ActivityRegistrationMapper registrationMapper;
    @Mock private ViolationRecordMapper violationRecordMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private GeoLocationService geoLocationService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock private AttendanceCredentialMapper attendanceCredentialMapper;

    @InjectMocks private AttendanceService service;

    private void injectMaxDistance() {
        ReflectionTestUtils.setField(service, "maxDistanceMeters", MAX_DISTANCE);
    }

    /**
     * 注入签到时间窗配置（分钟）：提前量 / 延迟量。
     */
    private void injectTimeWindow(long advanceMinutes, long delayMinutes) {
        ReflectionTestUtils.setField(service, "checkInAdvanceMinutes", advanceMinutes);
        ReflectionTestUtils.setField(service, "checkInDelayMinutes", delayMinutes);
    }

    private Activity activityWithWindow(LocalDateTime start, LocalDateTime end) {
        Activity a = new Activity();
        a.setId(100L);
        a.setStatus("RECRUITING");
        a.setStartTime(start);
        a.setEndTime(end);
        return a;
    }

    private ActivityRegistration approvedRegistration() {
        ActivityRegistration r = new ActivityRegistration();
        r.setActivityId(100L);
        r.setUserId(7L);
        r.setStatus("APPROVED");
        return r;
    }

    private Activity activityWithLocation(BigDecimal lat, BigDecimal lng) {
        Activity a = new Activity();
        a.setId(100L);
        a.setLocationGpsLat(lat);
        a.setLocationGpsLng(lng);
        return a;
    }

    // ------------------------------------------------------------------
    // 1. 位置预校验(P0-1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("活动不存在:报「活动不存在」而不是返回 distance=-1")
    void validateLocationForActivity_missingActivity() {
        injectMaxDistance();
        when(activityMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.validateLocationForActivity(999L, "31.2,121.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动不存在");
    }

    @Test
    @DisplayName("活动未配置坐标:报可读错误而不是恒 false")
    void validateLocationForActivity_activityWithoutGps() {
        injectMaxDistance();
        when(activityMapper.selectById(100L)).thenReturn(activityWithLocation(null, null));

        assertThatThrownBy(() -> service.validateLocationForActivity(100L, "31.2,121.4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未设置签到位置");
    }

    @Test
    @DisplayName("活动有坐标且在半径内:isValid=true(历史实现恒 false 的回归点)")
    void validateLocationForActivity_withinRadius() {
        injectMaxDistance();
        when(activityMapper.selectById(100L))
                .thenReturn(activityWithLocation(new BigDecimal("31.2000"), new BigDecimal("121.4000")));
        when(geoLocationService.calculateDistanceFromGps(any(), any())).thenReturn(12.5d);

        Map<String, Object> r = service.validateLocationForActivity(100L, "31.2001,121.4001");

        assertThat(r.get("isValid")).isEqualTo(Boolean.TRUE);
        assertThat(r.get("distance")).isEqualTo(12.5d);
        assertThat(r.get("activityId")).isEqualTo(100L);
        assertThat(r.get("activityGps")).isEqualTo("31.2000,121.4000");
    }

    @Test
    @DisplayName("超出半径:isValid=false,但距离是真实值而非 -1")
    void validateLocationForActivity_outsideRadius() {
        injectMaxDistance();
        when(activityMapper.selectById(100L))
                .thenReturn(activityWithLocation(new BigDecimal("31.2000"), new BigDecimal("121.4000")));
        when(geoLocationService.calculateDistanceFromGps(any(), any())).thenReturn(1500d);

        Map<String, Object> r = service.validateLocationForActivity(100L, "31.3000,121.5000");

        assertThat(r.get("isValid")).isEqualTo(Boolean.FALSE);
        assertThat(r.get("distance")).isEqualTo(1500d);
    }

    // ------------------------------------------------------------------
    // 2. 二维码令牌「比对而非消费」(P0-2)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("二维码校验脚本只 GET 比对,不 DEL(否则只有第一个扫码者能签到)")
    void qrCodeScriptDoesNotDeleteToken() throws Exception {
        @SuppressWarnings("unchecked")
        RedisScript<Long> script = (RedisScript<Long>) ReflectionTestUtils.getField(
                AttendanceService.class, "VERIFY_QR_CODE_SCRIPT");

        assertThat(script).isNotNull();
        String body = script.getScriptAsString();
        assertThat(body).contains("GET").contains("ARGV[1]");
        assertThat(body).doesNotContain("DEL");
        assertThat(body).doesNotContain("GETDEL");
        assertThat(script.getResultType()).isEqualTo(Long.class);
    }

    // ------------------------------------------------------------------
    // 4. 签到时间窗改为可配置(P2-6)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超过 sign-in 延迟窗:拒绝签到(窗口值来自配置而非硬编码)")
    void checkIn_afterDelayWindowRejected() {
        injectMaxDistance();
        injectTimeWindow(30, 60);
        Activity activity = activityWithWindow(
                LocalDateTime.now().minusHours(4), LocalDateTime.now().minusHours(2));
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(registrationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(approvedRegistration());

        assertThatThrownBy(() -> service.checkIn(100L, 7L, "现场", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已超过签到时间");
    }

    @Test
    @DisplayName("60 分钟延迟窗已过、但 2 小时旧硬编码窗未过:仍拒绝(证明配置生效)")
    void checkIn_configWindowIsActuallyUsed() {
        injectMaxDistance();
        // 结束时间在 90 分钟前：超出新的 60 分钟延迟窗，但在旧的 2 小时窗口内
        injectTimeWindow(30, 60);
        Activity activity = activityWithWindow(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusMinutes(90));
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(registrationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(approvedRegistration());

        assertThatThrownBy(() -> service.checkIn(100L, 7L, "现场", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已超过签到时间");
    }

    @Test
    @DisplayName("扩大延迟窗后同一活动可签到:窗口确实由配置驱动")
    void checkIn_widenedWindowAllowsCheckIn() {
        injectMaxDistance();
        injectTimeWindow(30, 180);
        Activity activity = activityWithWindow(
                LocalDateTime.now().minusHours(3), LocalDateTime.now().minusMinutes(90));
        activity.setMaxParticipants(10);
        activity.setCurrentParticipants(1);
        activity.setLocationGpsLat(new BigDecimal("31.2000"));
        activity.setLocationGpsLng(new BigDecimal("121.4000"));
        when(activityMapper.selectById(100L)).thenReturn(activity);
        when(registrationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(approvedRegistration());
        when(attendanceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(attendanceMapper.insert(any(Attendance.class))).thenReturn(1);

        assertThatCode(() -> service.checkIn(100L, 7L, "现场", "31.2000,121.4000"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("早于提前窗:拒绝签到")
    void checkIn_beforeAdvanceWindowRejected() {
        injectMaxDistance();
        injectTimeWindow(30, 60);
        Activity activity = activityWithWindow(
                LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(5));
        when(activityMapper.selectById(100L)).thenReturn(activity);

        assertThatThrownBy(() -> service.checkIn(100L, 7L, "现场", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未到签到时间");
    }

    // ------------------------------------------------------------------
    // 5. 签到凭据绑定(P0-3)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("登记未知类型凭据:拒绝")
    void registerCredential_rejectsUnknownType() {
        assertThatThrownBy(() -> service.registerCredential(1L, "FINGERPRINT", "abc", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的凭据类型");
        verifyNoInteractions(attendanceCredentialMapper);
    }

    @Test
    @DisplayName("登记空白凭据:拒绝")
    void registerCredential_rejectsBlankValue() {
        assertThatThrownBy(() -> service.registerCredential(1L, "FACE", "   ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("首次登记:写入 SHA-256 哈希,响应不含凭据原文")
    void registerCredential_insertsHashedValue() {
        when(attendanceCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Map<String, Object> r = service.registerCredential(7L, "face", "  my-face-template  ", null);

        assertThat(r.get("registered")).isEqualTo(Boolean.TRUE);
        assertThat(r.get("credentialType")).isEqualTo("FACE");
        assertThat(r.toString()).doesNotContain("my-face-template");

        ArgumentCaptor<AttendanceCredential> captor = ArgumentCaptor.forClass(AttendanceCredential.class);
        verify(attendanceCredentialMapper).insert(captor.capture());
        AttendanceCredential saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getCredentialType()).isEqualTo("FACE");
        assertThat(saved.getStatus()).isEqualTo(1);
        // trim 后的 SHA-256 十六进制(64 字符)
        assertThat(saved.getCredentialHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(saved.getDisplayName()).isEqualTo("人脸识别");
        // 明文绝不入库
        assertThat(saved.getCredentialHash()).isNotEqualTo("my-face-template");
    }

    @Test
    @DisplayName("同一账号重复登记同一凭据:幂等,不新增记录")
    void registerCredential_sameOwnerIsIdempotent() {
        AttendanceCredential existing = new AttendanceCredential();
        existing.setUserId(7L);
        existing.setCredentialType("NFC");
        existing.setStatus(1);
        when(attendanceCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        Map<String, Object> r = service.registerCredential(7L, "NFC", "04A1B2C3", null);

        assertThat(r.get("registered")).isEqualTo(Boolean.TRUE);
        verify(attendanceCredentialMapper, never()).insert(any(AttendanceCredential.class));
        verify(attendanceCredentialMapper, never()).updateById(any(AttendanceCredential.class));
    }

    @Test
    @DisplayName("凭据已被他人登记:拒绝(防止冒用他人人脸/卡号)")
    void registerCredential_otherOwnerRejected() {
        AttendanceCredential others = new AttendanceCredential();
        others.setUserId(99L);
        others.setCredentialType("FACE");
        others.setStatus(1);
        when(attendanceCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(others);

        assertThatThrownBy(() -> service.registerCredential(7L, "FACE", "someone-else-face", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其它账号登记");
        verify(attendanceCredentialMapper, never()).insert(any(AttendanceCredential.class));
    }

    @Test
    @DisplayName("曾停用的本人凭据重新登记:恢复为启用,不新增记录")
    void registerCredential_reactivatesDisabledOwnCredential() {
        AttendanceCredential disabled = new AttendanceCredential();
        disabled.setUserId(7L);
        disabled.setCredentialType("BLUETOOTH");
        disabled.setStatus(0);
        when(attendanceCredentialMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(disabled);

        service.registerCredential(7L, "bluetooth", "AA:BB:CC:DD:EE:FF", "我的手表");

        ArgumentCaptor<AttendanceCredential> captor = ArgumentCaptor.forClass(AttendanceCredential.class);
        verify(attendanceCredentialMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
        verify(attendanceCredentialMapper, never()).insert(any(AttendanceCredential.class));
    }
}
