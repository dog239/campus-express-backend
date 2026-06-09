package com.campusexpress.util;

import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class WatermarkUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WatermarkUtil() {
    }

    public static byte[] addWatermark(byte[] sourceBytes, String formatName, Long orderId, LocalDateTime timestamp)
            throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(sourceBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage source = ImageIO.read(inputStream);
            if (source == null) {
                throw new IllegalArgumentException("仅支持 PNG/JPG/JPEG 图片");
            }

            String outputFormat = normalizeFormat(formatName);
            int imageType = "png".equals(outputFormat) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
            BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), imageType);

            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, null);

            int fontSize = Math.max(18, Math.min(source.getWidth(), source.getHeight()) / 24);
            int padding = Math.max(12, fontSize / 2);
            String[] lines = {
                    "时间: " + FORMATTER.format(timestamp),
                    "订单号: " + orderId
            };

            graphics.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics metrics = graphics.getFontMetrics();
            int maxLineWidth = 0;
            for (String line : lines) {
                maxLineWidth = Math.max(maxLineWidth, metrics.stringWidth(line));
            }

            int lineHeight = metrics.getHeight();
            int boxWidth = maxLineWidth + padding * 2;
            int boxHeight = lineHeight * lines.length + padding * 2;
            int x = Math.max(padding, source.getWidth() - boxWidth - padding);
            int y = Math.max(padding, source.getHeight() - boxHeight - padding);

            graphics.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            graphics.setColor(Color.BLACK);
            graphics.fillRoundRect(x, y, boxWidth, boxHeight, 16, 16);

            graphics.setComposite(AlphaComposite.SrcOver.derive(0.92f));
            graphics.setColor(Color.WHITE);
            int baseLine = y + padding + metrics.getAscent();
            for (String line : lines) {
                graphics.drawString(line, x + padding, baseLine);
                baseLine += lineHeight;
            }
            graphics.dispose();

            ImageIO.write(target, outputFormat, outputStream);
            return outputStream.toByteArray();
        }
    }

    public static String normalizeFormat(String formatName) {
        if (!StringUtils.hasText(formatName)) {
            return "jpg";
        }
        String normalized = formatName.trim().toLowerCase();
        if ("jpeg".equals(normalized)) {
            return "jpg";
        }
        if (!"jpg".equals(normalized) && !"png".equals(normalized)) {
            throw new IllegalArgumentException("仅支持 PNG/JPG/JPEG 图片");
        }
        return normalized;
    }
}
