package com.campusexpress.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.campusexpress.config.OssProperties;
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

    private final OssProperties ossProperties;

    public EvidenceStorageService(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    public String upload(String objectKey, byte[] content, String contentType) {
        if (ossProperties.isEnabled()) {
            validateOssConfig();
            OSS client = buildClient();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(content.length);
                metadata.setContentType(contentType);
                client.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
                return objectKey;
            } catch (IOException ex) {
                throw new IllegalStateException("上传 OSS 失败", ex);
            } finally {
                client.shutdown();
            }
        }

        Path target = Paths.get(ossProperties.getLocalDir()).resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return objectKey;
        } catch (IOException ex) {
            throw new IllegalStateException("保存本地存证文件失败", ex);
        }
    }

    public StoredFile download(String objectKey) {
        if (ossProperties.isEnabled()) {
            validateOssConfig();
            OSS client = buildClient();
            try (OSSObject object = client.getObject(ossProperties.getBucketName(), objectKey)) {
                String contentType = object.getObjectMetadata().getContentType();
                byte[] content = StreamUtils.copyToByteArray(object.getObjectContent());
                return new StoredFile(content, contentType);
            } catch (IOException ex) {
                throw new IllegalStateException("读取 OSS 存证文件失败", ex);
            } finally {
                client.shutdown();
            }
        }

        Path target = Paths.get(ossProperties.getLocalDir()).resolve(objectKey);
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
            if (ossProperties.isEnabled()) {
                validateOssConfig();
                OSS client = buildClient();
                try {
                    client.deleteObject(ossProperties.getBucketName(), objectKey);
                } finally {
                    client.shutdown();
                }
                return;
            }

            Files.deleteIfExists(Paths.get(ossProperties.getLocalDir()).resolve(objectKey));
        } catch (Exception ignored) {
        }
    }

    public String buildObjectKey(String objectKey) {
        String folder = trimSlashes(ossProperties.getFolder());
        if (!StringUtils.hasText(folder)) {
            return objectKey;
        }
        return folder + "/" + objectKey;
    }

    private OSS buildClient() {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }

    private void validateOssConfig() {
        if (!StringUtils.hasText(ossProperties.getEndpoint())
                || !StringUtils.hasText(ossProperties.getAccessKeyId())
                || !StringUtils.hasText(ossProperties.getAccessKeySecret())
                || !StringUtils.hasText(ossProperties.getBucketName())) {
            throw new IllegalStateException("OSS 已启用，但 endpoint/accessKeyId/accessKeySecret/bucketName 未完整配置");
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
