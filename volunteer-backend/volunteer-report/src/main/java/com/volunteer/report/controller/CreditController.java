package com.volunteer.report.controller;

import com.volunteer.auth.annotation.RequiresAccess;
import com.volunteer.common.exception.BusinessException;
import com.volunteer.report.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credit")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getCreditOverview(@RequestAttribute("userId") Long userId) {
        Map<String, Object> overview = creditService.getCreditOverview(userId);
        return ResponseEntity.ok(overview);
    }

    /**
     * 分页查询积分流水(R-08 实施):避免长期用户流水过大导致 OOM / 慢响应。
     * <p>返回结构:{ total, page, size, records }。</p>
     */
    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Map<String, Object> result = creditService.getTransactionsPage(userId, type, startDate, endDate, page, size);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/medals")
    public ResponseEntity<List<Map<String, Object>>> getMyMedals(@RequestAttribute("userId") Long userId) {
        List<Map<String, Object>> medals = creditService.getMyMedals(userId);
        return ResponseEntity.ok(medals);
    }
    
    @GetMapping("/tasks")
    public ResponseEntity<Map<String, Object>> getDailyTasks(@RequestAttribute("userId") Long userId) {
        Map<String, Object> tasks = creditService.getDailyTasks(userId);
        return ResponseEntity.ok(tasks);
    }
    
    @PostMapping("/checkin")
    @RequiresAccess(requireStudentId = true)
    public ResponseEntity<Map<String, Object>> checkin(@RequestAttribute("userId") Long userId) {
        Map<String, Object> result = creditService.checkin(userId);
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/task/progress")
    @RequiresAccess(requireStudentId = true)
    public ResponseEntity<Map<String, Object>> updateTaskProgress(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false, defaultValue = "1") Integer increment,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = creditService.updateTaskProgress(
                userId,
                requiredLong(body, "taskId", taskId),
                optionalInteger(body, "increment", increment));
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @PostMapping("/task/claim")
    @RequiresAccess(requireStudentId = true)
    public ResponseEntity<Map<String, Object>> claimTaskReward(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long taskId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = creditService.claimTaskReward(userId, requiredLong(body, "taskId", taskId));
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @GetMapping("/products")
    public ResponseEntity<List<Map<String, Object>>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minCredit,
            @RequestParam(required = false) Integer maxCredit,
            @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> products = creditService.getProducts(category, minCredit, maxCredit, keyword);
        return ResponseEntity.ok(products);
    }
    
    @PostMapping("/exchange")
    @RequiresAccess(requireStudentId = true)
    public ResponseEntity<Map<String, Object>> exchangeProduct(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false, defaultValue = "1") Integer quantity,
            @RequestParam(required = false) String shippingAddress,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = creditService.exchangeProduct(
                userId,
                requiredLong(body, "productId", productId),
                optionalInteger(body, "quantity", quantity),
                optionalString(body, "shippingAddress", shippingAddress));
        if ((Boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }
    
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, Object>>> getOrders(
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) String status) {
        List<Map<String, Object>> orders = creditService.getOrders(userId, status);
        return ResponseEntity.ok(orders);
    }
    
    @PostMapping("/add")
    @RequiresAccess("admin")
    public ResponseEntity<Map<String, Object>> addCredit(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer amount,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String description,
            @RequestBody(required = false) Map<String, Object> body) {
        creditService.addCredit(
                requiredLong(body, "userId", userId),
                requiredInteger(body, "amount", amount),
                requiredString(body, "source", source),
                optionalLong(body, "sourceId", sourceId),
                optionalString(body, "description", description));
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "积分添加成功");
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/consume")
    @RequiresAccess("admin")
    public ResponseEntity<Map<String, Object>> consumeCredit(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer amount,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) String description,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean success = creditService.consumeCredit(
                requiredLong(body, "userId", userId),
                requiredInteger(body, "amount", amount),
                requiredString(body, "source", source),
                optionalLong(body, "sourceId", sourceId),
                optionalString(body, "description", description));
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("success", true);
            result.put("message", "积分消费成功");
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "积分不足");
            return ResponseEntity.badRequest().body(result);
        }
    }

    private Object bodyValue(Map<String, Object> body, String key) {
        return body == null ? null : body.get(key);
    }

    private Long requiredLong(Map<String, Object> body, String key, Long fallback) {
        Long value = optionalLong(body, key, fallback);
        if (value == null) {
            throw new BusinessException(key + "不能为空");
        }
        return value;
    }

    private Integer requiredInteger(Map<String, Object> body, String key, Integer fallback) {
        Integer value = optionalInteger(body, key, fallback);
        if (value == null) {
            throw new BusinessException(key + "不能为空");
        }
        return value;
    }

    private String requiredString(Map<String, Object> body, String key, String fallback) {
        String value = optionalString(body, key, fallback);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + "不能为空");
        }
        return value;
    }

    private Long optionalLong(Map<String, Object> body, String key, Long fallback) {
        Object value = bodyValue(body, key);
        return value == null ? fallback : Long.parseLong(value.toString());
    }

    private Integer optionalInteger(Map<String, Object> body, String key, Integer fallback) {
        Object value = bodyValue(body, key);
        return value == null ? fallback : Integer.parseInt(value.toString());
    }

    private String optionalString(Map<String, Object> body, String key, String fallback) {
        Object value = bodyValue(body, key);
        return value == null ? fallback : value.toString();
    }
}
