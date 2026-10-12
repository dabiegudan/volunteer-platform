package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.UserCredit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {

    @Select("SELECT * FROM user_credit WHERE user_id = #{userId} LIMIT 1")
    UserCredit findByUserId(@Param("userId") Long userId);

    /**
     * 增加/扣减用户可用积分 (正数加分, 负数扣分),同时维护 total_credit
     */
    @Update("UPDATE user_credit " +
            "SET available_credit = available_credit + #{amount}, " +
            "    total_credit = total_credit + #{amount}, " +
            "    update_time = NOW() " +
            "WHERE user_id = #{userId} " +
            "  AND available_credit + #{amount} >= 0")
    int updateCredit(@Param("userId") Long userId, @Param("amount") Integer amount);

    /**
     * 增加成长值(用于星级晋升)
     */
    @Update("UPDATE user_credit " +
            "SET growth_value = growth_value + #{amount}, " +
            "    update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int updateGrowthValue(@Param("userId") Long userId, @Param("amount") Integer amount);
}
