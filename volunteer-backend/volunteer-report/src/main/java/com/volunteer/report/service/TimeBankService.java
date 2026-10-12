package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.entity.TimeBankAccount;
import com.volunteer.report.entity.TimeBankServiceEntity;
import com.volunteer.report.entity.TimeBankTransaction;
import com.volunteer.report.mapper.TimeBankAccountMapper;
import com.volunteer.report.mapper.TimeBankServiceMapper;
import com.volunteer.report.mapper.TimeBankTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 时间银行服务
 * 实现TBC积分的双向循环机制
 */
@Service
@RequiredArgsConstructor
public class TimeBankService {

    private static final Logger log = LoggerFactory.getLogger(TimeBankService.class);

    private final TimeBankAccountMapper accountMapper;
    private final TimeBankTransactionMapper transactionMapper;
    private final TimeBankServiceMapper serviceMapper;

    /**
     * 获取用户时间银行账户
     */
    public TimeBankAccount getAccount(Long userId) {
        TimeBankAccount account = accountMapper.selectOne(
            new LambdaQueryWrapper<TimeBankAccount>().eq(TimeBankAccount::getUserId, userId)
        );
        if (account == null) {
            // 创建默认账户
            account = createAccount(userId);
        }
        return account;
    }

    /**
     * 创建时间银行账户
     */
    @Transactional
    public TimeBankAccount createAccount(Long userId) {
        log.info("创建时间银行账户, userId: {}", userId);
        TimeBankAccount account = new TimeBankAccount();
        account.setUserId(userId);
        account.setBalance(BigDecimal.ZERO);
        account.setTotalEarned(BigDecimal.ZERO);
        account.setTotalSpent(BigDecimal.ZERO);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.insert(account);
        return account;
    }

    /**
     * 存入TBC积分（志愿服务时长转换）
     */
    @Transactional
    public void deposit(Long userId, BigDecimal hours, Long sourceId, String sourceType) {
        validatePositiveAmount(hours, "Deposit amount");
        log.info("存入TBC积分, userId: {}, hours: {}", userId, hours);
        
        TimeBankAccount account = getAccount(userId);
        BigDecimal balanceBefore = account.getBalance();
        
        int updated = accountMapper.deposit(userId, hours);
        if (updated == 0) {
            throw new BusinessException("账户更新失败");
        }
        
        // 记录交易流水
        TimeBankTransaction transaction = new TimeBankTransaction();
        transaction.setUserId(userId);
        transaction.setType("DEPOSIT");
        transaction.setAmount(hours);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceBefore.add(hours));
        transaction.setSourceId(sourceId);
        transaction.setSourceType(sourceType);
        transaction.setDescription("志愿服务时长转换TBC");
        transaction.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(transaction);
        
