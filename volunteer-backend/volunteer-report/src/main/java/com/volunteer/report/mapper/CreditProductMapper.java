package com.volunteer.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.volunteer.report.entity.CreditProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CreditProductMapper extends BaseMapper<CreditProduct> {

    @Select("SELECT * FROM credit_product WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<CreditProduct> findActiveProducts();

    @Select("SELECT * FROM credit_product WHERE category = #{category} AND status = 1 ORDER BY sort_order ASC, id ASC")
    List<CreditProduct> findByCategory(@Param("category") String category);

    @Select("SELECT * FROM credit_product " +
            "WHERE status = 1 AND credit_cost BETWEEN #{minPrice} AND #{maxPrice} " +
            "ORDER BY credit_cost ASC, id ASC")
    List<CreditProduct> findByPriceRange(@Param("minPrice") Integer minPrice, @Param("maxPrice") Integer maxPrice);

    @Select("SELECT * FROM credit_product " +
            "WHERE status = 1 AND (name LIKE CONCAT('%', #{keyword}, '%') " +
            "                     OR description LIKE CONCAT('%', #{keyword}, '%')) " +
            "ORDER BY sort_order ASC, id ASC")
    List<CreditProduct> searchByKeyword(@Param("keyword") String keyword);

    @Update("UPDATE credit_product " +
            "SET stock = stock - #{quantity}, " +
            "    exchange_count = exchange_count + #{quantity}, " +
            "    update_time = NOW() " +
            "WHERE id = #{id} AND status = 1 AND stock >= #{quantity}")
    int deductStockWithLock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
