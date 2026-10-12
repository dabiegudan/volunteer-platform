export const ActivityStatus = {
  DRAFT: 'DRAFT',
  AUDITING: 'AUDITING',
  APPROVED: 'APPROVED',
  PUBLISHED: 'PUBLISHED',
  RECRUITING: 'RECRUITING',
  ONGOING: 'ONGOING',
  COMPLETED: 'COMPLETED',
  ARCHIVED: 'ARCHIVED',
  CANCELLED: 'CANCELLED',
  REJECTED: 'REJECTED'
}

export const ActivityStatusText = {
  [ActivityStatus.DRAFT]: '草稿',
  [ActivityStatus.AUDITING]: '审核中',
  [ActivityStatus.APPROVED]: '已通过',
  [ActivityStatus.PUBLISHED]: '已发布',
  [ActivityStatus.RECRUITING]: '招募中',
  [ActivityStatus.ONGOING]: '进行中',
  [ActivityStatus.COMPLETED]: '已完成',
  [ActivityStatus.ARCHIVED]: '已归档',
  [ActivityStatus.CANCELLED]: '已取消',
  [ActivityStatus.REJECTED]: '已拒绝'
}

export const ActivityStatusClass = {
  [ActivityStatus.DRAFT]: 'draft',
  [ActivityStatus.AUDITING]: 'auditing',
  [ActivityStatus.APPROVED]: 'approved',
  [ActivityStatus.PUBLISHED]: 'published',
  [ActivityStatus.RECRUITING]: 'recruiting',
  [ActivityStatus.ONGOING]: 'ongoing',
  [ActivityStatus.COMPLETED]: 'completed',
  [ActivityStatus.ARCHIVED]: 'archived',
  [ActivityStatus.CANCELLED]: 'cancelled',
  [ActivityStatus.REJECTED]: 'rejected'
}

export const RegistrationStatus = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  CHECKED_IN: 'CHECKED_IN',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
}

export const RegistrationStatusText = {
  [RegistrationStatus.PENDING]: '待审核',
  [RegistrationStatus.APPROVED]: '已通过',
  [RegistrationStatus.REJECTED]: '已拒绝',
  [RegistrationStatus.CHECKED_IN]: '已签到',
  [RegistrationStatus.COMPLETED]: '已完成',
  [RegistrationStatus.CANCELLED]: '已取消'
}

export const RoleType = {
  ADMIN: 'ADMIN',
  ORGANIZER: 'ORGANIZER',
  VOLUNTEER: 'VOLUNTEER',
  COLLEGE_ADMIN: 'COLLEGE_ADMIN',
  AUDITOR: 'AUDITOR'
}

export const RoleTypeText = {
  [RoleType.ADMIN]: '超级管理员',
  [RoleType.ORGANIZER]: '活动组织者',
  [RoleType.VOLUNTEER]: '志愿者',
  [RoleType.COLLEGE_ADMIN]: '学院管理员',
  [RoleType.AUDITOR]: '系统审核员'
}

export const ViolationType = {
  ABSENCE: 'ABSENCE',
  LATE: 'LATE',
  EARLY_LEAVE: 'EARLY_LEAVE',
  VIOLATION: 'VIOLATION'
}

export const ViolationTypeText = {
  [ViolationType.ABSENCE]: '无故缺席',
  [ViolationType.LATE]: '迟到',
  [ViolationType.EARLY_LEAVE]: '早退',
  [ViolationType.VIOLATION]: '违规行为'
}

export const CreditTransactionType = {
  EARN: 'EARN',
  SPEND: 'SPEND',
  ADJUST: 'ADJUST'
}

export const CreditTransactionTypeText = {
  [CreditTransactionType.EARN]: '获得积分',
  [CreditTransactionType.SPEND]: '消费积分',
  [CreditTransactionType.ADJUST]: '积分调整'
}

