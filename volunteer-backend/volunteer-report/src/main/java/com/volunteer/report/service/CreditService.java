package com.volunteer.report.service;

import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.common.enums.MedalStatus;
import com.volunteer.common.enums.OrderStatus;
import com.volunteer.common.enums.TaskStatus;
import com.volunteer.report.entity.*;
import com.volunteer.report.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {
    
    private final UserCreditMapper userCreditMapper;
    private final CreditTransactionMapper transactionMapper;
    private final MedalMapper medalMapper;
    private final UserMedalMapper userMedalMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final UserTaskRecordMapper taskRecordMapper;
    private final CreditProductMapper productMapper;
    private final CreditOrderMapper orderMapper;
    private final AttendanceMapper attendanceMapper;
    
    private static final String[] LEVELS = {"L1", "L2", "L3", "L4", "L5", "L6"};
    private static final int[] LEVEL_THRESHOLDS = {0, 100, 300, 600, 1000, 1500};
    
    @Transactional
    public UserCredit getOrCreateUserCredit(Long userId) {
        UserCredit credit = userCreditMapper.findByUserId(userId);
        if (credit == null) {
            credit = new UserCredit();
            credit.setUserId(userId);
            credit.setTotalCredit(0);
            credit.setAvailableCredit(0);
            credit.setFrozenCredit(0);
            credit.setGrowthValue(0);
            credit.setLevel("L1");
            userCreditMapper.insert(credit);
        }
        return credit;
    }
    
    @Transactional
    public void addCredit(Long userId, Integer amount, String source, Long sourceId, String description) {
        UserCredit credit = getOrCreateUserCredit(userId);

        int balanceBefore = credit.getAvailableCredit();
        credit.setTotalCredit(credit.getTotalCredit() + amount);
        credit.setAvailableCredit(balanceBefore + amount);
        credit.setGrowthValue(credit.getGrowthValue() + amount);

        updateLevel(credit);
        userCreditMapper.updateById(credit);

        recordTransaction(userId, amount, "EARN", source, sourceId, description, balanceBefore, balanceBefore + amount);

        checkMedalUnlock(userId);
    }

    private void addCreditInternal(Long userId, Integer amount, String source, Long sourceId, String description) {
        UserCredit credit = getOrCreateUserCredit(userId);

        int balanceBefore = credit.getAvailableCredit();
        credit.setTotalCredit(credit.getTotalCredit() + amount);
        credit.setAvailableCredit(balanceBefore + amount);
        credit.setGrowthValue(credit.getGrowthValue() + amount);

        updateLevel(credit);
        userCreditMapper.updateById(credit);

        recordTransaction(userId, amount, "EARN", source, sourceId, description, balanceBefore, balanceBefore + amount);
    }
    
    @Transactional
    public boolean consumeCredit(Long userId, Integer amount, String source, Long sourceId, String description) {
        UserCredit credit = getOrCreateUserCredit(userId);
        
        if (credit.getAvailableCredit() < amount) {
            return false;
        }
        
        int balanceBefore = credit.getAvailableCredit();
        credit.setAvailableCredit(balanceBefore - amount);
        userCreditMapper.updateById(credit);
        
        recordTransaction(userId, -amount, "CONSUME", source, sourceId, description, balanceBefore, balanceBefore - amount);
        return true;
    }
    
    private void recordTransaction(Long userId, Integer amount, String type, String source, 
                                   Long sourceId, String description, Integer balanceBefore, Integer balanceAfter) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setSource(source);
        transaction.setSourceId(sourceId);
        transaction.setDescription(description);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transactionMapper.insert(transaction);
    }
    
    private void updateLevel(UserCredit credit) {
        int growth = credit.getGrowthValue();
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (growth >= LEVEL_THRESHOLDS[i]) {
                credit.setLevel(LEVELS[i]);
                break;
            }
        }
    }
    
    @Transactional
    public void checkMedalUnlock(Long userId) {
        getOrCreateUserCredit(userId);
        List<Medal> medals = medalMapper.findActiveMedals();
        
        for (Medal medal : medals) {
            UserMedal userMedal = userMedalMapper.findByUserIdAndMedalId(userId, medal.getId());
            if (userMedal == null) {
                userMedal = new UserMedal();
                userMedal.setUserId(userId);
                userMedal.setMedalId(medal.getId());
                userMedal.setStatus(MedalStatus.LOCKED.getCode());
                userMedal.setProgress(0.0);
                userMedal.setCurrentValue(0);
                userMedalMapper.insert(userMedal);
            }

            if (MedalStatus.LOCKED.getCode().equals(userMedal.getStatus())) {
                int currentValue = getCurrentValueForMedal(userId, medal);
                userMedal.setCurrentValue(currentValue);

                double progress = Math.min((double) currentValue / medal.getRequiredValue(), 1.0);
                userMedal.setProgress(progress);

                if (currentValue >= medal.getRequiredValue()) {
                    userMedal.setStatus(MedalStatus.OBTAINED.getCode());
                    userMedal.setObtainTime(LocalDateTime.now());
                    addCreditInternal(userId, medal.getRewardCredit(), "MEDAL", medal.getId(), "获得勋章：" + medal.getName());
                    log.info("用户 {} 获得勋章：{}", userId, medal.getName());
                }

                userMedalMapper.updateById(userMedal);
            }
        }
    }
    
    private int getCurrentValueForMedal(Long userId, Medal medal) {
        String conditionType = medal.getConditionType();
        
        return switch (conditionType) {
            case "HOURS" -> getTotalDuration(userId);
            case "COUNT" -> getActivityCount(userId);
            case "LEVEL" -> getCurrentLevelValue(userId);
            default -> 0;
        };
    }
    
    private int getTotalDuration(Long userId) {
        BigDecimal totalHours = attendanceMapper.sumApprovedDurationHours(userId);
        if (totalHours == null) {
            return 0;
        }
        return totalHours.setScale(0, RoundingMode.DOWN).intValue();
    }
    
    private int getActivityCount(Long userId) {
        return attendanceMapper.countApprovedActivities(userId);
    }
    
    private int getCurrentLevelValue(Long userId) {
        UserCredit credit = getOrCreateUserCredit(userId);
        return credit.getGrowthValue();
    }
    
    public Map<String, Object> getCreditOverview(Long userId) {
        UserCredit credit = getOrCreateUserCredit(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("totalCredit", credit.getTotalCredit());
        result.put("currentLevel", credit.getLevel());
        result.put("nextLevel", getNextLevel(credit.getLevel()));
        
        int currentIndex = Arrays.asList(LEVELS).indexOf(credit.getLevel());
        int nextThreshold = currentIndex < LEVEL_THRESHOLDS.length - 1 ? LEVEL_THRESHOLDS[currentIndex + 1] : credit.getGrowthValue();
        int currentThreshold = LEVEL_THRESHOLDS[currentIndex];
        int progressToNext = credit.getGrowthValue() - currentThreshold;
        int totalToNext = nextThreshold - currentThreshold;
        
        result.put("nextLevelThreshold", nextThreshold);
        result.put("progressToNext", totalToNext > 0 ? (double) progressToNext / totalToNext : 1.0);
        result.put("availableCredit", credit.getAvailableCredit());
        result.put("frozenCredit", credit.getFrozenCredit());
        
        LocalDate startOfWeek = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        int weekEarn = transactionMapper.sumByUserIdAndTimeRange(userId, 
                startOfWeek.atStartOfDay(), endOfWeek.atTime(23, 59, 59));
        result.put("thisWeekGain", weekEarn);
        
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate endOfMonth = LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);
        int monthEarn = transactionMapper.sumByUserIdAndTimeRange(userId, 
                startOfMonth.atStartOfDay(), endOfMonth.atTime(23, 59, 59));
        result.put("thisMonthGain", monthEarn);
        
        return result;
    }
    
    private String getNextLevel(String currentLevel) {
        int index = Arrays.asList(LEVELS).indexOf(currentLevel);
        return index < LEVELS.length - 1 ? LEVELS[index + 1] : currentLevel;
    }
    
    public List<CreditTransaction> getTransactions(Long userId, String type, String startDate, String endDate) {
        // R-08 实施:此方法保留以兼容旧调用方;Controller 已切换到 getTransactionsPage
        if (startDate != null && endDate != null) {
            LocalDateTime startTime = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime endTime = LocalDate.parse(endDate).atTime(23, 59, 59);
            return transactionMapper.findByUserIdAndTimeRange(userId, startTime, endTime);
        } else if (type != null) {
            return transactionMapper.findByUserIdAndType(userId, type);
        }
        return transactionMapper.findByUserId(userId);
    }

    /**
     * 分页查询用户积分流水(R-08 实施)
     * <p>前端默认 page=1,size=20,size 上限 100,避免 OOM 与慢响应。</p>
     */
    public Map<String, Object> getTransactionsPage(Long userId, String type, String startDate, String endDate,
                                                   int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        int offset = (safePage - 1) * safeSize;

        long total;
        List<CreditTransaction> records;
        if (startDate != null && endDate != null) {
            LocalDateTime startTime = LocalDate.parse(startDate).atStartOfDay();
            LocalDateTime endTime = LocalDate.parse(endDate).atTime(23, 59, 59);
            total = transactionMapper.countByUserIdAndTimeRange(userId, startTime, endTime);
            records = transactionMapper.findByUserIdAndTimeRangePage(userId, startTime, endTime, offset, safeSize);
        } else if (type != null) {
            total = transactionMapper.countByUserIdAndType(userId, type);
            records = transactionMapper.findByUserIdAndTypePage(userId, type, offset, safeSize);
        } else {
            total = transactionMapper.countByUserId(userId);
            records = transactionMapper.findByUserIdPage(userId, offset, safeSize);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("records", records);
        return result;
    }
    
    public List<Map<String, Object>> getMyMedals(Long userId) {
        List<UserMedal> userMedals = userMedalMapper.findByUserId(userId);
        Map<Long, Medal> medalMap = medalMapper.findActiveMedals().stream()
                .collect(Collectors.toMap(Medal::getId, m -> m));
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserMedal userMedal : userMedals) {
            Medal medal = medalMap.get(userMedal.getMedalId());
            if (medal != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", medal.getId());
                item.put("name", medal.getName());
                item.put("icon", medal.getIcon());
                item.put("description", medal.getDescription());
                item.put("obtainTime", userMedal.getObtainTime());
                item.put("status", userMedal.getStatus());
                item.put("rarity", medal.getRarity());
                item.put("progress", userMedal.getProgress());
                item.put("currentValue", userMedal.getCurrentValue());
                item.put("requiredValue", medal.getRequiredValue());
                result.add(item);
            }
        }
        
        result.sort((a, b) -> {
            String statusA = (String) a.get("status");
            String statusB = (String) b.get("status");
            if (MedalStatus.OBTAINED.getCode().equals(statusA) && !MedalStatus.OBTAINED.getCode().equals(statusB)) return -1;
            if (!MedalStatus.OBTAINED.getCode().equals(statusA) && MedalStatus.OBTAINED.getCode().equals(statusB)) return 1;
            return 0;
        });
        
        return result;
    }
    
    public Map<String, Object> getDailyTasks(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        List<DailyTask> tasks = dailyTaskMapper.findActiveTasks();
        List<UserTaskRecord> userRecords = taskRecordMapper.findByUserIdAndDate(userId, today);
        Map<Long, UserTaskRecord> recordMap = userRecords.stream()
                .collect(Collectors.toMap(UserTaskRecord::getTaskId, r -> r));
        
        List<Map<String, Object>> taskList = new ArrayList<>();
        int totalRewardEarned = 0;
        int streakDays = getStreakDays(userId);
        
        for (DailyTask task : tasks) {
            UserTaskRecord record = recordMap.get(task.getId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", task.getId());
            item.put("name", task.getName());
            item.put("description", task.getDescription());
            item.put("reward", task.getReward());
            item.put("target", task.getTarget());
            
            if (record != null) {
                item.put("progress", record.getProgress());
                item.put("status", record.getStatus());
                if (TaskStatus.REWARDED.getCode().equals(record.getStatus())) {
                    totalRewardEarned += task.getReward();
                }
            } else {
                item.put("progress", 0);
                item.put("status", TaskStatus.NOT_STARTED.getCode());
            }
            
            taskList.add(item);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", today);
        result.put("tasks", taskList);
        result.put("totalRewardEarned", totalRewardEarned);
        result.put("streakDays", streakDays);
        
        return result;
    }
    
    private int getStreakDays(Long userId) {
        int streak = 0;
        LocalDate date = LocalDate.now();
        
        for (int i = 0; i < 365; i++) {
            String dateStr = date.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            int completed = taskRecordMapper.countCompletedByUserIdAndDate(userId, dateStr);
            if (completed > 0) {
                streak++;
            } else if (i > 0) {
                break;
            }
        }
        
        return streak;
    }
    
    @Transactional
    public Map<String, Object> checkin(Long userId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        UserTaskRecord record = taskRecordMapper.findByUserIdAndTaskIdAndDate(userId, 1L, today);
        
        Map<String, Object> result = new HashMap<>();
        int streakDays = getStreakDays(userId);
        
        if (record != null && TaskStatus.REWARDED.getCode().equals(record.getStatus())) {
            result.put("success", false);
            result.put("message", "今日已签到");
            result.put("streakDays", streakDays);
            return result;
        }

        int reward = 10;
        if (streakDays >= 7) {
            reward += 5;
        }

        addCredit(userId, reward, "DAILY_TASK", 1L, "每日签到奖励");

        if (record == null) {
            record = new UserTaskRecord();
            record.setUserId(userId);
            record.setTaskId(1L);
            record.setProgress(1);
            record.setTarget(1);
            record.setStatus(TaskStatus.REWARDED.getCode());
            record.setDate(today);
            record.setCompletedTime(LocalDateTime.now());
            record.setRewardTime(LocalDateTime.now());
            taskRecordMapper.insert(record);
        } else {
            record.setProgress(1);
            record.setStatus(TaskStatus.REWARDED.getCode());
            record.setRewardTime(LocalDateTime.now());
            taskRecordMapper.updateById(record);
        }
        
        streakDays = getStreakDays(userId);
        String message = "签到成功，获得" + reward + "积分";
        if (streakDays >= 7) {
            message += "，连续签到" + streakDays + "天，额外奖励5积分！";
        }
        
        result.put("success", true);
        result.put("reward", reward);
        result.put("streakDays", streakDays);
        result.put("message", message);
        
        return result;
    }
    
    @Transactional
    public Map<String, Object> updateTaskProgress(Long userId, Long taskId, Integer increment) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        DailyTask task = dailyTaskMapper.selectById(taskId);
        
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        
        UserTaskRecord record = taskRecordMapper.findByUserIdAndTaskIdAndDate(userId, taskId, today);
        if (record == null) {
            record = new UserTaskRecord();
            record.setUserId(userId);
            record.setTaskId(taskId);
            record.setProgress(0);
            record.setTarget(task.getTarget());
            record.setStatus(TaskStatus.NOT_STARTED.getCode());
            record.setDate(today);
            taskRecordMapper.insert(record);
        }

        if (TaskStatus.REWARDED.getCode().equals(record.getStatus())) {
            return Map.of("success", false, "message", "任务已完成并领奖");
        }

        int newProgress = record.getProgress() + increment;
        record.setProgress(Math.min(newProgress, record.getTarget()));

        if (record.getProgress() >= record.getTarget()) {
            record.setStatus(TaskStatus.COMPLETED.getCode());
            record.setCompletedTime(LocalDateTime.now());
        } else {
            record.setStatus(TaskStatus.IN_PROGRESS.getCode());
        }

        taskRecordMapper.updateById(record);
        
        return Map.of("success", true, "progress", record.getProgress(), 
                      "target", record.getTarget(), "status", record.getStatus());
    }

    @Transactional
    public Map<String, Object> updateTaskProgressByType(Long userId, String type, Integer increment) {
        List<DailyTask> tasks = dailyTaskMapper.findByType(type);
        DailyTask task = tasks.stream()
                .filter(t -> t.getStatus() == null || t.getStatus() == 1)
                .findFirst()
                .orElse(null);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        return updateTaskProgress(userId, task.getId(), increment);
    }
    
    @Transactional
    public Map<String, Object> claimTaskReward(Long userId, Long taskId) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        UserTaskRecord record = taskRecordMapper.findByUserIdAndTaskIdAndDate(userId, taskId, today);
        
        if (record == null) {
            return Map.of("success", false, "message", "任务记录不存在");
        }

        if (!TaskStatus.COMPLETED.getCode().equals(record.getStatus())) {
            return Map.of("success", false, "message", "任务未完成");
        }
        
        DailyTask task = dailyTaskMapper.selectById(taskId);
        if (task == null) {
            return Map.of("success", false, "message", "任务不存在");
        }
        
        addCredit(userId, task.getReward(), "DAILY_TASK", taskId, "完成任务奖励：" + task.getName());

        record.setStatus(TaskStatus.REWARDED.getCode());
        record.setRewardTime(LocalDateTime.now());
        taskRecordMapper.updateById(record);
        
        return Map.of("success", true, "reward", task.getReward());
    }
    
    public List<Map<String, Object>> getProducts(String category, Integer minCredit, Integer maxCredit, String keyword) {
        List<CreditProduct> products;
        
        if (keyword != null && !keyword.isEmpty()) {
            products = productMapper.searchByKeyword(keyword);
        } else if (category != null && !category.isEmpty()) {
            products = productMapper.findByCategory(category);
        } else if (minCredit != null || maxCredit != null) {
            products = productMapper.findByPriceRange(minCredit != null ? minCredit : 0, 
                                                     maxCredit != null ? maxCredit : Integer.MAX_VALUE);
        } else {
            products = productMapper.findActiveProducts();
        }
        
        return products.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", p.getId());
            item.put("name", p.getName());
            item.put("category", p.getCategory());
            item.put("creditCost", p.getCreditCost());
            item.put("stock", p.getStock());
            item.put("imageUrl", p.getImageUrl());
            item.put("isLimited", p.getIsLimited() == 1);
            item.put("limitPerUser", p.getLimitPerUser());
            item.put("exchangeCount", p.getExchangeCount());
            item.put("description", p.getDescription());
            return item;
        }).collect(Collectors.toList());
    }
    
    @Transactional
    public Map<String, Object> exchangeProduct(Long userId, Long productId, Integer quantity, String shippingAddress) {
        if (quantity == null || quantity <= 0) {
            return Map.of("success", false, "message", "兑换数量必须大于0");
        }

        CreditProduct product = productMapper.selectById(productId);
        if (product == null || product.getStatus() != 1) {
            return Map.of("success", false, "message", "商品不存在或已下架");
        }
        
        if (product.getStock() < quantity) {
            return Map.of("success", false, "message", "库存不足");
        }
        
        if (product.getLimitPerUser() > 0) {
            int existingCount = orderMapper.sumQuantityByUserIdAndProductId(userId, productId);
            if (existingCount + quantity > product.getLimitPerUser()) {
                return Map.of("success", false, "message", "已达到限购数量");
            }
        }
        
        int totalCost = product.getCreditCost() * quantity;
        UserCredit credit = getOrCreateUserCredit(userId);
        if (credit.getAvailableCredit() < totalCost) {
            return Map.of("success", false, "message", "积分不足");
        }

        int stockUpdated = productMapper.deductStockWithLock(productId, quantity);
        if (stockUpdated == 0) {
            return Map.of("success", false, "message", "库存不足");
        }

        if (!consumeCredit(userId, totalCost, "SHOP", productId, "兑换商品：" + product.getName())) {
            throw new BusinessException("积分不足");
        }
        
        CreditOrder order = new CreditOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(product.getName());
        order.setQuantity(quantity);
        order.setCreditCost(totalCost);
        order.setShippingAddress(shippingAddress);
        order.setStatus(OrderStatus.PENDING.getCode());
        orderMapper.insert(order);
        
        return Map.of("success", true, "orderId", order.getId(), 
                      "creditSpent", totalCost, "orderNo", order.getOrderNo());
    }
    
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
    
    public List<Map<String, Object>> getOrders(Long userId, String status) {
        List<CreditOrder> orders = status != null ? 
                orderMapper.findByUserIdAndStatus(userId, status) : orderMapper.findByUserId(userId);
        
        return orders.stream().map(o -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", o.getId());
            item.put("orderNo", o.getOrderNo());
            item.put("productName", o.getProductName());
            item.put("quantity", o.getQuantity());
            item.put("creditCost", o.getCreditCost());
            item.put("status", o.getStatus());
            item.put("createTime", o.getCreateTime());
            return item;
        }).collect(Collectors.toList());
    }
}
