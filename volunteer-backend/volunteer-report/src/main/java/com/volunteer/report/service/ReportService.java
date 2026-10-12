package com.volunteer.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.volunteer.attendance.entity.Attendance;
import com.volunteer.attendance.mapper.AttendanceMapper;
import com.volunteer.report.entity.UserDuration;
import com.volunteer.report.mapper.UserDurationMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {
    
    private final AttendanceMapper attendanceMapper;
    private final UserDurationMapper durationMapper;
    
    public Map<String, Object> getUserDurationReport(Long userId) {
        Map<String, Object> report = new HashMap<>();
        
        List<Attendance> attendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getUserId, userId)
        );
        
        BigDecimal totalDuration = BigDecimal.ZERO;
        int activityCount = 0;
        
        for (Attendance attendance : attendances) {
            if (attendance.getDurationHours() != null) {
                totalDuration = totalDuration.add(attendance.getDurationHours());
                activityCount++;
            }
        }
        
        report.put("userId", userId);
        report.put("totalDuration", totalDuration);
        report.put("activityCount", activityCount);
        report.put("reportTime", LocalDateTime.now());
        
        return report;
    }
    
    public byte[] exportAttendanceExcel(Long userId) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("考勤记录");
        
        Row headerRow = sheet.createRow(0);
        String[] headers = {"活动ID", "签到时间", "签退时间", "时长(小时)", "状态"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
        
        List<Attendance> attendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getUserId, userId)
                        .orderByDesc(Attendance::getCreateTime)
        );
        int rowNum = 1;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (Attendance attendance : attendances) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(attendance.getActivityId());
            row.createCell(1).setCellValue(attendance.getCheckInTime() != null ? attendance.getCheckInTime().format(formatter) : "");
            row.createCell(2).setCellValue(attendance.getCheckOutTime() != null ? attendance.getCheckOutTime().format(formatter) : "");
            row.createCell(3).setCellValue(attendance.getDurationHours() != null ? attendance.getDurationHours().doubleValue() : 0);
            row.createCell(4).setCellValue(attendance.getStatus());
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }
    
    public byte[] exportDurationExcel(Long userId) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("时长明细");
        
        Row headerRow = sheet.createRow(0);
        String[] headers = {"活动ID", "原始时长", "调整后时长", "状态", "审核时间"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
        
        List<UserDuration> durations = getUserDurationDetails(userId, null);
        int rowNum = 1;
        
        for (UserDuration duration : durations) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(duration.getActivityId());
            row.createCell(1).setCellValue(duration.getOriginalDuration() != null ? duration.getOriginalDuration().doubleValue() : 0);
            row.createCell(2).setCellValue(duration.getAdjustedDuration() != null ? duration.getAdjustedDuration().doubleValue() : 0);
            row.createCell(3).setCellValue(duration.getStatus());
            row.createCell(4).setCellValue(duration.getApprovalTime() != null ? duration.getApprovalTime().toString() : "");
        }
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }

    public List<UserDuration> getUserDurationDetails(Long userId, String status) {
        LambdaQueryWrapper<UserDuration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDuration::getUserId, userId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(UserDuration::getStatus, status);
        }
        wrapper.orderByDesc(UserDuration::getCreateTime);
        return durationMapper.selectList(wrapper);
    }
    
    public byte[] generateDurationCertificate(Long userId) throws Exception {
        Map<String, Object> report = getUserDurationReport(userId);
        BigDecimal totalDuration = (BigDecimal) report.get("totalDuration");
        int activityCount = (Integer) report.get("activityCount");

        String qrCodeContent = generateQRCodeContent(userId, totalDuration);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, outputStream);
        document.open();
        
        BaseFont bfChinese = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        com.itextpdf.text.Font titleFont = new com.itextpdf.text.Font(bfChinese, 24, com.itextpdf.text.Font.BOLD);
        com.itextpdf.text.Font normalFont = new com.itextpdf.text.Font(bfChinese, 12, com.itextpdf.text.Font.NORMAL);
        com.itextpdf.text.Font boldFont = new com.itextpdf.text.Font(bfChinese, 12, com.itextpdf.text.Font.BOLD);
        
        Paragraph title = new Paragraph("志愿者服务时长证明", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(30);
        document.add(title);
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(80);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);
        
        addTableCell(table, "志愿者ID", userId.toString(), boldFont, normalFont);
        addTableCell(table, "服务总时长", totalDuration + " 小时", boldFont, normalFont);
        addTableCell(table, "参与活动数", activityCount + " 次", boldFont, normalFont);
        addTableCell(table, "生成时间", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), boldFont, normalFont);
        
        document.add(table);
        
        Paragraph certContent = new Paragraph("\n特此证明", normalFont);
        certContent.setAlignment(Element.ALIGN_CENTER);
        certContent.setSpacingBefore(30);
        document.add(certContent);
        
        byte[] qrCodeBytes = generateQRCode(qrCodeContent, 150, 150);
        Image qrCodeImage = Image.getInstance(qrCodeBytes);
        qrCodeImage.setAlignment(Element.ALIGN_CENTER);
        qrCodeImage.setSpacingBefore(30);
        document.add(qrCodeImage);
        
        Paragraph qrCodeHint = new Paragraph("扫码验证证书真伪", normalFont);
        qrCodeHint.setAlignment(Element.ALIGN_CENTER);
        document.add(qrCodeHint);
        
        document.close();
        
        return outputStream.toByteArray();
    }
    
    private void addTableCell(PdfPTable table, String label, String value, com.itextpdf.text.Font labelFont, com.itextpdf.text.Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(10);
        
        PdfPCell valueCell = new PdfPCell(new Paragraph(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(10);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
    
    private String generateCertificateData(Long userId, BigDecimal duration) {
        String normalizedDuration = duration == null ? BigDecimal.ZERO.toPlainString() : duration.stripTrailingZeros().toPlainString();
        String rawData = userId + ":" + normalizedDuration;
        return hashString(rawData);
    }
    
    private String generateQRCodeContent(Long userId, BigDecimal duration) {
        String certificateHash = generateCertificateData(userId, duration);
        return "volunteer://certificate?userId=" + userId + "&hash=" + certificateHash;
    }
    
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }
    
    private byte[] generateQRCode(String content, int width, int height) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return outputStream.toByteArray();
    }
    
    public boolean verifyCertificate(Long userId, String hash) {
        Map<String, Object> report = getUserDurationReport(userId);
        BigDecimal duration = (BigDecimal) report.get("totalDuration");
        String expectedHash = generateCertificateData(userId, duration);
        return expectedHash.equals(hash);
    }
}
