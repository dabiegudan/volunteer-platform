package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.TimeBankAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TimeBankAccountMapper extends BaseMapper<TimeBankAccount> {
    
    @Update("UPDATE time_bank_account " +
            "SET balance = balance + #{amount}, " +
            "    total_earned = total_earned + #{amount}, " +
            "    update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int deposit(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);
    
    @Update("UPDATE time_bank_account " +
            "SET balance = balance - #{amount}, " +
            "    total_spent = total_spent + #{amount}, " +
            "    update_time = NOW() " +
            "WHERE user_id = #{userId} " +
            "AND balance >= #{amount}")
    int withdraw(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);
    
    @Update("UPDATE time_bank_account " +
            "SET balance = balance + #{amount}, " +
            "    total_earned = total_earned + #{amount}, " +
            "    update_time = NOW() " +
            "WHERE user_id = #{targetUserId}")
    int transferTo(@Param("targetUserId") Long targetUserId, @Param("amount") java.math.BigDecimal amount);
}