        log.info("TBC积分存入成功, userId: {}, amount: {}", userId, hours);
    }

    /**
     * 支出TBC积分
     */
    @Transactional
    public void withdraw(Long userId, BigDecimal amount, Long sourceId, String sourceType, String description) {
        validatePositiveAmount(amount, "Withdraw amount");
        log.info("支出TBC积分, userId: {}, amount: {}", userId, amount);
        
        TimeBankAccount account = getAccount(userId);
        BigDecimal balanceBefore = account.getBalance();
        
        if (balanceBefore.compareTo(amount) < 0) {
            throw new BusinessException("TBC积分不足");
        }
        
        int updated = accountMapper.withdraw(userId, amount);
        if (updated == 0) {
            throw new BusinessException("账户更新失败");
        }
        
        TimeBankTransaction transaction = new TimeBankTransaction();
        transaction.setUserId(userId);
        transaction.setType("WITHDRAW");
        transaction.setAmount(amount);
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceBefore.subtract(amount));
        transaction.setSourceId(sourceId);
        transaction.setSourceType(sourceType);
        transaction.setDescription(description);
        transaction.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(transaction);
        
        log.info("TBC积分支出成功, userId: {}, amount: {}", userId, amount);
    }

    /**
     * 用户间转账
     */
    @Transactional
    public void transfer(Long fromUserId, Long toUserId, BigDecimal amount) {
        validatePositiveAmount(amount, "Transfer amount");
        if (fromUserId == null || toUserId == null || fromUserId.equals(toUserId)) {
            throw new BusinessException("Invalid transfer target");
        }
        log.info("TBC转账, fromUserId: {}, toUserId: {}, amount: {}", fromUserId, toUserId, amount);
        
        TimeBankAccount fromAccount = getAccount(fromUserId);
        BigDecimal fromBalanceBefore = fromAccount.getBalance();
        
        if (fromBalanceBefore.compareTo(amount) < 0) {
            throw new BusinessException("TBC积分不足");
        }
        
        // 转出
        int updated1 = accountMapper.withdraw(fromUserId, amount);
        if (updated1 == 0) {
            throw new BusinessException("转出失败");
        }
        
        // 转入
        TimeBankAccount toAccount = getAccount(toUserId);
        BigDecimal toBalanceBefore = toAccount.getBalance();
        int updated2 = accountMapper.transferTo(toUserId, amount);
        if (updated2 == 0) {
            throw new BusinessException("转入失败");
        }
        
        // 记录转出流水
        TimeBankTransaction transaction1 = new TimeBankTransaction();
        transaction1.setUserId(fromUserId);
        transaction1.setType("TRANSFER_OUT");
        transaction1.setAmount(amount);
        transaction1.setBalanceBefore(fromBalanceBefore);
        transaction1.setBalanceAfter(fromBalanceBefore.subtract(amount));
        transaction1.setTargetUserId(toUserId);
        transaction1.setDescription("转账给用户" + toUserId);
        transaction1.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(transaction1);
        
        // 记录转入流水
        TimeBankTransaction transaction2 = new TimeBankTransaction();
        transaction2.setUserId(toUserId);
        transaction2.setType("TRANSFER_IN");
        transaction2.setAmount(amount);
        transaction2.setBalanceBefore(toBalanceBefore);
        transaction2.setBalanceAfter(toBalanceBefore.add(amount));
        transaction2.setTargetUserId(fromUserId);
        transaction2.setDescription("收到用户" + fromUserId + "的转账");
        transaction2.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(transaction2);
        
        log.info("TBC转账成功, fromUserId: {}, toUserId: {}, amount: {}", fromUserId, toUserId, amount);
    }

    /**
     * 兑换服务
     */
    @Transactional
    public void redeemService(Long userId, Long serviceId) {
        log.info("兑换服务, userId: {}, serviceId: {}", userId, serviceId);
        
        TimeBankServiceEntity service = serviceMapper.selectById(serviceId);
        if (service == null) {
            throw new BusinessException("服务不存在");
        }
        
        if (!"ACTIVE".equals(service.getStatus())) {
            throw new BusinessException("服务不可用");
        }
        
        if (service.getAvailableCount() == null || service.getAvailableCount() <= 0) {
            throw new BusinessException("服务已被抢光");
        }
        
        validatePositiveAmount(service.getTbcCost(), "Service cost");
        withdraw(userId, service.getTbcCost(), serviceId, "SERVICE_REDEEM", "兑换服务: " + service.getTitle());
        
        int updated = serviceMapper.decreaseAvailableCount(serviceId);
        if (updated == 0) {
            throw new BusinessException("服务库存更新失败");
        }
        
        log.info("服务兑换成功, userId: {}, serviceId: {}", userId, serviceId);
    }

    /**
     * 发布可兑换服务
     */
    @Transactional
    public TimeBankServiceEntity publishService(Long userId, TimeBankServiceEntity service) {
        validatePositiveAmount(service.getTbcCost(), "Service cost");
        if (service.getAvailableCount() == null || service.getAvailableCount() <= 0) {
            throw new BusinessException("Available count must be greater than zero");
        }
        log.info("发布可兑换服务, userId: {}, title: {}", userId, service.getTitle());
        service.setProviderId(userId);
        service.setStatus("ACTIVE");
        service.setCreateTime(LocalDateTime.now());
        service.setUpdateTime(LocalDateTime.now());
        serviceMapper.insert(service);
        return service;
    }

    /**
     * 分页查询可兑换服务
     */
    public Page<TimeBankServiceEntity> getServicePage(Integer pageNum, Integer pageSize, String category) {
        Page<TimeBankServiceEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TimeBankServiceEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimeBankServiceEntity::getStatus, "ACTIVE");
        if (category != null && !category.isEmpty()) {
            wrapper.eq(TimeBankServiceEntity::getCategory, category);
        }
        wrapper.orderByDesc(TimeBankServiceEntity::getCreateTime);
        return serviceMapper.selectPage(page, wrapper);
    }

    /**
     * 获取用户交易记录
     */
    public Page<TimeBankTransaction> getTransactionPage(Long userId, Integer pageNum, Integer pageSize) {
        Page<TimeBankTransaction> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TimeBankTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TimeBankTransaction::getUserId, userId);
        wrapper.orderByDesc(TimeBankTransaction::getCreateTime);
        return transactionMapper.selectPage(page, wrapper);
    }

    private void validatePositiveAmount(BigDecimal amount, String label) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(label + " must be greater than zero");
        }
    }
}
