package com.medai.upload.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Storage backend configuration.
 *
 * <p>{@code type} selects the implementation. {@code local} is single-instance only; anything
 * running more than one replica must use {@code s3}, which covers any S3-compatible endpoint
 * (AWS, Cloudflare R2, MinIO, Ceph) via {@code endpoint}.
 */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    private String type = "local";

    private String localPath = "./uploads";

    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {

        private String bucket;

        private String region = "us-east-1";

        /**
         * Override for a non-AWS S3-compatible endpoint. Leave unset for AWS itself.
         */
        private String endpoint;

        /**
         * Required by MinIO and most self-hosted gateways, which do not implement virtual-host
         * style addressing. Ignored by AWS and R2.
         */
        private boolean pathStyleAccess = false;

        /**
         * KMS key for server-side encryption. When set, objects are written with SSE-KMS; when
         * unset, with SSE-S3. There is no unencrypted path — PHI at rest is encrypted either way,
         * and a customer-managed key is the upgrade rather than the on-switch.
         */
        private String kmsKeyId;

        /**
         * Static credentials. Leave both unset to use the default AWS credential chain, which is
         * what an IRSA / instance-profile deployment wants.
         */
        private String accessKey;

        private String secretKey;
    }
}