export const TaskStatus = {
  PENDING: 'PENDING',
  COMPLETED: 'COMPLETED',
  EXPIRED: 'EXPIRED'
}

export const TaskStatusText = {
  [TaskStatus.PENDING]: '待完成',
  [TaskStatus.COMPLETED]: '已完成',
  [TaskStatus.EXPIRED]: '已过期'
}

export const MessageType = {
  ACTIVITY: 'ACTIVITY',
  ATTENDANCE: 'ATTENDANCE',
  SYSTEM: 'SYSTEM',
  ACHIEVEMENT: 'ACHIEVEMENT',
  CREDIT: 'CREDIT'
}

export const MessageTypeText = {
  [MessageType.ACTIVITY]: '活动通知',
  [MessageType.ATTENDANCE]: '考勤提醒',
  [MessageType.SYSTEM]: '系统消息',
  [MessageType.ACHIEVEMENT]: '成就通知',
  [MessageType.CREDIT]: '积分通知'
}

export const MessageIcon = {
  [MessageType.ACTIVITY]: '📢',
  [MessageType.ATTENDANCE]: '✅',
  [MessageType.SYSTEM]: '💬',
  [MessageType.ACHIEVEMENT]: '🎉',
  [MessageType.CREDIT]: '⭐'
}

export const MessageBgColor = {
  [MessageType.ACTIVITY]: 'linear-gradient(135deg, #FF8A80, #F44336)',
  [MessageType.ATTENDANCE]: 'linear-gradient(135deg, #81C784, #4CAF50)',
  [MessageType.SYSTEM]: 'linear-gradient(135deg, #90CAF9, #2196F3)',
  [MessageType.ACHIEVEMENT]: 'linear-gradient(135deg, #FFD54F, #FF9800)',
  [MessageType.CREDIT]: 'linear-gradient(135deg, #CE93D8, #BA68C8)'
}

export const ActivityCategory = {
  PUBLIC_WELFARE: 'PUBLIC_WELFARE',
  ENVIRONMENT: 'ENVIRONMENT',
  COMMUNITY: 'COMMUNITY',
  EDUCATION: 'EDUCATION',
  CAMPUS: 'CAMPUS',
  ELDERLY: 'ELDERLY',
  MEDICAL: 'MEDICAL',
  HEALTH: 'HEALTH',
  EMERGENCY: 'EMERGENCY',
  CULTURE: 'CULTURE',
  OTHER: 'OTHER'
}

export const ActivityCategoryText = {
  [ActivityCategory.PUBLIC_WELFARE]: '公益服务',
  [ActivityCategory.ENVIRONMENT]: '环保',
  [ActivityCategory.COMMUNITY]: '社区服务',
  [ActivityCategory.EDUCATION]: '教育支持',
  [ActivityCategory.CAMPUS]: '校园服务',
  [ActivityCategory.ELDERLY]: '助老服务',
  [ActivityCategory.MEDICAL]: '医疗辅助',
  [ActivityCategory.HEALTH]: '健康医疗',
  [ActivityCategory.EMERGENCY]: '应急救援',
  [ActivityCategory.CULTURE]: '文体活动',
  [ActivityCategory.OTHER]: '其他'
}

export const ActivityCategoryEmoji = {
  [ActivityCategory.PUBLIC_WELFARE]: '♡',
  [ActivityCategory.ENVIRONMENT]: '🌿',
  [ActivityCategory.COMMUNITY]: '🏘️',
  [ActivityCategory.EDUCATION]: '📚',
  [ActivityCategory.CAMPUS]: '校',
  [ActivityCategory.ELDERLY]: '👴',
  [ActivityCategory.MEDICAL]: '🏥',
  [ActivityCategory.HEALTH]: '医',
  [ActivityCategory.EMERGENCY]: '!',
  [ActivityCategory.CULTURE]: '🎭',
  [ActivityCategory.OTHER]: '🏠'
}
