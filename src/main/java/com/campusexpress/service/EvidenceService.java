package com.campusexpress.service;

import com.campusexpress.entity.Order;
import com.campusexpress.mapper.OrderMapper;
import com.campusexpress.util.WatermarkUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EvidenceService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;
    private final EvidenceStorageService evidenceStorageService;

    public EvidenceService(OrderMapper orderMapper, EvidenceStorageService evidenceStorageService) {
        this.orderMapper = orderMapper;
        this.evidenceStorageService = evidenceStorageService;
    }

    public UploadResult uploadEvidence(Long orderId, Long operatorId, MultipartFile file) {
        Order order = getOrder(orderId);
        validateParticipant(order, operatorId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("图片文件不能为空");
        }

        String formatName = resolveFormatName(file.getOriginalFilename(), file.getContentType());
        String contentType = "png".equals(formatName) ? "image/png" : "image/jpeg";
        LocalDateTime now = LocalDateTime.now();

        try {
            byte[] watermarked = WatermarkUtil.addWatermark(file.getBytes(), formatName, orderId, now);
            String objectKey = evidenceStorageService.buildObjectKey(buildFileName(orderId, now, formatName));

            evidenceStorageService.deleteQuietly(order.getPhotoUrl());
            String storedPath = evidenceStorageService.upload(objectKey, watermarked, contentType);

            order.setPhotoUrl(storedPath);
            orderMapper.updateById(order);

            return new UploadResult(orderId, storedPath, "/api/evidence/view/" + orderId);
        } catch (IOException ex) {
            throw new IllegalStateException("处理存证图片失败", ex);
        }
    }

    public EvidenceFile viewEvidence(Long orderId, Long operatorId) {
        Order order = getOrder(orderId);
        validateParticipant(order, operatorId);
        if (!StringUtils.hasText(order.getPhotoUrl())) {
            throw new IllegalArgumentException("该订单暂未上传存证照片");
        }

        EvidenceStorageService.StoredFile storedFile = evidenceStorageService.download(order.getPhotoUrl());
        String contentType = StringUtils.hasText(storedFile.getContentType())
                ? storedFile.getContentType()
                : guessContentType(order.getPhotoUrl());
        return new EvidenceFile(storedFile.getContent(), contentType);
    }

    private Order getOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private void validateParticipant(Order order, Long operatorId) {
        boolean isRequester = order.getRequesterId() != null && order.getRequesterId().equals(operatorId);
        boolean isReceiver = order.getReceiverId() != null && order.getReceiverId().equals(operatorId);
        if (!isRequester && !isReceiver) {
            throw new IllegalArgumentException("无权访问该订单存证");
        }
    }

    private String resolveFormatName(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1);
            return WatermarkUtil.normalizeFormat(extension);
        }
        if (StringUtils.hasText(contentType)) {
            if (contentType.toLowerCase(Locale.ROOT).contains("png")) {
                return "png";
            }
            if (contentType.toLowerCase(Locale.ROOT).contains("jpeg")
                    || contentType.toLowerCase(Locale.ROOT).contains("jpg")) {
                return "jpg";
            }
        }
        return "jpg";
    }

    private String buildFileName(Long orderId, LocalDateTime timestamp, String formatName) {
        return DATE_PATH.format(timestamp)
                + "/order-" + orderId
                + "-" + FILE_TIME.format(timestamp)
                + "." + formatName;
    }

    private String guessContentType(String objectKey) {
        String lowerCase = objectKey.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".png")) {
            return "image/png";
        }
        return "image/jpeg";
    }

    public static class UploadResult {
        private final Long orderId;
        private final String photoUrl;
        private final String viewUrl;

        public UploadResult(Long orderId, String photoUrl, String viewUrl) {
            this.orderId = orderId;
            this.photoUrl = photoUrl;
            this.viewUrl = viewUrl;
        }

        public Long getOrderId() {
            return orderId;
        }

        public String getPhotoUrl() {
            return photoUrl;
        }

        public String getViewUrl() {
            return viewUrl;
        }
    }

    public static class EvidenceFile {
        private final byte[] content;
        private final String contentType;

        public EvidenceFile(byte[] content, String contentType) {
            this.content = content;
            this.contentType = contentType;
        }

        public byte[] getContent() {
            return content;
        }

        public String getContentType() {
            return contentType;
        }
    }
}
