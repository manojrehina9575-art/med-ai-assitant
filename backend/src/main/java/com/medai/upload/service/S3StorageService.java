package com.medai.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/**
 * Object-storage backend, and the only one that survives more than one instance.
 *
 * <p>{@link LocalStorageService} writes to the pod's own filesystem. The Kubernetes manifests run
 * three replicas behind a service with no shared volume, so an upload landed on one pod and
 * roughly two out of three download requests reached a pod that had never seen the file. In a
 * clinical product an intermittently missing scan is an incident, not a bug report.
 *
 * <p>Keys are {@code {tenantId}/patients/{patientId}/{fileName}} — the same layout the local
 * backend produces, so a stored path means the same thing under either. The tenant prefix is first
 * deliberately: it is what a per-tenant bucket policy, a KMS grant, or a tenant-scoped export all
 * need to key off.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final StorageProperties.S3 config;

    public S3StorageService(StorageProperties properties) {
        this.config = properties.getS3();

        if (config.getBucket() == null || config.getBucket().isBlank()) {
            throw new IllegalStateException(
                    "app.storage.type=s3 requires app.storage.s3.bucket (STORAGE_S3_BUCKET).");
        }

        var builder = S3Client.builder().region(Region.of(config.getRegion()));

        if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(config.getEndpoint()));
        }
        if (config.isPathStyleAccess()) {
            builder.forcePathStyle(true);
        }
        if (config.getAccessKey() != null && !config.getAccessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())));
        } else {
            // Instance profile / IRSA / environment. Preferred: no long-lived key to rotate.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        this.client = builder.build();

        verifyBucketReachable();

        log.info("File storage backend: s3 (bucket={}, region={}, endpoint={}, sse={})",
                config.getBucket(), config.getRegion(),
                config.getEndpoint() != null ? config.getEndpoint() : "aws",
                config.getKmsKeyId() != null ? "SSE-KMS" : "SSE-S3");
    }

    /**
     * Fails startup on a bucket that is missing or unreachable.
     *
     * <p>Without this the first symptom of a misconfigured bucket is a clinician's upload failing,
     * which is both the worst time to find out and the hardest place to read the error.
     */
    private void verifyBucketReachable() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(config.getBucket()).build());
        } catch (NoSuchBucketException e) {
            throw new IllegalStateException("Storage bucket does not exist: " + config.getBucket(), e);
        } catch (S3Exception e) {
            throw new IllegalStateException(
                    "Storage bucket " + config.getBucket() + " is not reachable with the configured "
                    + "credentials: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    private String key(UUID tenantId, UUID patientId, String fileName) {
        return tenantId + "/patients/" + patientId + "/" + fileName;
    }

    @Override
    public String store(UUID tenantId, UUID patientId, String fileName, MultipartFile file) {
        String key = key(tenantId, patientId, fileName);

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize());

        if (config.getKmsKeyId() != null && !config.getKmsKeyId().isBlank()) {
            request.serverSideEncryption(ServerSideEncryption.AWS_KMS)
                   .ssekmsKeyId(config.getKmsKeyId());
        } else {
            request.serverSideEncryption(ServerSideEncryption.AES256);
        }

        try (InputStream in = file.getInputStream()) {
            client.putObject(request.build(), RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException | SdkException e) {
            throw new StorageException("Failed to store file: " + fileName, e);
        }

        log.info("Stored object: {}", key);
        return key;
    }

    @Override
    public InputStream retrieve(String storagePath) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(storagePath)
                    .build());
        } catch (NoSuchKeyException e) {
            throw new StorageException("File not found: " + storagePath, e);
        } catch (SdkException e) {
            throw new StorageException("Failed to retrieve file: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(storagePath)
                    .build());
            log.info("Deleted object: {}", storagePath);
        } catch (SdkException e) {
            throw new StorageException("Failed to delete file: " + storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        try {
            client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(storagePath)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            throw new StorageException("Failed to stat file: " + storagePath, e);
        }
    }

    /**
     * Reads the whole object into memory.
     *
     * <p>Callers ({@code ImageAnalysisService}, {@code BloodReportAnalysisService}) need a
     * re-readable resource with a known length, and a stream-backed one gives them neither.
     * Uploads are capped at 100MB and the analysis executor is bounded, so the ceiling on
     * concurrent copies is the pool size rather than the request rate.
     */
    @Override
    public Resource retrieveAsResource(String storagePath) {
        try (InputStream in = retrieve(storagePath)) {
            return new ByteArrayResource(in.readAllBytes());
        } catch (IOException e) {
            throw new StorageException("Failed to read file: " + storagePath, e);
        }
    }
}
