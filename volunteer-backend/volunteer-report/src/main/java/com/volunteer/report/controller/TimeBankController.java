package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.result.PageResult;
import com.volunteer.common.result.Result;
import com.volunteer.report.entity.TimeBankAccount;
import com.volunteer.report.entity.TimeBankServiceEntity;
import com.volunteer.report.entity.TimeBankTransaction;
import com.volunteer.report.service.TimeBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "时间银行")
@RestController
@RequestMapping("/api/time-bank")
@RequiredArgsConstructor
public class TimeBankController {

    private final TimeBankService timeBankService;

    @Operation(summary = "获取我的时间银行账户")
    @GetMapping("/account")
    public Result<TimeBankAccount> getAccount(@RequestAttribute("userId") Long userId) {
        return Result.success(timeBankService.getAccount(userId));
    }

    @Operation(summary = "TBC 转账")
    @PostMapping("/transfer")
    @RequiresAccess(requireStudentId = true)
    public Result<Void> transfer(
            @RequestBody Map<String, Object> body,
            @RequestAttribute("userId") Long userId) {
        timeBankService.transfer(userId, longValue(body.get("toUserId")), decimalValue(body.get("amount")));
        return Result.success();
    }

    @Operation(summary = "分页查询可兑换服务")
    @GetMapping("/services")
    public Result<PageResult<TimeBankServiceEntity>> getServices(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String category) {
        return Result.success(PageResult.of(timeBankService.getServicePage(pageNum, pageSize, category)));
    }

    @Operation(summary = "发布可兑换服务")
    @PostMapping("/services")
    @RequiresAccess(requireStudentId = true)
    public Result<TimeBankServiceEntity> publishService(
            @RequestBody TimeBankServiceEntity service,
            @RequestAttribute("userId") Long userId) {
        return Result.success(timeBankService.publishService(userId, service));
    }

    @Operation(summary = "兑换服务")
    @PostMapping("/services/{serviceId}/redeem")
    @RequiresAccess(requireStudentId = true)
    public Result<Void> redeemService(
            @PathVariable Long serviceId,
            @RequestAttribute("userId") Long userId) {
        timeBankService.redeemService(userId, serviceId);
        return Result.success();
    }

    @Operation(summary = "获取我的交易流水")
    @GetMapping("/transactions")
    public Result<PageResult<TimeBankTransaction>> getTransactions(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestAttribute("userId") Long userId) {
        return Result.success(PageResult.of(timeBankService.getTransactionPage(userId, pageNum, pageSize)));
    }

    @Operation(summary = "管理员存入 TBC")
    @PostMapping("/deposit")
    @RequiresAccess("admin")
    public Result<Void> deposit(@RequestBody Map<String, Object> body) {
        timeBankService.deposit(
                longValue(body.get("userId")),
                decimalValue(firstNonNull(body.get("hours"), body.get("amount"))),
                nullableLong(body.get("sourceId")),
                stringValue(body.get("sourceType")));
        return Result.success();
    }

    @Operation(summary = "管理员扣减 TBC")
    @PostMapping("/withdraw")
    @RequiresAccess("admin")
    public Result<Void> withdraw(@RequestBody Map<String, Object> body) {
        timeBankService.withdraw(
                longValue(body.get("userId")),
                decimalValue(body.get("amount")),
                nullableLong(body.get("sourceId")),
                stringValue(body.get("sourceType")),
                stringValue(body.get("description")));
        return Result.success();
    }

    private Object firstNonNull(Object left, Object right) {
        return left != null ? left : right;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long nullableLong(Object value) {
        return value == null ? null : Long.parseLong(value.toString());
    }

    private Long longValue(Object value) {
        return Long.parseLong(value.toString());
    }

    private BigDecimal decimalValue(Object value) {
        return new BigDecimal(value.toString());
    }
}
