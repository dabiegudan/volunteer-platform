package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CreditTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CreditTransactionMapper extends BaseMapper<CreditTransaction> {

    @Select("SELECT * FROM credit_transaction WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<CreditTransaction> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM credit_transaction " +
            "WHERE user_id = #{userId} AND type = #{type} " +
            "ORDER BY create_time DESC")
    List<CreditTransaction> findByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT * FROM credit_transaction " +
            "WHERE user_id = #{userId} " +
            "  AND create_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY create_time DESC")
    List<CreditTransaction> findByUserIdAndTimeRange(@Param("userId") Long userId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM credit_transaction " +
            "WHERE user_id = #{userId} AND type = #{type}")
    int sumByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT COALESCE(SUM(amount), 0) FROM credit_transaction " +
            "WHERE user_id = #{userId} " +
            "  AND create_time BETWEEN #{startTime} AND #{endTime}")
    int sumByUserIdAndTimeRange(@Param("userId") Long userId,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime);

    // ===== 分页查询 (R-08 实施):避免长期用户流水过大时一次拉全表导致 OOM / 慢响应 =====

    @Select("SELECT * FROM credit_transaction " +
            "WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<CreditTransaction> findByUserIdPage(@Param("userId") Long userId,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    @Select("SELECT * FROM credit_transaction " +
            "WHERE user_id = #{userId} AND type = #{type} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<CreditTransaction> findByUserIdAndTypePage(@Param("userId") Long userId,
                                                    @Param("type") String type,
                                                    @Param("offset") int offset,
                                                    @Param("size") int size);

    @Select("SELECT * FROM credit_transaction " +
            "WHERE user_id = #{userId} " +
            "  AND create_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<CreditTransaction> findByUserIdAndTimeRangePage(@Param("userId") Long userId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime,
                                                         @Param("offset") int offset,
                                                         @Param("size") int size);

    @Select("SELECT COUNT(*) FROM credit_transaction WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM credit_transaction " +
            "WHERE user_id = #{userId} AND type = #{type}")
    long countByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM credit_transaction " +
            "WHERE user_id = #{userId} " +
            "  AND create_time BETWEEN #{startTime} AND #{endTime}")
    long countByUserIdAndTimeRange(@Param("userId") Long userId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
}
