package com.volunteer.auth.service;

import com.volunteer.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final StringRedisTemplate redisTemplate;

    private static final int WIDTH = 300;
    private static final int HEIGHT = 150;
    private static final int BLOCK_SIZE = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    public Map<String, Object> generateCaptcha() {
        String captchaId = UUID.randomUUID().toString();

        int blockX = RANDOM.nextInt(WIDTH - BLOCK_SIZE * 2) + BLOCK_SIZE;
        int blockY = RANDOM.nextInt(HEIGHT - BLOCK_SIZE) + 10;

        BufferedImage background = createBackground();
        drawBlock(background, blockX, blockY, true);

        BufferedImage slider = createSlider(blockX, blockY);

        String backgroundBase64 = imageToBase64(background);
        String sliderBase64 = imageToBase64(slider);

        String key = "captcha:" + captchaId;
        String finalKey = key;
        redisTemplate.opsForValue().set(finalKey, String.valueOf(blockX), 5, TimeUnit.MINUTES);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("background", backgroundBase64);
        result.put("slider", sliderBase64);
        result.put("sliderWidth", BLOCK_SIZE);
        result.put("sliderHeight", BLOCK_SIZE);
        
        return result;
    }
    
    public boolean verifyCaptcha(String captchaId, int sliderX) {
        String key = "captcha:" + captchaId;
        String storedX = redisTemplate.opsForValue().get(key);
        
        if (storedX == null) {
            throw new BusinessException("验证码已过期");
        }
        
        redisTemplate.delete(key);
        
        int targetX = Integer.parseInt(storedX);
        int tolerance = 10;
        
        return Math.abs(sliderX - targetX) <= tolerance;
    }
    
    private BufferedImage createBackground() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        
        for (int i = 0; i < 50; i++) {
            int x = RANDOM.nextInt(WIDTH);
            int y = RANDOM.nextInt(HEIGHT);
            g.setColor(new Color(RANDOM.nextInt(256), RANDOM.nextInt(256), RANDOM.nextInt(256)));
            g.drawOval(x, y, 2, 2);
        }
        
        for (int i = 0; i < 10; i++) {
            int x1 = RANDOM.nextInt(WIDTH);
            int y1 = RANDOM.nextInt(HEIGHT);
            int x2 = RANDOM.nextInt(WIDTH);
            int y2 = RANDOM.nextInt(HEIGHT);
            g.setColor(new Color(RANDOM.nextInt(200) + 55, RANDOM.nextInt(200) + 55, RANDOM.nextInt(200) + 55));
            g.drawLine(x1, y1, x2, y2);
        }
        
        g.dispose();
        return image;
    }
    
    private void drawBlock(BufferedImage image, int x, int y, boolean isBackground) {
        Graphics2D g = image.createGraphics();
        
        Color blockColor = new Color(255, 100, 100);
        g.setColor(blockColor);
        
        int radius = 8;
        g.fillRoundRect(x, y, BLOCK_SIZE, BLOCK_SIZE, radius, radius);
        
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(x, y, BLOCK_SIZE, BLOCK_SIZE, radius, radius);
        
        g.dispose();
    }
    
    private BufferedImage createSlider(int targetX, int targetY) {
        BufferedImage slider = new BufferedImage(BLOCK_SIZE, BLOCK_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = slider.createGraphics();
        
        g.setColor(new Color(255, 100, 100));
        
        int radius = 8;
        g.fillRoundRect(0, 0, BLOCK_SIZE, BLOCK_SIZE, radius, radius);
        
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(0, 0, BLOCK_SIZE, BLOCK_SIZE, radius, radius);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("→", 15, 30);
        
        g.dispose();
        return slider;
    }
    
    private String imageToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] bytes = baos.toByteArray();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.error("Failed to convert image to base64", e);
            throw new BusinessException("生成验证码失败");
        }
    }
}
