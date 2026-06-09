package com.campusexpress.service;

import com.campusexpress.config.ObsProperties;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class EvidenceStorageService {

    private final ObsProperties obsProperties;

    public EvidenceStorageService(ObsProperties obsProperties) {
        this.obsProperties = obsProperties;
    }

    public String upload(String objectKey, byte[] content, String contentType) {
        if (obsProperties.isEnabled()) {
            validateObsConfig();
            ObsClient client = buildClient();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(Long.valueOf(content.length));
                metadata.setContentType(contentType);
                client.putObject(obsProperties.getBucketName(), objectKey, inputStream, metadata);
                return objectKey;
            } catch (IOException ex) {
                throw new IllegalStateException("上传 OBS 失败", ex);
            } catch (ObsException ex) {
                throw new IllegalStateException("上传 OBS 失败: " + ex.getErrorMessage(), ex);
            } finally {
                closeClientQuietly(client);
            }
        }

        Path target = Paths.get(obsProperties.getLocalDir()).resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return objectKey;
        } catch (IOException ex) {
            throw new IllegalStateException("保存本地存证文件失败", ex);
        }
    }

    public StoredFile download(String objectKey) {
        if (obsProperties.isEnabled()) {
            validateObsConfig();
            ObsClient client = buildClient();
            try {
                ObsObject object = client.getObject(obsProperties.getBucketName(), objectKey);
                String contentType = object.getMetadata().getContentType();
                try {
                    byte[] content = StreamUtils.copyToByteArray(object.getObjectContent());
                    return new StoredFile(content, contentType);
                } finally {
                    object.getObjectContent().close();
                }
            } catch (IOException ex) {
                throw new IllegalStateException("读取 OBS 存证文件失败", ex);
            } catch (ObsException ex) {
                throw new IllegalStateException("读取 OBS 存证文件失败: " + ex.getErrorMessage(), ex);
            } finally {
                closeClientQuietly(client);
            }
        }

        Path target = Paths.get(obsProperties.getLocalDir()).resolve(objectKey);
        try {
            byte[] content = Files.readAllBytes(target);
            String contentType = Files.probeContentType(target);
            return new StoredFile(content, contentType);
        } catch (IOException ex) {
            throw new IllegalStateException("读取本地存证文件失败", ex);
        }
    }

    public void deleteQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            if (obsProperties.isEnabled()) {
                validateObsConfig();
                ObsClient client = buildClient();
                try {
                    client.deleteObject(obsProperties.getBucketName(), objectKey);
                } finally {
                    closeClientQuietly(client);
                }
                return;
            }

            Files.deleteIfExists(Paths.get(obsProperties.getLocalDir()).resolve(objectKey));
        } catch (Exception ignored) {
        }
    }

    public String buildObjectKey(String objectKey) {
        String folder = trimSlashes(obsProperties.getFolder());
        if (!StringUtils.hasText(folder)) {
            return objectKey;
        }
        return folder + "/" + objectKey;
    }

    private ObsClient buildClient() {
        return new ObsClient(
                obsProperties.getAccessKeyId(),
                obsProperties.getSecretAccessKey(),
                normalizeEndpoint(obsProperties.getEndpoint())
        );
    }

    private void validateObsConfig() {
        if (!StringUtils.hasText(obsProperties.getEndpoint())
                || !StringUtils.hasText(obsProperties.getAccessKeyId())
                || !StringUtils.hasText(obsProperties.getSecretAccessKey())
                || !StringUtils.hasText(obsProperties.getBucketName())) {
            throw new IllegalStateException("OBS 已启用，但 endpoint/accessKeyId/secretAccessKey/bucketName 未完整配置");
        }
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private void closeClientQuietly(ObsClient client) {
        try {
            client.close();
        } catch (IOException ignored) {
        }
    }

    private String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static class StoredFile {
        private final byte[] content;
        private final String contentType;

        public StoredFile(byte[] content, String contentType) {
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
