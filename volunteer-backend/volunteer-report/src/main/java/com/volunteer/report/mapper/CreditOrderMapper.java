package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CreditOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CreditOrderMapper extends BaseMapper<CreditOrder> {

    @Select("SELECT * FROM credit_order WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<CreditOrder> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM credit_order " +
            "WHERE user_id = #{userId} AND status = #{status} " +
            "ORDER BY create_time DESC")
    List<CreditOrder> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM credit_order WHERE order_no = #{orderNo} LIMIT 1")
    CreditOrder findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT COUNT(*) FROM credit_order " +
            "WHERE user_id = #{userId} AND product_id = #{productId} " +
            "  AND status NOT IN ('CANCELLED')")
    int countByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM credit_order " +
            "WHERE user_id = #{userId} AND product_id = #{productId} " +
            "  AND status NOT IN ('CANCELLED')")
    int sumQuantityByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
