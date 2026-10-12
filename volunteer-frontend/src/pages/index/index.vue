<template>
  <view class="page workspace-page">
    <view class="admin-shell">
      <view class="admin-sidebar">
        <view class="brand">
          <text class="brand-mark">重</text>
          <view>
            <text class="brand-name">志愿服务平台</text>
            <text class="brand-sub">管理端工作台</text>
          </view>
        </view>

        <scroll-view class="nav-scroll" scroll-y>
          <view class="nav-list">
            <view class="nav-group">
              <button
                v-for="item in sections"
                :key="item.key"
                class="nav-item"
                :class="{ active: isActiveNavItem(item) }"
                @click="handleNavItem(item)"
              >
                <view class="nav-code">
                  <image class="nav-icon" :src="item.icon" mode="aspectFit" />
                </view>
                <text class="nav-label">{{ item.label }}</text>
              </button>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="admin-main">
        <view class="topbar">
          <view>
            <text class="admin-title">{{ currentSection.label }}</text>
            <text class="admin-subtitle">校级志愿服务运行中枢</text>
          </view>
          <view class="top-actions">
            <text class="tag">{{ roleLabel }}</text>
            <button class="btn" :disabled="refreshLocked" @click="refreshCurrentSection">{{ refreshButtonText }}</button>
            <button class="btn btn--primary" @click="goClientHome">客户端页面</button>
          </view>
        </view>

        <view v-if="activeSection === 'overview'" class="admin-section">
          <view class="metric-grid">
            <view class="metric-card shell-card">
              <text class="metric-label">活动总数</text>
              <text class="metric-value">{{ activityTotal }}</text>
              <text class="metric-note">来自运营大屏接口</text>
            </view>
            <view class="metric-card shell-card">
              <text class="metric-label">用户总数</text>
              <text class="metric-value">{{ userTotal }}</text>
              <text class="metric-note">来自运营大屏接口</text>
            </view>
            <view class="metric-card shell-card">
              <text class="metric-label">签到率</text>
              <text class="metric-value">{{ checkinRateText }}</text>
              <text class="metric-note">来自统计接口</text>
            </view>
            <view class="metric-card shell-card">
              <text class="metric-label">导出任务</text>
              <text class="metric-value">{{ exportTasks.length }}</text>
              <text class="metric-note">异步导出记录</text>
            </view>
          </view>

          <view class="dashboard-grid">
            <view class="panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">活动趋势</text>
                  <text class="section-title__sub">最近活动数据</text>
                </view>
              </view>
              <view class="trend-summary">
                <text>{{ trendSummaryText }}</text>
                <text class="tag">{{ trendTotal }} 个</text>
              </view>
              <view class="bar-chart">
                <view v-for="item in trendBars" :key="item.date" class="bar-slot" :class="{ empty: item.value === 0 }">
                  <view class="bar-track">
                    <view class="bar-fill" :style="{ height: `${trendBarHeight(item.value)}%` }">
                      <text v-if="item.value" class="bar-value">{{ item.value }}</text>
                    </view>
                  </view>
                  <text class="bar-label">{{ item.label }}</text>
                </view>
              </view>
            </view>

            <view class="panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">工作提醒</text>
                  <text class="section-title__sub">当前可处理事项</text>
                </view>
              </view>
              <view class="todo-line">
                <text>活动管理</text>
                <text class="tag">{{ overviewActivityCount }} 条</text>
              </view>
              <view class="todo-line">
                <text>公告发布</text>
                <text class="tag tag--blue">{{ announcements.length }} 条</text>
              </view>
              <view class="todo-line">
                <text>审计日志</text>
                <text class="tag tag--amber">按需查看</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="activeSection === 'activities'" class="admin-section">
          <view class="toolbar shell-card">
            <input class="input" v-model="activityQuery.keyword" placeholder="搜索活动名称、地点或组织方" @confirm="loadActivities" />
            <input class="input" v-model="activityQuery.status" placeholder="状态，例如 RECRUITING" @confirm="loadActivities" />
            <button class="btn" @click="loadActivities">查询</button>
            <button class="btn btn--primary" @click="openCreateActivityDrawer">新建活动</button>
          </view>

          <view class="data-table">
            <view class="data-head activity-row">
              <text>活动</text>
              <text>状态</text>
              <text>人数</text>
              <text>时长</text>
              <text>操作</text>
            </view>
            <view v-for="activity in activities" :key="activity.id" class="data-row activity-row">
              <view class="data-main">
                <text>{{ activity.title || activity.name || '未命名活动' }}</text>
                <text class="data-sub">{{ activity.location || '地点待定' }} · {{ activity.organizerName || '负责人' }}</text>
              </view>
              <text class="tag" :class="statusClass(activity.status)">{{ statusText(activity.status) }}</text>
              <text>{{ activity.currentParticipants || activity.participantCount || 0 }}/{{ activity.maxParticipants || '不限' }}</text>
              <text>{{ activity.durationHours || activity.duration || 0 }} 小时</text>
              <view class="row-actions">
                <button class="btn btn--ghost" @click="goDetail(activity.id)">详情</button>
                <button class="btn" @click="publishActivity(activity.id)">发布</button>
                <button class="btn btn--danger" @click="deleteActivity(activity.id)">删除</button>
              </view>
            </view>
          </view>
          <view v-if="!activities.length && !loading" class="empty-state">
            <text class="empty-state__title">暂无活动</text>
          </view>
        </view>

        <view v-if="activeSection === 'users'" class="admin-section">
          <view class="toolbar shell-card two-controls">
            <input class="input" v-model="userQuery.keyword" placeholder="搜索用户、姓名或手机号" @confirm="loadUsers" />
            <button class="btn" @click="loadUsers">查询</button>
          </view>
          <view class="data-table">
            <view class="data-head user-row">
              <text>用户</text>
              <text>角色</text>
              <text>学院</text>
              <text>操作</text>
            </view>
            <view v-for="user in users" :key="user.id || user.userId" class="data-row user-row">
              <view class="data-main">
                <text>{{ user.realName || user.username || '未命名用户' }}</text>
                <text class="data-sub">{{ user.phone || user.studentNo || '暂无联系方式' }}</text>
              </view>
              <text class="tag tag--blue">{{ user.role || user.roleCode || 'VOLUNTEER' }}</text>
              <text>{{ user.collegeName || user.college || '未绑定' }}</text>
              <button class="btn btn--ghost" @click="openUserDrawer(user)">查看</button>
            </view>
          </view>
          <view v-if="!users.length && !loading" class="empty-state">
            <text class="empty-state__title">暂无用户数据</text>
          </view>
        </view>

        <view v-if="activeSection === 'attendance'" class="admin-section two-column">
          <view class="panel shell-card">
            <view class="section-title">
              <view>
                <text class="section-title__main">签到二维码</text>
                <text class="section-title__sub">选择活动生成现场签到二维码</text>
              </view>
            </view>
            <label class="field">
              <text class="field__label">选择活动</text>
              <picker
                mode="selector"
                :range="attendanceActivityOptions"
                range-key="label"
                :value="attendanceActivityIndex"
                :disabled="attendanceActivitiesLoading || !attendanceActivityOptions.length"
                @change="selectAttendanceActivity"
              >
                <view class="drawer-select attendance-activity-select">
                  <text class="attendance-activity-name">{{ attendanceActivityLabel || attendanceActivityPlaceholder }}</text>
                  <text v-if="attendanceForm.activityId" class="attendance-activity-id">ID {{ attendanceForm.activityId }}</text>
                </view>
              </picker>
            </label>
            <view class="attendance-actions">
              <button class="btn" :disabled="attendanceActivitiesLoading" @click="loadAttendanceActivities(true)">
                {{ attendanceActivitiesLoading ? '加载中' : '刷新活动' }}
              </button>
              <button class="btn btn--primary" :disabled="qrGenerating || !attendanceForm.activityId" @click="generateQRCode">
                {{ qrGenerating ? '生成中' : '生成二维码' }}
              </button>
            </view>
            <view v-if="qrImageSrc || qrToken" class="attendance-qr-card">
              <view class="attendance-qr-visual">
                <image v-if="qrImageSrc" class="attendance-qr-image" :src="qrImageSrc" mode="aspectFit" />
                <view v-else class="attendance-qr-empty">
                  <text>二维码图片未返回</text>
                </view>
              </view>
              <view class="attendance-qr-info">
                <text class="attendance-qr-title">现场签到二维码</text>
                <text class="attendance-qr-meta">活动 ID：{{ qrActivityId || attendanceForm.activityId }}</text>
                <text class="attendance-qr-meta">{{ qrExpireText }}</text>
                <text class="attendance-qr-token" selectable>{{ qrToken }}</text>
              </view>
            </view>
          </view>

          <view class="panel shell-card">
            <view class="section-title">
              <view>
                <text class="section-title__main">失信记录</text>
                <text class="section-title__sub">创建活动违规限制</text>
              </view>
            </view>
            <view class="form-grid">
              <label class="field">
                <text class="field__label">用户 ID</text>
                <input class="input" v-model="violationForm.userId" />
              </label>
              <label class="field">
                <text class="field__label">活动 ID</text>
                <input class="input" v-model="violationForm.activityId" />
              </label>
            </view>
            <label class="field">
              <text class="field__label">原因</text>
              <textarea class="textarea" v-model="violationForm.violationReason" />
            </label>
            <button class="btn btn--primary" @click="createViolation">创建记录</button>
          </view>
        </view>

        <view v-if="activeSection === 'reports'" class="admin-section">
          <view class="toolbar shell-card export-toolbar">
            <button class="btn btn--primary" @click="downloadExport('/api/export/activity-stats', '活动统计.xlsx')">导出活动统计</button>
            <button class="btn" @click="downloadExport('/api/export/user-duration', '用户时长.xlsx')">导出用户时长</button>
            <button class="btn" @click="downloadExport('/api/export/csv', '平台数据.csv')">导出 CSV</button>
          </view>
          <view class="dashboard-grid">
            <view class="panel shell-card">
              <text class="section-title__main">活动分类</text>
              <view v-if="categoryData.length" class="category-list">
                <view v-for="item in categoryData" :key="item.name" class="category-line">
                  <text>{{ item.name }}</text>
                  <view class="category-track">
                    <view class="category-fill" :style="{ width: `${barHeight(item.value)}%` }"></view>
                  </view>
                  <text>{{ item.value }}</text>
                </view>
              </view>
              <view v-else class="empty-state">
                <text class="empty-state__title">暂无分类数据</text>
              </view>
            </view>

            <view class="panel shell-card">
              <text class="section-title__main">导出任务</text>
              <view v-for="task in exportTasks" :key="task.id || task.taskId" class="todo-line">
                <text>{{ task.fileName || task.exportType || '导出任务' }}</text>
                <text class="tag">{{ task.status || 'RUNNING' }}</text>
              </view>
              <view v-if="!exportTasks.length" class="empty-state">
                <text class="empty-state__title">暂无导出任务</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="activeSection === 'announcements'" class="admin-section">
          <view class="toolbar shell-card two-controls">
            <input class="input" v-model="announcementQuery.keyword" placeholder="搜索公告" @confirm="loadAnnouncements" />
            <button class="btn" @click="loadAnnouncements">查询</button>
          </view>
          <view class="data-table">
            <view class="data-head announcement-row">
              <text>公告</text>
              <text>状态</text>
              <text>时间</text>
            </view>
            <view v-for="item in announcements" :key="item.id" class="data-row announcement-row">
              <view class="data-main">
                <text>{{ item.title || '系统公告' }}</text>
                <text class="data-sub">{{ item.summary || item.content || '暂无摘要' }}</text>
              </view>
              <text class="tag">{{ item.status || 'ACTIVE' }}</text>
              <text>{{ formatTime(item.createTime || item.createdAt) }}</text>
            </view>
          </view>
        </view>

        <view v-if="activeSection === 'audit'" class="admin-section">
          <view class="data-table">
            <view class="data-head audit-row">
              <text>操作</text>
              <text>人员</text>
              <text>结果</text>
              <text>时间</text>
            </view>
            <view v-for="item in auditLogs" :key="item.id" class="data-row audit-row">
              <view class="data-main">
                <text>{{ item.operation || item.action || '系统操作' }}</text>
                <text class="data-sub">{{ item.module || item.description || '暂无详情' }}</text>
              </view>
              <text>{{ item.operator || item.username || '系统' }}</text>
              <text class="tag" :class="item.success === false ? 'tag--red' : ''">{{ item.success === false ? '失败' : '成功' }}</text>
              <text>{{ formatTime(item.createTime || item.createdAt) }}</text>
            </view>
          </view>
          <view v-if="!auditLogs.length" class="empty-state">
            <text class="empty-state__title">暂无审计日志</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="drawer.visible" class="drawer-layer">
      <view class="drawer-mask" @click="closeDrawer"></view>
      <view class="admin-drawer">
        <view class="drawer-head">
          <view>
            <text class="drawer-kicker">管理端详情</text>
            <text class="drawer-title">{{ drawerTitle }}</text>
            <text class="drawer-subtitle">{{ drawerSubtitle }}</text>
          </view>
          <button class="btn drawer-close" @click="closeDrawer">关闭</button>
        </view>

        <scroll-view class="drawer-body" scroll-y>
          <view v-if="drawerLoading" class="empty-state drawer-state">
            <text class="empty-state__title">加载中</text>
            <text>正在读取详细信息</text>
          </view>

          <view v-else-if="drawer.type === 'activity' && (selectedActivity || drawer.mode === 'create')" class="drawer-stack">
            <view class="drawer-hero">
              <view>
                <text class="drawer-kicker drawer-kicker--light">{{ activityDrawerModeText }}</text>
                <text class="drawer-hero-title">{{ activityDrawerHeroTitle }}</text>
                <text class="drawer-hero-meta">{{ activityDrawerHeroMeta }}</text>
              </view>
              <text class="tag drawer-hero-tag" :class="statusClass(activityDrawerStatus)">{{ statusText(activityDrawerStatus) }}</text>
            </view>

            <view class="drawer-summary">
              <view class="summary-cell">
                <text class="summary-label">活动时间</text>
                <text class="summary-value">{{ activityDrawerTimeText }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">报名人数</text>
                <text class="summary-value">{{ activityDrawerCapacityText }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">活动分类</text>
                <text class="summary-value">{{ activityCategoryLabel }}</text>
              </view>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">{{ drawer.mode === 'create' ? '新建活动' : '编辑活动' }}</text>
                  <text class="section-title__sub">{{ drawer.mode === 'create' ? '填写完整信息后创建草稿' : '保存后同步回活动列表' }}</text>
                </view>
              </view>
              <view v-if="drawer.mode === 'create'" class="ai-draft-box">
                <label class="field">
                  <text class="field__label">AI 活动草稿</text>
                  <textarea
                    class="textarea ai-draft-input"
                    v-model="aiActivityPrompt"
                    maxlength="1200"
                    placeholder="例如：下周三下午在大学城社区给老人教智能手机，20 人左右"
                    @input="aiActivityError = ''"
                  />
                </label>
                <view class="ai-draft-actions">
                  <button class="btn btn--primary" :disabled="aiActivityGenerating" @click="generateActivityDraft">
                    {{ aiActivityGenerating ? '生成中' : '生成并回填' }}
                  </button>
                  <button class="btn btn--ghost" :disabled="aiActivityGenerating" @click="clearActivityDraftPrompt">清空</button>
                </view>
                <text v-if="aiActivityError" class="error-line">{{ aiActivityError }}</text>
              </view>
              <label class="field">
                <text class="field__label">活动名称</text>
                <input class="input" v-model="activityForm.title" placeholder="请输入活动名称" />
              </label>
              <label class="field">
                <text class="field__label">活动地点</text>
                <input class="input" v-model="activityForm.location" placeholder="请输入活动地点" />
              </label>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">活动分类</text>
                  <picker :range="activityCategoryOptions" range-key="label" :value="activityCategoryIndex" @change="selectActivityCategory">
                    <view class="drawer-select">{{ activityCategoryLabel || '请选择活动分类' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">组织方</text>
                  <input class="input" v-model="activityForm.organizerName" placeholder="负责人或组织名称" />
                </label>
              </view>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">开始日期</text>
                  <picker mode="date" :value="activityForm.startDate" @change="selectActivityStartDate">
                    <view class="drawer-select">{{ activityForm.startDate || '请选择开始日期' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">开始时间</text>
                  <picker mode="time" :value="activityForm.startClock" @change="selectActivityStartClock">
                    <view class="drawer-select">{{ activityForm.startClock || '请选择开始时间' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">结束日期</text>
                  <picker mode="date" :value="activityForm.endDate" @change="selectActivityEndDate">
                    <view class="drawer-select">{{ activityForm.endDate || '请选择结束日期' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">结束时间</text>
                  <picker mode="time" :value="activityForm.endClock" @change="selectActivityEndClock">
                    <view class="drawer-select">{{ activityForm.endClock || '请选择结束时间' }}</view>
                  </picker>
                </label>
              </view>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">状态</text>
                  <view class="drawer-select drawer-select--readonly">{{ statusText(activityForm.status || 'DRAFT') }}</view>
                </label>
                <label class="field">
                  <text class="field__label">服务时长</text>
                  <input class="input" v-model="activityForm.durationHours" type="number" placeholder="例如 2" />
                </label>
                <label class="field">
                  <text class="field__label">名额上限</text>
                  <input class="input" v-model="activityForm.maxParticipants" type="number" placeholder="例如 30" />
                </label>
              </view>
              <label class="field">
                <text class="field__label">活动说明</text>
                <textarea class="textarea drawer-textarea" v-model="activityForm.description" placeholder="填写服务内容、参与要求和注意事项" />
              </label>
              <view v-if="drawer.mode === 'create'" class="notice-draft-box">
                <label class="field">
                  <text class="field__label">通知标题</text>
                  <input class="input" v-model="activityNotificationTitle" placeholder="AI 生成后可编辑" />
                </label>
                <label class="field">
                  <text class="field__label">发布通知文案</text>
                  <textarea
                    class="textarea notice-draft-textarea"
                    v-model="activityNotificationContent"
                    maxlength="8000"
                    placeholder="AI 生成后会在这里回填招募通知，可用于公告、群通知或活动发布说明"
                  />
                </label>
                <view class="notice-draft-actions">
                  <button class="btn" :disabled="!activityNotificationContent" @click="copyActivityNotification">复制通知文案</button>
                </view>
              </view>
              <button class="btn btn--primary drawer-save" :disabled="drawerSaving" @click="saveActivityDrawer">
                {{ drawerSaving ? '保存中' : (drawer.mode === 'create' ? '创建活动' : '保存活动信息') }}
              </button>
            </view>
          </view>

          <view v-else-if="drawer.type === 'user' && selectedUser" class="drawer-stack">
            <view class="drawer-hero">
              <view class="drawer-user-head">
                <view class="drawer-avatar">{{ userInitials(selectedUser) }}</view>
                <view>
                  <text class="drawer-kicker drawer-kicker--light">用户信息</text>
                  <text class="drawer-hero-title">{{ displayUserName(selectedUser) }}</text>
                  <text class="drawer-hero-meta">{{ selectedUser.phone || selectedUser.email || '暂无联系方式' }}</text>
                </view>
              </view>
              <text class="tag drawer-hero-tag" :class="userStatusClass(selectedUser)">{{ userStatusText(selectedUser) }}</text>
            </view>

            <view class="drawer-summary">
              <view class="summary-cell">
                <text class="summary-label">角色</text>
                <text class="summary-value">{{ selectedUser.role || selectedUser.roleCode || 'VOLUNTEER' }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">学院</text>
                <text class="summary-value">{{ selectedUser.collegeName || selectedUser.college || '未绑定' }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">学号</text>
                <text class="summary-value">{{ selectedUser.studentNo || selectedUser.studentId || '未绑定' }}</text>
              </view>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">编辑用户</text>
                  <text class="section-title__sub">后台用户信息直接在抽屉内维护</text>
                </view>
              </view>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">用户名</text>
                  <input class="input" v-model="userForm.username" />
                </label>
                <label class="field">
                  <text class="field__label">真实姓名</text>
                  <input class="input" v-model="userForm.realName" />
                </label>
                <label class="field">
                  <text class="field__label">手机号</text>
                  <input class="input" v-model="userForm.phone" />
                </label>
                <label class="field">
                  <text class="field__label">邮箱</text>
                  <input class="input" v-model="userForm.email" />
                </label>
                <label class="field">
                  <text class="field__label">学院</text>
                  <input class="input" v-model="userForm.collegeName" />
                </label>
                <label class="field">
                  <text class="field__label">角色</text>
                  <input class="input" v-model="userForm.role" />
                </label>
                <label class="field">
                  <text class="field__label">学号</text>
                  <input class="input" v-model="userForm.studentNo" />
                </label>
                <label class="field">
                  <text class="field__label">状态</text>
                  <input class="input" v-model="userForm.status" />
                </label>
              </view>
              <button class="btn btn--primary drawer-save" :disabled="drawerSaving" @click="saveUserDrawer">
                {{ drawerSaving ? '保存中' : '保存用户信息' }}
              </button>
            </view>
          </view>

          <view v-else-if="drawer.type === 'profile'" class="drawer-stack">
            <view class="drawer-hero">
              <view class="drawer-user-head">
                <view class="drawer-avatar">档</view>
                <view>
                  <text class="drawer-kicker drawer-kicker--light">个人档案</text>
                  <text class="drawer-hero-title">{{ userStore.userInfo.realName || userStore.userInfo.username || '当前账号' }}</text>
                  <text class="drawer-hero-meta">{{ profileRequiredComplete ? '必要信息已完整' : '请补齐报名、通知和应急联系所需信息' }}</text>
                </view>
              </view>
              <text class="tag drawer-hero-tag" :class="profileStatusClass">{{ profileStatusText }}</text>
            </view>

            <view class="drawer-summary">
              <view class="summary-cell">
                <text class="summary-label">学号</text>
                <text class="summary-value">{{ currentStudentNo || '未绑定' }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">联系方式</text>
                <text class="summary-value">{{ profileForm.phone || '未填写' }}</text>
              </view>
              <view class="summary-cell">
                <text class="summary-label">必要信息</text>
                <text class="summary-value">{{ profileRequiredCompletedCount }}/{{ profileRequiredInfoItems.length }}</text>
              </view>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">基础信息</text>
                  <text class="section-title__sub">资料补全在管理端抽屉内完成</text>
                </view>
              </view>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">姓名</text>
                  <input class="input" v-model.trim="profileForm.realName" maxlength="30" placeholder="请输入姓名" />
                </label>
                <label class="field">
                  <text class="field__label">性别</text>
                  <picker mode="selector" :range="profileGenderOptions" range-key="label" :value="profileGenderIndex" @change="selectProfileGender">
                    <view class="drawer-select">{{ profileGenderLabel || '请选择性别' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">学院</text>
                  <view class="drawer-select drawer-select--readonly">{{ profileForm.collegeName || '未绑定学院' }}</view>
                </label>
                <label class="field">
                  <text class="field__label">专业</text>
                  <input class="input" v-model.trim="profileForm.major" maxlength="40" placeholder="如计算机科学" />
                </label>
                <label class="field">
                  <text class="field__label">年级</text>
                  <input class="input" v-model.trim="profileForm.grade" maxlength="20" placeholder="如2024级" />
                </label>
                <label class="field">
                  <text class="field__label">班级</text>
                  <input class="input" v-model.trim="profileForm.className" maxlength="40" placeholder="如计科 2401 班" />
                </label>
                <label class="field">
                  <text class="field__label">政治面貌</text>
                  <picker mode="selector" :range="profilePoliticalOptions" :value="profilePoliticalIndex" @change="selectProfilePoliticalStatus">
                    <view class="drawer-select">{{ profileForm.politicalStatus || '请选择' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">宿舍片区</text>
                  <input class="input" v-model.trim="profileForm.dormArea" maxlength="60" placeholder="如兰园 3 栋" />
                </label>
              </view>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">必要信息</text>
                  <text class="section-title__sub">报名、通知和应急联系使用</text>
                </view>
              </view>
              <label class="field">
                <text class="field__label">学号</text>
                <view class="drawer-inline-control">
                  <view class="drawer-select drawer-select--readonly">{{ currentStudentNo || '未绑定学号' }}</view>
                  <button v-if="!currentStudentNo" class="btn" @click="openBindStudentDrawer">绑定</button>
                </view>
              </label>
              <view class="drawer-form-grid">
                <label class="field">
                  <text class="field__label">联系方式</text>
                  <input class="input" v-model.trim="profileForm.phone" maxlength="20" placeholder="请输入手机号" />
                </label>
                <label class="field">
                  <text class="field__label">出生日期</text>
                  <picker mode="date" :value="profileForm.birthDate" :end="todayDate" @change="selectProfileBirthDate">
                    <view class="drawer-select">{{ profileForm.birthDate || '请选择日期' }}</view>
                  </picker>
                </label>
                <label class="field">
                  <text class="field__label">紧急联系人</text>
                  <input class="input" v-model.trim="profileForm.emergencyContact" maxlength="30" placeholder="请输入姓名" />
                </label>
                <label class="field">
                  <text class="field__label">紧急联系电话</text>
                  <input class="input" v-model.trim="profileForm.emergencyPhone" maxlength="20" placeholder="请输入联系电话" />
                </label>
              </view>
              <view class="drawer-progress">
                <text>{{ profileRequiredCompletedCount }}/{{ profileRequiredInfoItems.length }}</text>
                <text>{{ profileRequiredComplete ? '必要信息已完成' : '仍有必要信息未填写' }}</text>
              </view>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">服务偏好</text>
                  <text class="section-title__sub">用于活动推荐与组织分组</text>
                </view>
              </view>
              <view class="drawer-tag-list">
                <button
                  v-for="tag in profileServiceTags"
                  :key="tag"
                  class="drawer-tag-option"
                  :class="{ active: profileTags.includes(tag) }"
                  @click="toggleProfileTag(tag)"
                >
                  {{ tag }}
                </button>
              </view>
            </view>

            <text v-if="profileError" class="error-line">{{ profileError }}</text>
            <button class="btn btn--primary drawer-save" :disabled="drawerSaving" @click="saveProfileDrawer">
              {{ drawerSaving ? '保存中' : '保存个人档案' }}
            </button>
          </view>

          <view v-else-if="drawer.type === 'bindStudent'" class="drawer-stack">
            <view class="drawer-hero">
              <view class="drawer-user-head">
                <view class="drawer-avatar">ID</view>
                <view>
                  <text class="drawer-kicker drawer-kicker--light">账号身份</text>
                  <text class="drawer-hero-title">绑定学号</text>
                  <text class="drawer-hero-meta">{{ currentStudentNo ? `当前学号 ${currentStudentNo}` : '当前账号暂未绑定学号' }}</text>
                </view>
              </view>
              <text class="tag drawer-hero-tag" :class="currentStudentNo ? 'tag--blue' : 'tag--amber'">
                {{ currentStudentNo ? '已绑定' : '未绑定' }}
              </text>
            </view>

            <view class="drawer-panel shell-card">
              <view class="section-title">
                <view>
                  <text class="section-title__main">学号信息</text>
                  <text class="section-title__sub">6-20 位字母或数字</text>
                </view>
              </view>
              <label class="field">
                <text class="field__label">学号</text>
                <input
                  class="input"
                  v-model.trim="bindStudentForm.studentId"
                  :disabled="Boolean(currentStudentNo)"
                  maxlength="20"
                  placeholder="请输入学号"
                  @input="clearStudentAvailability"
                />
              </label>
              <label class="field">
                <text class="field__label">登录密码</text>
                <input
                  class="input"
                  v-model="bindStudentForm.password"
                  :disabled="Boolean(currentStudentNo)"
                  password
                  maxlength="64"
                  placeholder="请输入当前账号密码"
                  @input="studentError = ''"
                />
              </label>
              <text v-if="studentAvailabilityText" class="drawer-result" :class="studentAvailabilityClass">{{ studentAvailabilityText }}</text>
              <text v-if="studentError" class="error-line">{{ studentError }}</text>
              <view class="drawer-actions">
                <button class="btn" :disabled="studentChecking || Boolean(currentStudentNo)" @click="checkDrawerStudentId">
                  {{ studentChecking ? '检查中' : '检查占用' }}
                </button>
                <button class="btn btn--primary" :disabled="bindStudentDisabled" @click="bindStudentInDrawer">
                  {{ studentBinding ? '绑定中' : '确认绑定' }}
                </button>
              </view>
            </view>
          </view>

          <view v-else class="empty-state drawer-state">
            <text class="empty-state__title">暂无信息</text>
            <text>请选择一条记录查看详情</text>
          </view>
        </scroll-view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import {
  aiApi,
  activityApi,
  announcementApi,
  attendanceApi,
  auditApi,
  authApi,
  dashboardApi,
  exportApi,
  profileApi,
  reportApi,
  statisticsApi,
  userApi
} from '@/api'
import { downloadAuthenticated } from '@/utils/fileDownload'
import { useUserStore } from '@/stores/user'
import { sanitizeInput } from '@/utils/validate'

const userStore = useUserStore()
const activeSection = ref('overview')
const loading = ref(false)
const refreshingSection = ref('')
const error = ref('')
const lastSectionRefreshAt = reactive({})
const refreshCooldownMs = 12000
const activityRequestCooldownMs = 12000
let lastActivityRequestAt = 0
let activityRequestPromise = null

const activities = ref([])
const activityTotal = ref(0)
const attendanceActivities = ref([])
const attendanceActivitiesLoading = ref(false)
const users = ref([])
const userTotal = ref(0)
const selectedActivity = ref(null)
const selectedUser = ref(null)
const drawerLoading = ref(false)
const drawerSaving = ref(false)
const dashboard = ref({})
const trendData = ref([])
const categoryData = ref([])
const checkinRate = ref(null)
const announcements = ref([])
const auditLogs = ref([])
const exportTasks = ref([])
const qrActivityId = ref('')
const qrToken = ref('')
const qrImageSrc = ref('')
const qrExpireText = ref('')
const qrGenerating = ref(false)

const activityQuery = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 20 })
const userQuery = reactive({ keyword: '', pageNum: 1, pageSize: 20 })
const announcementQuery = reactive({ keyword: '', pageNum: 1, pageSize: 20 })
const attendanceForm = reactive({ activityId: '' })
const drawer = reactive({
  visible: false,
  type: '',
  mode: ''
})
const activityForm = reactive({
  title: '',
  location: '',
  category: 'PUBLIC_WELFARE',
  startDate: '',
  startClock: '',
  endDate: '',
  endClock: '',
  status: '',
  durationHours: '',
  maxParticipants: '',
  organizerName: '',
  description: ''
})
const aiActivityPrompt = ref('')
const aiActivityGenerating = ref(false)
const aiActivityError = ref('')
const activityNotificationTitle = ref('')
const activityNotificationContent = ref('')
const userForm = reactive({
  username: '',
  realName: '',
  phone: '',
  email: '',
  collegeName: '',
  role: '',
  studentNo: '',
  status: ''
})
const profileForm = reactive({
  realName: '',
  gender: '',
  collegeName: '',
  major: '',
  grade: '',
  className: '',
  politicalStatus: '',
  dormArea: '',
  phone: '',
  birthDate: '',
  emergencyContact: '',
  emergencyPhone: ''
})
const profileTags = ref([])
const profileComplete = ref(false)
const profileError = ref('')
const bindStudentForm = reactive({
  studentId: '',
  password: ''
})
const studentChecking = ref(false)
const studentBinding = ref(false)
const studentAvailability = ref('')
const studentError = ref('')
const violationForm = reactive({
  userId: '',
  activityId: '',
  violationType: 'ABSENCE',
  violationReason: '',
  restrictionType: 'ACTIVITY_REGISTER',
  restrictionDays: 7
})

const navIconColor = 'ffffff'
const navIcon = name => `https://api.iconify.design/lucide:${name}.svg?color=%23${navIconColor}`
const todayDate = new Date().toISOString().slice(0, 10)
const profileGenderOptions = [
  { label: '男', value: 'M' },
  { label: '女', value: 'F' }
]
const profilePoliticalOptions = ['群众', '共青团员', '中共党员', '中共预备党员', '其他']
const profileServiceTags = ['校园服务', '社区服务', '应急保障', '文化活动', '环保公益', '助老助残']
const activityCategoryOptions = [
  { label: '公益服务', value: 'PUBLIC_WELFARE' },
  { label: '社区服务', value: 'COMMUNITY' },
  { label: '校园服务', value: 'CAMPUS' },
  { label: '环保行动', value: 'ENVIRONMENT' },
  { label: '教育支持', value: 'EDUCATION' },
  { label: '文化活动', value: 'CULTURE' },
  { label: '应急保障', value: 'EMERGENCY' },
  { label: '健康服务', value: 'HEALTH' },
  { label: '助老服务', value: 'ELDERLY' },
  { label: '医疗辅助', value: 'MEDICAL' },
  { label: '其他服务', value: 'OTHER' }
]

const sections = [
  { type: 'section', key: 'overview', label: '运营概览', icon: navIcon('layout-dashboard') },
  { type: 'section', key: 'activities', label: '活动管理', icon: navIcon('calendar-days') },
  { type: 'section', key: 'users', label: '用户学院', icon: navIcon('users') },
  { type: 'section', key: 'attendance', label: '考勤失信', icon: navIcon('scan-line') },
  { type: 'section', key: 'reports', label: '统计报表', icon: navIcon('chart-column') },
  { type: 'section', key: 'announcements', label: '公告发布', icon: navIcon('megaphone') },
  { type: 'section', key: 'audit', label: '审计日志', icon: navIcon('shield-check') }
]



const currentSection = computed(() => sections.find(item => item.key === activeSection.value) || sections[0])
const roleLabel = computed(() => userStore.userInfo.role || '未登录')
const currentStudentNo = computed(() => userStore.userInfo.studentNo || userStore.userInfo.studentId || '')
const profileGenderIndex = computed(() => {
  const index = profileGenderOptions.findIndex(item => item.value === profileForm.gender)
  return index >= 0 ? index : 0
})
const profileGenderLabel = computed(() => profileGenderOptions.find(item => item.value === profileForm.gender)?.label || '')
const profilePoliticalIndex = computed(() => {
  const index = profilePoliticalOptions.findIndex(item => item === profileForm.politicalStatus)
  return index >= 0 ? index : 0
})
const profileRequiredInfoItems = computed(() => [
  currentStudentNo.value,
  profileForm.phone,
  profileForm.birthDate,
  profileForm.emergencyContact,
  profileForm.emergencyPhone
])
const profileRequiredCompletedCount = computed(() => profileRequiredInfoItems.value.filter(Boolean).length)
const profileRequiredComplete = computed(() => profileRequiredCompletedCount.value === profileRequiredInfoItems.value.length)
const profileStatusText = computed(() => profileRequiredComplete.value ? '完整' : '待完善')
const profileStatusClass = computed(() => profileRequiredComplete.value ? 'tag--blue' : 'tag--amber')
const normalizedStudentId = computed(() => bindStudentForm.studentId.trim())
const normalizedBindPassword = computed(() => bindStudentForm.password.trim())
const studentAvailabilityText = computed(() => {
  if (currentStudentNo.value) return '当前账号已绑定学号'
  if (studentAvailability.value === 'available') return '该学号可绑定'
  if (studentAvailability.value === 'bound') return '该学号已被其他账号绑定'
  return ''
})
const studentAvailabilityClass = computed(() => {
  if (currentStudentNo.value || studentAvailability.value === 'available') return 'success'
  if (studentAvailability.value === 'bound') return 'danger'
  return ''
})
const bindStudentDisabled = computed(() => (
  studentBinding.value ||
  Boolean(currentStudentNo.value) ||
  !/^[a-zA-Z0-9]{6,20}$/.test(normalizedStudentId.value) ||
  !normalizedBindPassword.value ||
  studentAvailability.value === 'bound'
))
const drawerTitle = computed(() => {
  if (drawer.type === 'activity') {
    if (drawer.mode === 'create') return '新建活动'
    return selectedActivity.value?.title || selectedActivity.value?.name || '活动详情'
  }
  if (drawer.type === 'user') return displayUserName(selectedUser.value)
  if (drawer.type === 'profile') return '补全个人档案'
  if (drawer.type === 'bindStudent') return '绑定学号'
  return '详情'
})
const drawerSubtitle = computed(() => {
  if (drawer.type === 'activity') {
    return drawer.mode === 'create' ? '填写活动基础信息，创建后进入草稿状态' : '在管理端抽屉内查看与维护活动信息'
  }
  if (drawer.type === 'user') return '在管理端抽屉内查看与维护用户信息'
  if (drawer.type === 'profile') return '管理端内完成必要资料补全，不跳转到客户端页面'
  if (drawer.type === 'bindStudent') return '无需离开管理端工作台即可完成账号身份补全'
  return ''
})
const checkinRateText = computed(() => {
  const value = checkinRate.value && typeof checkinRate.value === 'object' ? checkinRate.value.rate : checkinRate.value
  if (value === null || value === undefined || value === '') return '--'
  const numeric = Number(value)
  if (Number.isNaN(numeric)) return '--'
  const percent = numeric > 1 ? numeric : numeric * 100
  return `${Math.round(Math.max(0, Math.min(100, percent)))}%`
})
const trendBars = computed(() => buildRecentTrendBars(trendData.value))
const trendTotal = computed(() => trendBars.value.reduce((total, item) => total + item.value, 0))
const trendActiveBars = computed(() => trendBars.value.filter(item => item.value > 0))
const trendSummaryText = computed(() => {
  if (trendTotal.value === 0) return '近 7 天暂无新增活动'
  if (trendActiveBars.value.length === 1) {
    const item = trendActiveBars.value[0]
    return `近 7 天仅 ${item.label} 新增 ${item.value} 个活动`
  }
  const peak = trendBars.value.reduce((max, item) => item.value > max.value ? item : max, trendBars.value[0])
  return `近 7 天 ${trendActiveBars.value.length} 天有新增活动，峰值 ${peak.value} 个`
})
const overviewActivityCount = computed(() => {
  const value = dashboard.value?.totalActivities ?? activityTotal.value ?? activities.value.length
  return Number(value || 0)
})
const refreshLocked = computed(() => loading.value || Boolean(refreshingSection.value))
const refreshButtonText = computed(() => refreshLocked.value ? '刷新中' : '刷新')
const activityCategoryIndex = computed(() => {
  const index = activityCategoryOptions.findIndex(item => item.value === activityForm.category)
  return index >= 0 ? index : 0
})
const activityCategoryLabel = computed(() => (
  activityCategoryOptions.find(item => item.value === activityForm.category)?.label || ''
))
const activityDrawerModeText = computed(() => drawer.mode === 'create' ? '活动新建' : '活动信息')
const activityDrawerStatus = computed(() => activityForm.status || selectedActivity.value?.status || 'DRAFT')
const activityDrawerHeroTitle = computed(() => {
  const formTitle = activityForm.title.trim()
  if (formTitle) return formTitle
  if (drawer.mode === 'create') return '新建志愿服务活动'
  return selectedActivity.value?.title || selectedActivity.value?.name || '未命名活动'
})
const activityDrawerHeroMeta = computed(() => {
  const location = activityForm.location.trim() || '地点待定'
  return `${location} · ${activityCategoryLabel.value || '未分类'}`
})
const activityDrawerTimeText = computed(() => {
  const startTime = combineActivityDateTime(activityForm.startDate, activityForm.startClock)
  const endTime = combineActivityDateTime(activityForm.endDate, activityForm.endClock)
  return `${formatTime(startTime)} - ${formatTime(endTime)}`
})
const activityDrawerCapacityText = computed(() => {
  const current = selectedActivity.value?.currentParticipants || selectedActivity.value?.participantCount || 0
  const max = activityForm.maxParticipants || selectedActivity.value?.maxParticipants || '不限'
  return `${current}/${max}`
})
const attendanceActivityOptions = computed(() => attendanceActivities.value
  .map(activity => ({
    label: attendanceActivityOptionLabel(activity),
    value: String(activity.id || activity.activityId || ''),
    activity
  }))
  .filter(item => item.value)
)
const attendanceActivityIndex = computed(() => {
  const index = attendanceActivityOptions.value.findIndex(item => item.value === String(attendanceForm.activityId || ''))
  return index >= 0 ? index : 0
})
const attendanceActivityLabel = computed(() => {
  const option = attendanceActivityOptions.value.find(item => item.value === String(attendanceForm.activityId || ''))
  return option?.label || ''
})
const attendanceActivityPlaceholder = computed(() => {
  if (attendanceActivitiesLoading.value) return '活动加载中'
  if (!attendanceActivityOptions.value.length) return '暂无可选活动'
  return '请选择活动'
})



function listFrom(result) {
  if (Array.isArray(result)) return result
  return result?.records || result?.list || []
}

function formatTime(value) {
  if (!value) return '时间待定'
  return String(value).replace('T', ' ').slice(0, 16)
}

function padDatePart(value) {
  return String(value).padStart(2, '0')
}

function dateKey(date) {
  return `${date.getFullYear()}-${padDatePart(date.getMonth() + 1)}-${padDatePart(date.getDate())}`
}

function normalizeTrendDate(value) {
  if (!value) return ''
  const match = String(value).match(/\d{4}-\d{1,2}-\d{1,2}/)
  if (!match) return ''
  const [year, month, day] = match[0].split('-')
  return `${year}-${padDatePart(month)}-${padDatePart(day)}`
}

function trendValue(item) {
  const value = item?.count ?? item?.value ?? item?.total ?? 0
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : 0
}

function buildRecentTrendBars(source = []) {
  const valuesByDate = new Map()
  source.forEach(item => {
    const key = normalizeTrendDate(item?.date || item?.name || item?.day)
    if (!key) return
    valuesByDate.set(key, (valuesByDate.get(key) || 0) + trendValue(item))
  })

  const today = new Date()
  return Array.from({ length: 7 }, (_, index) => {
    const offset = 6 - index
    const day = new Date(today)
    day.setHours(0, 0, 0, 0)
    day.setDate(today.getDate() - offset)
    const key = dateKey(day)
    return {
      date: key,
      label: offset === 0 ? '今日' : `${day.getMonth() + 1}/${day.getDate()}`,
      value: valuesByDate.get(key) || 0
    }
  })
}

function statusText(status) {
  const map = {
    DRAFT: '草稿',
    AUDITING: '审核中',
    RECRUITING: '招募中',
    PUBLISHED: '已发布',
    APPROVED: '已通过',
    ONGOING: '进行中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REJECTED: '已拒绝'
  }
  return map[status] || status || '未知'
}

function statusClass(status) {
  if (['CANCELLED', 'REJECTED'].includes(status)) return 'tag--red'
  if (['COMPLETED', 'APPROVED', 'PUBLISHED'].includes(status)) return 'tag--blue'
  if (['AUDITING', 'DRAFT'].includes(status)) return 'tag--amber'
  return ''
}

function attendanceActivityOptionLabel(activity = {}) {
  const title = activity.title || activity.name || '未命名活动'
  const location = activity.location || '地点待定'
  return `${title} · ${location} · ${statusText(activity.status)}`
}

function displayUserName(user) {
  return user?.realName || user?.username || '未命名用户'
}

function userInitials(user) {
  return displayUserName(user).slice(0, 1)
}

function userStatusText(user) {
  const value = user?.status ?? user?.enabled ?? user?.accountStatus
  if (value === false || value === 0 || value === 'DISABLED' || value === 'LOCKED') return '已停用'
  if (value === 'PENDING') return '待审核'
  return '正常'
}

function userStatusClass(user) {
  const text = userStatusText(user)
  if (text === '已停用') return 'tag--red'
  if (text === '待审核') return 'tag--amber'
  return 'tag--blue'
}

function numberOrUndefined(value) {
  if (value === '' || value === null || value === undefined) return undefined
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : undefined
}

function formatLocalDateTime(date) {
  return [
    date.getFullYear(),
    padDatePart(date.getMonth() + 1),
    padDatePart(date.getDate())
  ].join('-') + `T${padDatePart(date.getHours())}:${padDatePart(date.getMinutes())}:00`
}

function defaultActivityTimes() {
  const start = new Date()
  start.setDate(start.getDate() + 1)
  start.setHours(9, 0, 0, 0)
  const end = new Date(start)
  end.setHours(11, 0, 0, 0)
  return {
    startTime: formatLocalDateTime(start),
    endTime: formatLocalDateTime(end)
  }
}

function createActivityDraft() {
  const { startTime, endTime } = defaultActivityTimes()
  return {
    title: '',
    location: '',
    category: 'PUBLIC_WELFARE',
    status: 'DRAFT',
    startTime,
    endTime,
    durationHours: 2,
    maxParticipants: 30,
    organizerName: userStore.userInfo.realName || userStore.userInfo.username || '',
    description: ''
  }
}

function splitActivityDateTime(value) {
  if (!value) return { date: '', clock: '' }
  const normalized = String(value).replace(' ', 'T')
  const [date = '', time = ''] = normalized.split('T')
  return {
    date,
    clock: time.slice(0, 5)
  }
}

function combineActivityDateTime(date, clock) {
  if (!date || !clock) return ''
  return `${date}T${clock}:00`
}

function validateActivityTimeRange(startTime, endTime) {
  if (!startTime) return '请选择活动开始时间'
  if (!endTime) return '请选择活动结束时间'
  if (new Date(startTime).getTime() >= new Date(endTime).getTime()) return '结束时间必须晚于开始时间'
  return ''
}

function validateActivityDrawer() {
  const title = activityForm.title.trim()
  const location = activityForm.location.trim()
  const startTime = combineActivityDateTime(activityForm.startDate, activityForm.startClock)
  const endTime = combineActivityDateTime(activityForm.endDate, activityForm.endClock)
  const timeError = validateActivityTimeRange(startTime, endTime)
  const duration = numberOrUndefined(activityForm.durationHours)
  const maxParticipants = numberOrUndefined(activityForm.maxParticipants)

  if (!title) return '请输入活动名称'
  if (!location) return '请输入活动地点'
  if (!activityForm.category) return '请选择活动分类'
  if (timeError) return timeError
  if (duration === undefined || duration < 0.5 || duration > 24) return '服务时长需在 0.5 到 24 小时之间'
  if (maxParticipants === undefined || maxParticipants < 1 || maxParticipants > 1000) return '名额上限需在 1 到 1000 之间'
  return ''
}

function buildActivityPayload() {
  const title = sanitizeInput(activityForm.title, 255)
  const description = sanitizeInput(activityForm.description, 5000)
  const organizerName = sanitizeInput(activityForm.organizerName, 80)

  return {
    title,
    location: sanitizeInput(activityForm.location, 512),
    category: activityForm.category || 'PUBLIC_WELFARE',
    startTime: combineActivityDateTime(activityForm.startDate, activityForm.startClock),
    endTime: combineActivityDateTime(activityForm.endDate, activityForm.endClock),
    status: activityForm.status || 'DRAFT',
    durationHours: numberOrUndefined(activityForm.durationHours),
    maxParticipants: numberOrUndefined(activityForm.maxParticipants),
    leaderName: organizerName,
    description
  }
}

function assignActivityForm(activity = {}) {
  activityForm.title = activity.title || activity.name || ''
  activityForm.location = activity.location || ''
  activityForm.category = activity.category || 'PUBLIC_WELFARE'
  const start = splitActivityDateTime(activity.startTime)
  const end = splitActivityDateTime(activity.endTime)
  activityForm.startDate = start.date
  activityForm.startClock = start.clock
  activityForm.endDate = end.date
  activityForm.endClock = end.clock
  activityForm.status = activity.status || 'DRAFT'
  activityForm.durationHours = activity.durationHours ?? activity.duration ?? ''
  activityForm.maxParticipants = activity.maxParticipants ?? ''
  activityForm.organizerName = activity.organizerName || activity.leaderName || ''
  activityForm.description = activity.description || activity.content || ''
}

function limitGeneratedText(value, maxLength) {
  if (!value) return ''
  return String(value).trim().substring(0, maxLength)
}

function currentActivityDraftContext() {
  return {
    title: activityForm.title,
    location: activityForm.location,
    category: activityForm.category,
    startTime: combineActivityDateTime(activityForm.startDate, activityForm.startClock),
    endTime: combineActivityDateTime(activityForm.endDate, activityForm.endClock),
    durationHours: numberOrUndefined(activityForm.durationHours),
    maxParticipants: numberOrUndefined(activityForm.maxParticipants),
    organizerName: activityForm.organizerName,
    description: activityForm.description,
    notificationTitle: activityNotificationTitle.value,
    notificationContent: activityNotificationContent.value
  }
}

function normalizeGeneratedActivityCategory(value) {
  if (!value) return activityForm.category || 'PUBLIC_WELFARE'
  const raw = String(value).trim()
  const byValue = activityCategoryOptions.find(item => item.value === raw.toUpperCase())
  if (byValue) return byValue.value
  const byLabel = activityCategoryOptions.find(item => item.label === raw)
  return byLabel?.value || activityForm.category || 'PUBLIC_WELFARE'
}

function applyGeneratedActivityDraft(draft = {}) {
  if (draft.title || draft.activityName || draft.name) {
    activityForm.title = sanitizeInput(draft.title || draft.activityName || draft.name, 255)
  }
  if (draft.location || draft.address) {
    activityForm.location = sanitizeInput(draft.location || draft.address, 512)
  }
  activityForm.category = normalizeGeneratedActivityCategory(draft.category || draft.activityCategory)

  const start = splitActivityDateTime(draft.startTime || draft.start_time)
  const end = splitActivityDateTime(draft.endTime || draft.end_time)
  if (start.date && start.clock) {
    activityForm.startDate = start.date
    activityForm.startClock = start.clock
  }
  if (end.date && end.clock) {
    activityForm.endDate = end.date
    activityForm.endClock = end.clock
  }

  const duration = numberOrUndefined(draft.durationHours ?? draft.duration)
  if (duration !== undefined) {
    activityForm.durationHours = duration
  }
  const maxParticipants = numberOrUndefined(draft.maxParticipants ?? draft.participantLimit ?? draft.capacity)
  if (maxParticipants !== undefined) {
    activityForm.maxParticipants = Math.round(maxParticipants)
  }
  if (draft.organizerName || draft.leaderName || draft.organizer) {
    activityForm.organizerName = sanitizeInput(draft.organizerName || draft.leaderName || draft.organizer, 80)
  }
  if (draft.description || draft.content || draft.summary) {
    activityForm.description = sanitizeInput(draft.description || draft.content || draft.summary, 5000)
  }
  if (draft.notificationTitle || draft.noticeTitle || draft.announcementTitle) {
    activityNotificationTitle.value = limitGeneratedText(draft.notificationTitle || draft.noticeTitle || draft.announcementTitle, 255)
  }
  if (draft.notificationContent || draft.noticeContent || draft.announcementContent) {
    activityNotificationContent.value = limitGeneratedText(draft.notificationContent || draft.noticeContent || draft.announcementContent, 8000)
  }
}

function clearActivityDraftPrompt() {
  aiActivityPrompt.value = ''
  aiActivityError.value = ''
}

function resetActivityNotificationDraft() {
  activityNotificationTitle.value = ''
  activityNotificationContent.value = ''
}

function copyActivityNotification() {
  if (!activityNotificationContent.value) return
  uni.setClipboardData({
    data: activityNotificationContent.value,
    success: () => {
      uni.showToast({ title: '通知文案已复制', icon: 'success' })
    }
  })
}

async function generateActivityDraft() {
  if (aiActivityGenerating.value) return
  const description = sanitizeInput(aiActivityPrompt.value || activityForm.description || activityForm.title, 1200)
  if (!description) {
    aiActivityError.value = '请先输入活动描述'
    return
  }

  aiActivityGenerating.value = true
  aiActivityError.value = ''
  try {
    const draft = await aiApi.generateActivityDraft({
      description,
      currentDraft: currentActivityDraftContext()
    }, {
      showLoading: false,
      showErrorToast: false,
      skipAccessRedirect: true,
      deduplicate: false
    })
    applyGeneratedActivityDraft(draft || {})
    uni.showToast({ title: draft?.aiGenerated === false ? '已生成草稿' : '已回填表单', icon: 'success' })
  } catch (err) {
    aiActivityError.value = err?.message || '生成失败，请稍后重试'
  } finally {
    aiActivityGenerating.value = false
  }
}

function selectActivityCategory(event) {
  const index = Number(event.detail.value)
  activityForm.category = activityCategoryOptions[index]?.value || 'PUBLIC_WELFARE'
}

function selectActivityStartDate(event) {
  activityForm.startDate = event.detail.value
}

function selectActivityStartClock(event) {
  activityForm.startClock = event.detail.value
}

function selectActivityEndDate(event) {
  activityForm.endDate = event.detail.value
}

function selectActivityEndClock(event) {
  activityForm.endClock = event.detail.value
}

function assignUserForm(user = {}) {
  userForm.username = user.username || ''
  userForm.realName = user.realName || ''
  userForm.phone = user.phone || ''
  userForm.email = user.email || ''
  userForm.collegeName = user.collegeName || user.college || ''
  userForm.role = user.role || user.roleCode || ''
  userForm.studentNo = user.studentNo || user.studentId || ''
  userForm.status = user.status || user.accountStatus || ''
}

function parseProfileTags(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return String(value)
      .split(',')
      .map(item => item.trim())
      .filter(Boolean)
  }
}

function normalizeProfileDate(value) {
  return value ? String(value).slice(0, 10) : ''
}

function resetProfileForm() {
  profileForm.realName = userStore.userInfo.realName || ''
  profileForm.gender = ''
  profileForm.collegeName = userStore.userInfo.college || ''
  profileForm.major = userStore.userInfo.major || ''
  profileForm.grade = userStore.userInfo.grade || ''
  profileForm.className = userStore.userInfo.className || ''
  profileForm.politicalStatus = ''
  profileForm.dormArea = ''
  profileForm.phone = userStore.userInfo.phone || ''
  profileForm.birthDate = ''
  profileForm.emergencyContact = ''
  profileForm.emergencyPhone = ''
  profileTags.value = []
  profileComplete.value = false
  profileError.value = ''
}

function assignProfileForm(profile = {}) {
  profileComplete.value = Boolean(profile?.profileComplete)
  profileForm.realName = profile?.realName || userStore.userInfo.realName || ''
  profileForm.gender = profile?.gender || ''
  profileForm.collegeName = profile?.collegeName || userStore.userInfo.college || ''
  profileForm.major = profile?.major || userStore.userInfo.major || ''
  profileForm.grade = profile?.grade || userStore.userInfo.grade || ''
  profileForm.className = profile?.className || userStore.userInfo.className || ''
  profileForm.politicalStatus = profile?.politicalStatus || ''
  profileForm.dormArea = profile?.dormArea || ''
  profileForm.phone = profile?.phone || userStore.userInfo.phone || ''
  profileForm.birthDate = normalizeProfileDate(profile?.birthDate)
  profileForm.emergencyContact = profile?.emergencyContact || ''
  profileForm.emergencyPhone = profile?.emergencyPhone || ''
  profileTags.value = parseProfileTags(profile?.serviceCategoryTags)
  if (profile?.studentNo || profile?.studentId) {
    userStore.updateUser({ studentNo: profile.studentNo || profile.studentId })
  }
}

function selectProfileGender(event) {
  const index = Number(event.detail.value)
  profileForm.gender = profileGenderOptions[index]?.value || ''
}

function selectProfileBirthDate(event) {
  profileForm.birthDate = event.detail.value
}

function selectProfilePoliticalStatus(event) {
  profileForm.politicalStatus = profilePoliticalOptions[Number(event.detail.value)] || ''
}

function toggleProfileTag(tag) {
  profileTags.value = profileTags.value.includes(tag)
    ? profileTags.value.filter(item => item !== tag)
    : [...profileTags.value, tag]
}

function validateProfileDrawer() {
  if (!sanitizeInput(profileForm.realName, 30)) return '请填写姓名'
  if (!sanitizeInput(profileForm.phone, 20)) return '请填写联系方式'
  if (!/^[0-9+\-\s]{6,20}$/.test(profileForm.phone)) return '联系方式格式不正确'
  if (!profileForm.birthDate) return '请选择出生日期'
  if (!sanitizeInput(profileForm.emergencyContact, 30)) return '请填写紧急联系人'
  if (!sanitizeInput(profileForm.emergencyPhone, 20)) return '请填写紧急联系电话'
  if (!/^[0-9+\-\s]{6,20}$/.test(profileForm.emergencyPhone)) return '紧急联系电话格式不正确'
  return ''
}

function resetBindStudentForm() {
  bindStudentForm.studentId = currentStudentNo.value || ''
  bindStudentForm.password = ''
  studentAvailability.value = currentStudentNo.value ? 'available' : ''
  studentError.value = ''
}

function validateStudentBinding() {
  if (!normalizedStudentId.value) return '请输入学号'
  if (!/^[a-zA-Z0-9]{6,20}$/.test(normalizedStudentId.value)) return '学号应为 6-20 位字母或数字'
  if (!normalizedBindPassword.value) return '请输入当前账号密码'
  return ''
}

function clearStudentAvailability() {
  studentAvailability.value = ''
  studentError.value = ''
}

function trendBarHeight(value) {
  const max = Math.max(...trendBars.value.map(item => item.value), 1)
  if (!value) return 4
  return Math.max(18, Math.round((Number(value || 0) / max) * 100))
}

function barHeight(value) {
  const values = categoryData.value.map(item => Number(item.value || 0))
  const max = Math.max(...values, 1)
  return Math.max(8, Math.round((Number(value || 0) / max) * 100))
}

async function safeLoad(loader) {
  try {
    return await loader()
  } catch (err) {
    return null
  }
}

async function loadActivities(options = {}) {
  const { setPageLoading = true, notify = true, showErrorToast = true, force = false } = options
  const now = Date.now()
  if (activityRequestPromise) {
    const result = await activityRequestPromise
    if (setPageLoading) uni.stopPullDownRefresh()
    return result
  }
  if (!force && now - lastActivityRequestAt < activityRequestCooldownMs) {
    if (notify) {
      uni.showToast({ title: '刷新太频繁，请稍后再试', icon: 'none' })
    }
    if (setPageLoading) uni.stopPullDownRefresh()
    return null
  }

  lastActivityRequestAt = now
  if (setPageLoading) loading.value = true
  error.value = ''

  const requestPromise = (async () => {
    const res = await activityApi.getActivityList({
      pageNum: activityQuery.pageNum,
      pageSize: activityQuery.pageSize,
      keyword: activityQuery.keyword.trim(),
      status: activityQuery.status.trim()
    }, { showLoading: false, showErrorToast })
    activities.value = listFrom(res)
    activityTotal.value = res?.total || activities.value.length
    return res
  })()

  activityRequestPromise = requestPromise
  try {
    return await requestPromise
  } catch (err) {
    activities.value = []
    error.value = err?.message || '活动列表加载失败'
    return null
  } finally {
    if (activityRequestPromise === requestPromise) activityRequestPromise = null
    if (setPageLoading) {
      loading.value = false
      uni.stopPullDownRefresh()
    }
  }
}

async function loadAttendanceActivities(force = false) {
  if (attendanceActivitiesLoading.value) return
  if (!force && attendanceActivityOptions.value.length) return

  attendanceActivitiesLoading.value = true
  try {
    const res = await activityApi.getActivityList({
      pageNum: 1,
      pageSize: 100
    }, {
      showLoading: false,
      showErrorToast: false
    })
    attendanceActivities.value = listFrom(res)
    const selectedStillExists = attendanceActivityOptions.value.some(
      item => item.value === String(attendanceForm.activityId || '')
    )
    if (attendanceForm.activityId && !selectedStillExists) {
      attendanceForm.activityId = ''
      clearQRCodeResult()
    }
  } catch (err) {
    attendanceActivities.value = []
    if (force) {
      uni.showToast({ title: err?.message || '活动加载失败', icon: 'none' })
    }
  } finally {
    attendanceActivitiesLoading.value = false
  }
}

async function loadUsers() {
  try {
    const keyword = userQuery.keyword.trim()
    const res = await userApi.getUserList({
      pageNum: userQuery.pageNum,
      pageSize: userQuery.pageSize,
      username: keyword || undefined
    }, { showLoading: false, showErrorToast: false })
    users.value = listFrom(res)
    userTotal.value = res?.total || users.value.length
  } catch (err) {
    users.value = []
    userTotal.value = 0
  }
}

async function loadUserDetail(user) {
  if (!user) return
  const id = user.id || user.userId
  selectedUser.value = { ...user }
  assignUserForm(selectedUser.value)
  if (!id) return

  drawerLoading.value = true
  try {
    const detail = await userApi.getUserDetail(id, { showLoading: false, showErrorToast: false })
    selectedUser.value = { ...user, ...(detail || {}) }
    assignUserForm(selectedUser.value)
  } catch (err) {
    selectedUser.value = { ...user }
    assignUserForm(selectedUser.value)
  } finally {
    drawerLoading.value = false
  }
}

async function loadActivityDetail(activity) {
  if (!activity) return
  const id = activity.id || activity.activityId
  selectedActivity.value = { ...activity }
  assignActivityForm(selectedActivity.value)
  if (!id) return

  drawerLoading.value = true
  try {
    const detail = await activityApi.getActivityDetail(id, { showLoading: false, showErrorToast: false })
    selectedActivity.value = { ...activity, ...(detail || {}) }
    assignActivityForm(selectedActivity.value)
  } catch (err) {
    selectedActivity.value = { ...activity }
    assignActivityForm(selectedActivity.value)
  } finally {
    drawerLoading.value = false
  }
}

async function loadDashboard() {
  const data = await dashboardApi.getDashboardData()
  dashboard.value = data || {}
  activityTotal.value = Number(data?.totalActivities ?? activityTotal.value ?? 0)
  userTotal.value = Number(data?.totalUsers ?? userTotal.value ?? 0)
  if (Array.isArray(data?.activityTrend)) trendData.value = data.activityTrend
  if (Array.isArray(data?.categoryDistribution)) categoryData.value = data.categoryDistribution
}

async function loadCheckinRate() {
  checkinRate.value = await statisticsApi.getCheckinRate()
}

async function loadReports() {
  const [trend, category, rate] = await Promise.allSettled([
    statisticsApi.getActivityTrend({ period: 'DAY' }),
    statisticsApi.getActivityCategory({}),
    statisticsApi.getCheckinRate()
  ])
  trendData.value = trend.status === 'fulfilled' ? listFrom(trend.value) : []
  categoryData.value = category.status === 'fulfilled' ? listFrom(category.value) : []
  checkinRate.value = rate.status === 'fulfilled' ? rate.value : null
}

async function loadAnnouncements() {
  const res = await announcementApi.getAnnouncementPage({
    pageNum: announcementQuery.pageNum,
    pageSize: announcementQuery.pageSize,
    keyword: announcementQuery.keyword.trim()
  })
  announcements.value = listFrom(res)
}

async function loadAuditLogs() {
  try {
    const res = await auditApi.getAuditLogPage({ pageNum: 1, pageSize: 20 }, { showLoading: false, showErrorToast: false })
    auditLogs.value = listFrom(res)
  } catch (err) {
    auditLogs.value = []
  }
}

async function loadExportTasks() {
  const res = await exportApi.getExportTasks({ pageNum: 1, pageSize: 20 })
  exportTasks.value = listFrom(res)
}

async function loadOverviewData() {
  loading.value = true
  await Promise.allSettled([
    safeLoad(loadDashboard),
    safeLoad(loadCheckinRate),
    safeLoad(loadAnnouncements),
    safeLoad(loadExportTasks)
  ])
  loading.value = false
}

async function loadSectionData(section = activeSection.value) {
  try {
    if (section === 'overview') {
      await loadOverviewData()
      return
    }
    if (section === 'activities') {
      await loadActivities()
      return
    }
    if (section === 'users') {
      await loadUsers()
      return
    }
    if (section === 'attendance') {
      await loadAttendanceActivities()
      return
    }
    if (section === 'reports') {
      loading.value = true
      await Promise.allSettled([safeLoad(loadReports), safeLoad(loadExportTasks)])
      loading.value = false
      return
    }
    if (section === 'announcements') {
      await loadAnnouncements()
      return
    }
    if (section === 'audit') {
      await loadAuditLogs()
    }
  } finally {
    lastSectionRefreshAt[section] = Date.now()
  }
}

async function loadAdminData() {
  await loadSectionData(activeSection.value)
}

async function refreshCurrentSection() {
  const section = activeSection.value
  if (refreshLocked.value) return

  const lastRefreshAt = lastSectionRefreshAt[section] || 0
  if (Date.now() - lastRefreshAt < refreshCooldownMs) {
    uni.showToast({ title: '刚刚刷新过，请稍后再试', icon: 'none' })
    return
  }

  refreshingSection.value = section
  try {
    await loadSectionData(section)
  } finally {
    refreshingSection.value = ''
  }
}

function goDetail(id) {
  if (!id) return
  const activity = activities.value.find(item => String(item.id || item.activityId) === String(id)) || { id }
  openActivityDrawer(activity)
}

function isActiveNavItem(item) {
  if (item.type === 'section') return activeSection.value === item.key
  return false
}

function handleNavItem(item) {
  activeSection.value = item.key
  loadSectionData(item.key)
}

async function openActivityDrawer(activity) {
  drawer.type = 'activity'
  drawer.mode = 'edit'
  drawer.visible = true
  await loadActivityDetail(activity)
}

function openCreateActivityDrawer() {
  selectedActivity.value = null
  assignActivityForm(createActivityDraft())
  clearActivityDraftPrompt()
  resetActivityNotificationDraft()
  drawer.type = 'activity'
  drawer.mode = 'create'
  drawer.visible = true
  drawerLoading.value = false
}

async function openUserDrawer(user) {
  drawer.type = 'user'
  drawer.mode = ''
  drawer.visible = true
  await loadUserDetail(user)
}

async function openProfileDrawer() {
  userStore.loadFromStorage()
  resetProfileForm()
  drawer.type = 'profile'
  drawer.mode = ''
  drawer.visible = true
  drawerLoading.value = true
  try {
    const profile = await profileApi.getProfileStatus({
      showLoading: false,
      showErrorToast: false,
      skipAccessRedirect: true
    })
    assignProfileForm(profile)
  } catch (err) {
    profileError.value = err?.message || '资料加载失败'
  } finally {
    drawerLoading.value = false
  }
}

function openBindStudentDrawer() {
  userStore.loadFromStorage()
  resetBindStudentForm()
  drawer.type = 'bindStudent'
  drawer.mode = ''
  drawer.visible = true
  drawerLoading.value = false
}

function closeDrawer() {
  drawer.visible = false
}

async function checkDrawerStudentId() {
  studentError.value = validateStudentBinding()
  if (studentError.value) return

  studentChecking.value = true
  try {
    const result = await authApi.checkStudentId({ studentId: normalizedStudentId.value }, {
      showLoading: false,
      showErrorToast: false,
      skipAccessRedirect: true
    })
    studentAvailability.value = result?.isBound ? 'bound' : 'available'
  } catch (err) {
    studentError.value = err?.message || '学号检查失败，请稍后再试'
  } finally {
    studentChecking.value = false
  }
}

async function saveProfileDrawer() {
  profileError.value = validateProfileDrawer()
  if (profileError.value || drawerSaving.value) return

  drawerSaving.value = true
  try {
    const result = await profileApi.updateProfile({
      realName: sanitizeInput(profileForm.realName, 30),
      gender: profileForm.gender,
      major: sanitizeInput(profileForm.major, 40),
      grade: sanitizeInput(profileForm.grade, 20),
      className: sanitizeInput(profileForm.className, 40),
      politicalStatus: sanitizeInput(profileForm.politicalStatus, 20),
      dormArea: sanitizeInput(profileForm.dormArea, 60),
      phone: sanitizeInput(profileForm.phone, 20),
      birthDate: profileForm.birthDate,
      emergencyContact: sanitizeInput(profileForm.emergencyContact, 30),
      emergencyPhone: sanitizeInput(profileForm.emergencyPhone, 20),
      serviceCategoryTags: JSON.stringify(profileTags.value)
    }, {
      showLoading: false,
      showErrorToast: false,
      skipAccessRedirect: true
    })
    profileComplete.value = Boolean(result?.profileComplete ?? profileRequiredComplete.value)
    userStore.updateUser({
      realName: profileForm.realName,
      phone: profileForm.phone,
      major: profileForm.major,
      className: profileForm.className,
      grade: profileForm.grade
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
    closeDrawer()
  } catch (err) {
    if (err?.type === 'STUDENT_ID_REQUIRED') {
      openBindStudentDrawer()
      return
    }
    profileError.value = err?.message || '保存失败，请稍后再试'
  } finally {
    drawerSaving.value = false
  }
}

async function bindStudentInDrawer() {
  studentError.value = validateStudentBinding()
  if (studentError.value || studentAvailability.value === 'bound') return

  studentBinding.value = true
  try {
    const result = await authApi.bindStudentId({
      studentId: normalizedStudentId.value,
      password: normalizedBindPassword.value
    }, {
      showLoading: false,
      showErrorToast: false,
      skipAccessRedirect: true
    })
    const studentId = result?.studentId || normalizedStudentId.value
    userStore.updateUser({
      studentNo: studentId,
      studentId,
      accessLevel: result?.accessLevel || 'full'
    })
    bindStudentForm.studentId = studentId
    bindStudentForm.password = ''
    studentAvailability.value = 'available'
    uni.showToast({ title: '绑定成功', icon: 'success' })
    closeDrawer()
  } catch (err) {
    studentError.value = err?.message || '绑定失败，请稍后再试'
  } finally {
    studentBinding.value = false
  }
}

async function saveActivityDrawer() {
  if (drawerSaving.value) return
  const isCreate = drawer.mode === 'create'
  const id = selectedActivity.value?.id || selectedActivity.value?.activityId
  if (!isCreate && !id) return

  const formError = validateActivityDrawer()
  if (formError) {
    uni.showToast({ title: formError, icon: 'none' })
    return
  }

  drawerSaving.value = true
  try {
    const payload = buildActivityPayload()

    if (isCreate) {
      const result = await activityApi.createActivity(payload)
      selectedActivity.value = { ...payload, organizerName: payload.leaderName, ...(result || {}) }
      assignActivityForm(selectedActivity.value)
      uni.showToast({ title: '活动已创建', icon: 'success' })
      closeDrawer()
      await loadActivities({ force: true, notify: false, showErrorToast: false })
      return
    }

    const result = await activityApi.updateActivity(id, payload)
    selectedActivity.value = { id, ...payload, organizerName: payload.leaderName, ...(result || {}) }
    assignActivityForm(selectedActivity.value)
    activities.value = activities.value.map(item => {
      const itemId = item.id || item.activityId
      return String(itemId) === String(id) ? { ...item, ...selectedActivity.value } : item
    })
    uni.showToast({ title: '活动信息已保存', icon: 'success' })
  } catch (err) {
    uni.showToast({ title: err?.message || (isCreate ? '创建失败' : '保存失败'), icon: 'none' })
  } finally {
    drawerSaving.value = false
  }
}

async function saveUserDrawer() {
  if (!selectedUser.value || drawerSaving.value) return
  const id = selectedUser.value.id || selectedUser.value.userId
  if (!id) return

  drawerSaving.value = true
  try {
    const payload = {
      ...selectedUser.value,
      id,
      userId: selectedUser.value.userId || id,
      username: userForm.username,
      realName: userForm.realName,
      phone: userForm.phone,
      email: userForm.email,
      collegeName: userForm.collegeName,
      college: userForm.collegeName,
      role: userForm.role,
      roleCode: userForm.role,
      studentNo: userForm.studentNo,
      status: userForm.status
    }
    const result = await userApi.updateUser(payload)
    selectedUser.value = { ...payload, ...(result || {}) }
    assignUserForm(selectedUser.value)
    users.value = users.value.map(item => {
      const itemId = item.id || item.userId
      return String(itemId) === String(id) ? { ...item, ...selectedUser.value } : item
    })
    uni.showToast({ title: '用户信息已保存', icon: 'success' })
  } catch (err) {
    uni.showToast({ title: err?.message || '保存失败', icon: 'none' })
  } finally {
    drawerSaving.value = false
  }
}

function goClientHome() {
  uni.navigateTo({ url: '/pages/client/index' })
}

async function publishActivity(id) {
  await activityApi.publishActivity(id)
  uni.showToast({ title: '已发布', icon: 'success' })
  await loadActivities()
}

function deleteActivity(id) {
  uni.showModal({
    title: '删除活动',
    content: '确认删除该活动吗？',
    success: async (res) => {
      if (!res.confirm) return
      await activityApi.deleteActivity(id)
      await loadActivities()
    }
  })
}

function clearQRCodeResult() {
  qrActivityId.value = ''
  qrToken.value = ''
  qrImageSrc.value = ''
  qrExpireText.value = ''
}

function selectAttendanceActivity(event) {
  const index = Number(event.detail.value)
  const option = attendanceActivityOptions.value[index]
  if (!option) return
  attendanceForm.activityId = option.value
  clearQRCodeResult()
}

function normalizeQRCodeResponse(result) {
  if (typeof result === 'string') {
    return {
      qrCodeToken: result,
      qrCodeImage: '',
      qrCodePayload: result,
      activityId: attendanceForm.activityId,
      expiresInSeconds: 300
    }
  }

  return {
    qrCodeToken: result?.qrCodeToken || result?.token || '',
    qrCodeImage: result?.qrCodeImage || result?.qrImage || result?.image || '',
    qrCodePayload: result?.qrCodePayload || result?.payload || '',
    activityId: result?.activityId || attendanceForm.activityId,
    expireTime: result?.expireTime || '',
    expiresInSeconds: Number(result?.expiresInSeconds || 0)
  }
}

function formatQRCodeExpire(info) {
  if (info.expireTime) return `有效期至 ${formatTime(info.expireTime)}`
  if (info.expiresInSeconds) return `${Math.round(info.expiresInSeconds / 60)} 分钟内有效`
  return '短时有效'
}

async function generateQRCode() {
  const activityId = String(attendanceForm.activityId || '').trim()
  if (!activityId) {
    uni.showToast({ title: '请选择活动', icon: 'none' })
    return
  }

  clearQRCodeResult()
  qrGenerating.value = true
  try {
    const res = await attendanceApi.generateQRCode(activityId)
    const qrInfo = normalizeQRCodeResponse(res)
    qrActivityId.value = String(qrInfo.activityId || activityId)
    qrToken.value = qrInfo.qrCodeToken
    qrImageSrc.value = qrInfo.qrCodeImage
    qrExpireText.value = formatQRCodeExpire(qrInfo)
    if (!qrImageSrc.value) {
      uni.showToast({ title: '后端未返回二维码图片，请重启后端服务', icon: 'none' })
    }
  } catch (err) {
    uni.showToast({ title: err?.message || '二维码生成失败', icon: 'none' })
  } finally {
    qrGenerating.value = false
  }
}

async function createViolation() {
  if (!violationForm.userId || !violationForm.activityId) {
    uni.showToast({ title: '请填写用户和活动 ID', icon: 'none' })
    return
  }
  await attendanceApi.createViolation({ ...violationForm })
  uni.showToast({ title: '已创建记录', icon: 'success' })
}

async function downloadExport(path, filename) {
  try {
    await downloadAuthenticated(path, {}, filename)
  } catch (err) {
    uni.showToast({ title: '下载失败', icon: 'none' })
  }
}

onMounted(() => {
  userStore.loadFromStorage()
  uni.$on('admin:open-profile-drawer', openProfileDrawer)
  uni.$on('admin:open-bind-student-drawer', openBindStudentDrawer)
  loadSectionData('overview')
})

onUnmounted(() => {
  uni.$off('admin:open-profile-drawer', openProfileDrawer)
  uni.$off('admin:open-bind-student-drawer', openBindStudentDrawer)
})

onPullDownRefresh(async () => {
  await loadSectionData(activeSection.value)
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
$admin-ink: #2a1214;
$admin-ink-soft: #4a171d;
$admin-line: rgba(255, 226, 222, 0.16);
$admin-paper: #fffafa;
$admin-red: #a51f2b;
$admin-red-dark: #73151f;
$admin-red-soft: #fff0ee;
$admin-accent: #e08b73;
$admin-rose: #c84b5b;

.workspace-page {
  min-height: 100vh;
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  color: $color-text;
}

.admin-shell {
  display: grid;
  min-height: 100vh;
  grid-template-columns: 272px minmax(0, 1fr);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  height: 100vh;
  flex-direction: column;
  padding: 18px 14px;
  border-right: 1px solid rgba(224, 139, 115, 0.26);
  background:
    linear-gradient(180deg, rgba(224, 139, 115, 0.16), transparent 190px),
    linear-gradient(135deg, #4f1118 0%, #851c28 55%, #2a0c11 100%);
  color: #fff1ef;
  box-shadow: 12px 0 30px rgba(91, 17, 25, 0.16);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 18px;
  border-bottom: 1px solid $admin-line;
}

.brand-mark {
  display: flex;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $admin-ink;
  font-size: 18px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22);
}

.brand-name,
.brand-sub,
.admin-title,
.admin-subtitle,
.metric-label,
.metric-value,
.metric-note,
.activity-title,
.activity-meta,
.activity-count {
  display: block;
}

.brand-name {
  color: #fff;
  font-size: 16px;
  font-weight: 900;
}

.brand-sub {
  margin-top: 3px;
  color: rgba(255, 241, 239, 0.66);
  font-size: 12px;
  font-weight: 700;
}

.nav-scroll {
  min-height: 0;
  flex: 1;
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 0 6px;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-group-title {
  display: block;
  padding: 0 10px 7px;
  color: rgba(255, 241, 239, 0.48);
  font-size: 11px;
  font-weight: 900;
}

.nav-item {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 56px;
  align-items: center;
  gap: 10px;
  padding: 7px 10px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: $radius-md;
  background: rgba(255, 246, 244, 0.06);
  color: rgba(255, 241, 239, 0.78);
  font-weight: 800;
  text-align: left;
}

.nav-item::before {
  content: '';
  width: 3px;
  height: 22px;
  border-radius: 3px;
  background: transparent;
}

.nav-item:hover {
  border-color: rgba(255, 203, 191, 0.3);
  background: rgba(255, 246, 244, 0.1);
  color: #fff;
}

.nav-item.active {
  border-color: rgba(255, 214, 204, 0.5);
  background: rgba(255, 238, 234, 0.16);
  color: #fff;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.04);
}

.nav-item.active::before {
  background: #ffd4ca;
}

.nav-code {
  display: flex;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: $radius-sm;
  background: transparent;
}

.nav-item.active .nav-code {
  border-color: transparent;
  background: transparent;
  color: #fff;
}

.nav-icon {
  display: block;
  width: 16px;
  height: 16px;
}

.nav-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-main {
  min-width: 0;
  padding: 24px 28px 44px;
  overflow: hidden;
}

.topbar,
.top-actions,
.row-actions,
.todo-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topbar {
  position: sticky;
  z-index: 5;
  top: 0;
  justify-content: space-between;
  gap: 18px;
  margin: -2px 0 22px;
  padding: 14px 0 16px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: rgba(255, 248, 246, 0.92);
  backdrop-filter: blur(12px);
}

.top-actions,
.row-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.admin-main .btn {
  min-height: 34px;
  border-color: rgba(16, 35, 31, 0.16);
  background: #fff;
  color: $admin-ink;
  box-shadow: 0 1px 0 rgba(16, 35, 31, 0.04);
}

.admin-main .btn:hover {
  border-color: rgba(165, 31, 43, 0.38);
  color: $admin-red;
}

.admin-main .btn--primary {
  border-color: $admin-red;
  background: $admin-red;
  color: #fff;
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.24);
}

.admin-main .btn--primary:hover {
  border-color: $admin-red-dark;
  background: $admin-red-dark;
  color: #fff;
}

.admin-main .btn--ghost {
  border-color: transparent;
  background: transparent;
  color: $admin-red;
  box-shadow: none;
}

.admin-main .btn--danger {
  border-color: rgba(194, 65, 58, 0.2);
  background: #fff7f6;
  color: $color-red;
}

.top-actions .tag {
  border: 1px solid rgba(165, 31, 43, 0.14);
  background: $admin-red-soft;
  color: $admin-red;
}

.admin-title {
  color: $admin-ink;
  font-size: 28px;
  font-weight: 900;
  line-height: 1.2;
}

.admin-subtitle {
  margin-top: 5px;
  color: #60726d;
  font-size: 13px;
  font-weight: 700;
}

.admin-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-section .shell-card {
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card,
.panel {
  position: relative;
  overflow: hidden;
  padding: 18px;
}

.metric-card::after {
  content: '';
  position: absolute;
  right: 14px;
  bottom: 12px;
  width: 42px;
  height: 4px;
  border-radius: 4px;
  background: $admin-accent;
  opacity: 0.75;
}

.metric-card:nth-child(2)::after {
  background: $admin-rose;
}

.metric-card:nth-child(3)::after {
  background: #b83a45;
}

.metric-card:nth-child(4)::after {
  background: #e08b73;
}

.metric-label {
  color: #667772;
  font-size: 12px;
  font-weight: 800;
}

.metric-value {
  margin-top: 10px;
  color: $admin-ink;
  font-size: 30px;
  font-weight: 900;
  line-height: 1;
}

.metric-note {
  margin-top: 8px;
  color: #70807b;
  font-size: 12px;
}

.dashboard-grid,
.two-column {
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(320px, 0.72fr);
  gap: 16px;
}

.section-title {
  margin-bottom: 14px;
}

.section-title__main {
  color: $admin-ink;
  font-size: 18px;
  font-weight: 900;
}

.section-title__sub {
  color: #6d7d78;
  font-weight: 700;
}

.toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(180px, 240px) auto auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
}

.two-controls {
  grid-template-columns: minmax(260px, 1fr) auto;
}

.export-toolbar {
  grid-template-columns: repeat(3, auto);
  justify-content: start;
}

.toolbar .input,
.field .input,
.field .textarea {
  border-color: rgba(16, 35, 31, 0.12);
  background: $admin-paper;
}

.toolbar .input:focus,
.field .input:focus,
.field .textarea:focus {
  border-color: rgba(165, 31, 43, 0.42);
  background: #fff;
}

.data-table {
  overflow-x: auto;
  border: 1px solid rgba(16, 35, 31, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.data-row,
.data-head {
  min-height: 48px;
  border-bottom-color: rgba(16, 35, 31, 0.08);
}

.data-head {
  min-height: 42px;
  background: #fff0ee;
  color: #7d4046;
  font-weight: 900;
}

.data-row {
  background: #fff;
}

.data-row:hover {
  background: #fff9f7;
}

.data-main {
  color: $admin-ink;
}

.data-sub {
  color: #72827d;
}

.activity-row {
  min-width: 900px;
  grid-template-columns: minmax(280px, 1fr) 110px 100px 90px 220px;
}

.user-row {
  min-width: 740px;
  grid-template-columns: minmax(280px, 1fr) 130px 180px 90px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
}

.user-detail-grid {
  display: grid;
  grid-template-columns: minmax(320px, 0.82fr) minmax(0, 1.18fr);
  gap: 16px;
  align-items: start;
}

.user-profile-panel,
.user-info-panel {
  padding: 18px;
}

.user-profile-panel {
  overflow: hidden;
  border-color: rgba(255, 214, 204, 0.42);
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.18), transparent 190px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 18px 36px rgba(91, 17, 25, 0.16);
}

.user-profile-top {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.user-avatar {
  display: flex;
  width: 54px;
  height: 54px;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $admin-ink;
  font-size: 24px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(91, 17, 25, 0.2);
}

.user-profile-main {
  min-width: 0;
}

.detail-kicker,
.detail-name,
.detail-meta,
.user-metric__label,
.user-metric__value,
.detail-info-label,
.detail-info-value {
  display: block;
}

.detail-kicker {
  color: rgba(255, 241, 239, 0.68);
  font-size: 12px;
  font-weight: 900;
}

.detail-name {
  margin-top: 6px;
  color: #fff;
  font-size: 28px;
  font-weight: 900;
  line-height: 1.2;
  overflow-wrap: break-word;
}

.detail-meta {
  margin-top: 8px;
  color: rgba(255, 248, 246, 0.82);
  font-size: 13px;
  font-weight: 700;
  overflow-wrap: break-word;
}

.user-profile-top .tag {
  border: 1px solid rgba(255, 214, 204, 0.38);
  background: rgba(255, 244, 241, 0.16);
  color: #fff;
}

.user-detail-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 22px;
}

.user-metric {
  min-height: 78px;
  padding: 12px;
  border: 1px solid rgba(255, 214, 204, 0.18);
  border-radius: $radius-md;
  background: rgba(255, 246, 244, 0.1);
}

.user-metric__label {
  color: rgba(255, 241, 239, 0.66);
  font-size: 12px;
  font-weight: 800;
}

.user-metric__value {
  margin-top: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 900;
  line-height: 1.25;
  overflow-wrap: break-word;
}

.detail-info-list {
  display: grid;
  overflow: hidden;
  border: 1px solid rgba(16, 35, 31, 0.08);
  border-radius: $radius-md;
}

.detail-info-row {
  display: grid;
  grid-template-columns: 108px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 48px;
  padding: 0 14px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: #fffafa;
}

.detail-info-row:last-child {
  border-bottom: 0;
}

.detail-info-label {
  color: #667772;
  font-size: 12px;
  font-weight: 800;
}

.detail-info-value {
  min-width: 0;
  color: $admin-ink;
  font-weight: 800;
  overflow-wrap: break-word;
}

.announcement-row {
  min-width: 680px;
  grid-template-columns: minmax(340px, 1fr) 120px 160px;
}

.audit-row {
  min-width: 720px;
  grid-template-columns: minmax(300px, 1fr) 120px 100px 160px;
}

.trend-summary {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 10px;
  border: 1px solid rgba(165, 31, 43, 0.08);
  border-radius: $radius-md;
  background: #fff7f5;
  color: #7d4046;
  font-size: 12px;
  font-weight: 800;
}

.bar-chart {
  display: grid;
  height: 236px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
  align-items: end;
  padding: 12px 2px 0;
}

.bar-slot {
  display: flex;
  min-width: 0;
  height: 100%;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.bar-track {
  position: relative;
  display: flex;
  width: 100%;
  height: 188px;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(165, 31, 43, 0.04), transparent),
    repeating-linear-gradient(180deg, rgba(42, 18, 20, 0.08) 0 1px, transparent 1px 46px);
}

.bar-fill {
  position: relative;
  display: flex;
  width: min(34px, 64%);
  min-height: 6px;
  align-items: flex-start;
  justify-content: center;
  border-radius: $radius-sm $radius-sm 0 0;
  background: linear-gradient(180deg, #df5961, $admin-red);
  box-shadow: 0 8px 18px rgba(165, 31, 43, 0.18);
}

.bar-slot.empty .bar-fill {
  width: 12px;
  background: rgba(165, 31, 43, 0.16);
  box-shadow: none;
}

.bar-value {
  margin-top: 5px;
  color: #fff;
  font-size: 11px;
  font-weight: 900;
}

.bar-label {
  color: #7a696a;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.todo-line {
  justify-content: space-between;
  min-height: 46px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  color: $admin-ink;
  font-weight: 800;
}

.todo-line:last-child {
  border-bottom: 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.result-box {
  max-height: 220px;
  overflow: auto;
  margin-top: 12px;
  padding: 12px;
  border: 1px solid rgba(16, 35, 31, 0.08);
  border-radius: $radius-md;
  background: #f5f7f3;
  color: #4e615b;
  font-size: 12px;
  white-space: pre-wrap;
}

.attendance-actions {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
}

.attendance-actions .btn {
  width: 100%;
}

.attendance-activity-select {
  justify-content: space-between;
  gap: 12px;
}

.attendance-activity-name,
.attendance-activity-id {
  display: block;
  min-width: 0;
}

.attendance-activity-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attendance-activity-id {
  flex: 0 0 auto;
  color: #7d4046;
  font-size: 12px;
  font-weight: 900;
}

.attendance-qr-card {
  display: grid;
  grid-template-columns: 178px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  margin-top: 14px;
  padding: 16px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 244, 241, 0.92), rgba(255, 255, 255, 0.94)),
    $admin-paper;
  box-shadow: 0 14px 30px rgba(16, 35, 31, 0.08);
}

.attendance-qr-visual {
  display: flex;
  width: 178px;
  height: 178px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid rgba(16, 35, 31, 0.1);
  border-radius: $radius-md;
  background: #fff;
}

.attendance-qr-image {
  width: 156px;
  height: 156px;
}

.attendance-qr-empty {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  padding: 14px;
  color: #7a696a;
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

.attendance-qr-info,
.attendance-qr-title,
.attendance-qr-meta,
.attendance-qr-token {
  display: block;
}

.attendance-qr-info {
  min-width: 0;
}

.attendance-qr-title {
  color: $admin-ink;
  font-size: 18px;
  font-weight: 900;
}

.attendance-qr-meta {
  margin-top: 8px;
  color: #667772;
  font-size: 13px;
  font-weight: 800;
}

.attendance-qr-token {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid rgba(16, 35, 31, 0.08);
  border-radius: $radius-sm;
  background: #f5f7f3;
  color: #7d4046;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.category-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.category-line {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr) 48px;
  align-items: center;
  gap: 10px;
  color: #52655f;
  font-weight: 700;
}

.category-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e1e8e4;
}

.category-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, $admin-red, $admin-accent);
}

.drawer-layer {
  position: fixed;
  inset: 0;
  z-index: 40;
  pointer-events: auto;
}

.drawer-mask {
  position: absolute;
  inset: 0;
  background: rgba(42, 18, 20, 0.26);
  backdrop-filter: blur(2px);
}

.admin-drawer {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 30vw;
  min-width: 420px;
  max-width: 540px;
  border-left: 1px solid rgba(224, 139, 115, 0.28);
  background:
    linear-gradient(90deg, rgba(165, 31, 43, 0.055) 1px, transparent 1px) 0 0 / 44px 44px,
    linear-gradient(180deg, #fff8f6 0%, #f4eeee 100%);
  box-shadow: -20px 0 44px rgba(42, 18, 20, 0.18);
  color: $admin-ink;
}

.drawer-head {
  display: flex;
  min-height: 90px;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
  background: rgba(255, 248, 246, 0.92);
  backdrop-filter: blur(12px);
}

.drawer-kicker,
.drawer-title,
.drawer-subtitle,
.drawer-hero-title,
.drawer-hero-meta,
.summary-label,
.summary-value {
  display: block;
}

.drawer-kicker {
  color: #7d4046;
  font-size: 12px;
  font-weight: 900;
}

.drawer-title {
  margin-top: 5px;
  color: $admin-ink;
  font-size: 22px;
  font-weight: 900;
  line-height: 1.2;
  overflow-wrap: break-word;
}

.drawer-subtitle {
  margin-top: 5px;
  color: #60726d;
  font-size: 12px;
  font-weight: 700;
}

.drawer-close {
  flex: 0 0 auto;
}

.drawer-body {
  height: calc(100vh - 90px);
  padding: 16px;
}

.drawer-state {
  min-height: 240px;
  border-style: solid;
  background: rgba(255, 255, 255, 0.78);
}

.drawer-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.drawer-hero {
  display: flex;
  min-height: 132px;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  overflow: hidden;
  padding: 18px;
  border: 1px solid rgba(255, 214, 204, 0.42);
  border-radius: $radius-md;
  background:
    linear-gradient(180deg, rgba(255, 232, 226, 0.16), transparent 180px),
    linear-gradient(135deg, #ad3440 0%, #cf5c68 58%, #982934 100%);
  color: #fff1ef;
  box-shadow: 0 18px 36px rgba(91, 17, 25, 0.16);
}

.drawer-kicker--light {
  color: rgba(255, 241, 239, 0.7);
}

.drawer-hero-title {
  margin-top: 8px;
  color: #fff;
  font-size: 22px;
  font-weight: 900;
  line-height: 1.25;
  overflow-wrap: break-word;
}

.drawer-hero-meta {
  margin-top: 8px;
  color: rgba(255, 248, 246, 0.82);
  font-size: 13px;
  font-weight: 700;
  line-height: 1.45;
  overflow-wrap: break-word;
}

.drawer-hero-tag {
  flex: 0 0 auto;
  border: 1px solid rgba(255, 214, 204, 0.38);
  background: rgba(255, 244, 241, 0.16);
  color: #fff;
}

.drawer-user-head {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 12px;
}

.drawer-avatar {
  display: flex;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 214, 204, 0.65);
  border-radius: $radius-md;
  background: #fff4f1;
  color: $admin-ink;
  font-size: 20px;
  font-weight: 900;
  box-shadow: 0 10px 24px rgba(91, 17, 25, 0.2);
}

.drawer-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.summary-cell {
  min-height: 74px;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10px 22px rgba(16, 35, 31, 0.06);
}

.summary-label {
  color: #667772;
  font-size: 12px;
  font-weight: 800;
}

.summary-value {
  margin-top: 7px;
  color: $admin-ink;
  font-size: 14px;
  font-weight: 900;
  line-height: 1.35;
  overflow-wrap: break-word;
}

.drawer-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  border-color: rgba(16, 35, 31, 0.1);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 32px rgba(16, 35, 31, 0.08);
}

.drawer-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.drawer-panel .input,
.drawer-panel .textarea {
  border-color: rgba(16, 35, 31, 0.12);
  background: $admin-paper;
}

.ai-draft-box {
  display: grid;
  gap: 10px;
  padding: 12px 0 14px;
  border-bottom: 1px solid rgba(16, 35, 31, 0.08);
}

.ai-draft-input {
  min-height: 86px;
}

.ai-draft-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.notice-draft-box {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(165, 31, 43, 0.12);
  border-radius: $radius-md;
  background: #fff8f6;
}

.notice-draft-textarea {
  min-height: 220px;
  line-height: 1.6;
}

.notice-draft-actions {
  display: flex;
  justify-content: flex-start;
}

.drawer-select {
  display: flex;
  min-height: 40px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid rgba(16, 35, 31, 0.12);
  border-radius: $radius-sm;
  background: $admin-paper;
  color: $admin-ink;
  font-size: 14px;
  font-weight: 800;
}

.drawer-select--readonly {
  color: #667772;
}

.drawer-inline-control {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 82px;
  gap: 10px;
  align-items: center;
}

.drawer-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(165, 31, 43, 0.1);
  border-radius: $radius-md;
  background: rgba(255, 240, 238, 0.62);
  color: #667772;
  font-size: 13px;
  font-weight: 800;
}

.drawer-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.drawer-tag-option {
  min-height: 34px;
  margin: 0;
  padding: 0 12px;
  border: 1px solid rgba(16, 35, 31, 0.13);
  border-radius: $radius-sm;
  background: #fff;
  color: $admin-ink;
  font-size: 13px;
  font-weight: 800;
}

.drawer-tag-option.active {
  border-color: rgba(165, 31, 43, 0.32);
  background: $admin-red-soft;
  color: $admin-red;
}

.drawer-textarea {
  min-height: 132px;
}

.drawer-save {
  min-height: 42px;
  margin-top: 2px;
  font-weight: 900;
}

.drawer-result {
  display: block;
  color: #667772;
  font-size: 13px;
  font-weight: 800;
}

.drawer-result.success {
  color: #1f7a4d;
}

.drawer-result.danger {
  color: $admin-red;
}

.drawer-actions {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 10px;
}

.drawer-actions .btn {
  width: 100%;
}

@media (max-width: 1180px) {
  .admin-shell {
    grid-template-columns: 82px minmax(0, 1fr);
  }

  .brand-name,
  .brand-sub,
  .nav-label,
  .nav-group-title {
    display: none;
  }

  .admin-sidebar {
    padding: 16px 10px;
  }

  .brand {
    justify-content: center;
    padding: 8px 0 20px;
  }

  .nav-list {
    align-items: center;
    gap: 8px;
  }

  .nav-group {
    align-items: center;
    gap: 4px;
  }

  .nav-item {
    width: 50px;
    justify-content: center;
    padding: 7px 0;
  }

  .nav-item::before {
    display: none;
  }

  .topbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-grid,
  .two-column,
  .user-detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .user-profile-top {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .user-profile-top .tag {
    grid-column: 2;
    justify-self: start;
  }

  .user-detail-metrics {
    grid-template-columns: 1fr;
  }

  .admin-drawer {
    width: min(520px, calc(100vw - 82px));
    min-width: 0;
  }
}

@media (max-width: 720px) {
  .admin-drawer {
    width: 100vw;
    max-width: none;
  }

  .drawer-head {
    align-items: flex-start;
  }

  .drawer-hero,
  .drawer-summary,
  .attendance-actions,
  .attendance-qr-card,
  .drawer-actions,
  .drawer-inline-control,
  .drawer-form-grid {
    grid-template-columns: 1fr;
  }

  .attendance-qr-visual {
    justify-self: center;
  }

  .drawer-hero {
    flex-direction: column;
  }
}
</style>
