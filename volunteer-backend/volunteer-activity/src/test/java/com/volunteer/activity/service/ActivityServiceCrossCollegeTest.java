package com.volunteer.activity.service;

import com.volunteer.activity.entity.Activity;
import com.volunteer.auth.entity.SysUser;
import com.volunteer.common.enums.ActivityStatusEnum;
import com.volunteer.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-04 实施:跨学院越权拦截单元测试
 * <p>覆盖 validateCollegeAccess 的 admin / organizer / collegeAdmin / volunteer 四种角色场景。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivityService 跨学院越权 单元测试")
class ActivityServiceCrossCollegeTest {

    @Mock private com.volunteer.activity.mapper.ActivityMapper activityMapper;
    @Mock private com.volunteer.activity.mapper.ActivityRegistrationMapper registrationMapper;
    @Mock private ActivityCacheService activityCacheService;
    @Mock private com.volunteer.activity.mapper.TeamMapper teamMapper;
    @Mock private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Spy
    @InjectMocks
    private ActivityService service;

    private Activity newActivity(Long collegeId) {
        Activity a = new Activity();
        a.setId(1L);
        a.setTitle("测试活动");
        a.setOrganizerId(10L);
        a.setCollegeId(collegeId);
        a.setStatus(ActivityStatusEnum.DRAFT.name());
        return a;
    }

    @Test
    @DisplayName("admin 跨学院操作 - 放行")
    void adminAllowed() throws Exception {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setRoleCode("ADMIN");
        admin.setCollegeId(null);

        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("validateCollegeAccess", Activity.class, SysUser.class, String.class);
        m.setAccessible(true);
        // admin 无学院也能通过
        m.invoke(service, newActivity(99L), admin, "操作");
    }

    @Test
    @DisplayName("organizer 跨学院操作 - 拒绝")
    void organizerBlocked() throws Exception {
        SysUser organizer = new SysUser();
        organizer.setId(2L);
        organizer.setRoleCode("ORGANIZER");
        organizer.setCollegeId(10L);

        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("validateCollegeAccess", Activity.class, SysUser.class, String.class);
        m.setAccessible(true);

        Activity otherCollege = newActivity(20L); // 不属于 organizer 学院
        assertThatThrownBy(() -> m.invoke(service, otherCollege, organizer, "修改"))
                .hasCauseInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("organizer 本学院操作 - 放行")
    void organizerOwnCollegeAllowed() throws Exception {
        SysUser organizer = new SysUser();
        organizer.setId(2L);
        organizer.setRoleCode("ORGANIZER");
        organizer.setCollegeId(10L);

        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("validateCollegeAccess", Activity.class, SysUser.class, String.class);
        m.setAccessible(true);
        m.invoke(service, newActivity(10L), organizer, "修改");
    }

    @Test
    @DisplayName("collegeAdmin 跨学院操作 - 拒绝")
    void collegeAdminBlocked() throws Exception {
        SysUser collegeAdmin = new SysUser();
        collegeAdmin.setId(3L);
        collegeAdmin.setRoleCode("COLLEGE_ADMIN");
        collegeAdmin.setCollegeId(10L);

        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("validateCollegeAccess", Activity.class, SysUser.class, String.class);
        m.setAccessible(true);

        Activity otherCollege = newActivity(99L);
        assertThatThrownBy(() -> m.invoke(service, otherCollege, collegeAdmin, "发布"))
                .hasCauseInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("未登录访问 - 拒绝")
    void anonymousBlocked() throws Exception {
        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("validateCollegeAccess", Activity.class, SysUser.class, String.class);
        m.setAccessible(true);

        assertThatThrownBy(() -> m.invoke(service, newActivity(1L), null, "操作"))
                .hasCauseInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("checkIsCollegeAdmin 单元覆盖")
    void checkIsCollegeAdmin() throws Exception {
        java.lang.reflect.Method m = ActivityService.class
                .getDeclaredMethod("checkIsCollegeAdmin", SysUser.class);
        m.setAccessible(true);

        SysUser u = new SysUser();
        u.setRoleCode("COLLEGE_ADMIN");
        assertThat((Boolean) m.invoke(service, u)).isTrue();

        u.setRoleCode("VOLUNTEER");
        assertThat((Boolean) m.invoke(service, u)).isFalse();

        assertThat((Boolean) m.invoke(service, (SysUser) null)).isFalse();
    }
}
