package com.fitTrack.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper over the private progress-photo bucket. The bucket name comes from the
 * FITTRACK_PHOTOS_BUCKET environment variable (set by the CloudFormation template).
 * Clients and the presigner are created once per container.
 */
@ApplicationScoped
public class ProgressPhotoStore {

    public static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(5);
    public static final Duration VIEW_URL_TTL = Duration.ofMinutes(10);

    @ConfigProperty(name = "fittrack.photos.bucket", defaultValue = "")
    String bucket;

    private S3Client s3;
    private S3Presigner presigner;

    public boolean isConfigured() {
        return bucket != null && !bucket.isBlank();
    }

    private synchronized void init() {
        if (s3 == null) {
            s3 = S3Client.builder().region(Region.US_EAST_1).build();
            presigner = S3Presigner.builder().region(Region.US_EAST_1).build();
        }
    }

    /** One stored photo. */
    public record Stored(String key, long size) {
    }

    /** All photos under the prefix, up to [limit]. */
    public List<Stored> list(String prefix, int limit) {
        init();
        List<Stored> out = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .maxKeys(Math.min(limit, 1000))
                    .continuationToken(token)
                    .build());
            for (S3Object o : page.contents()) {
                out.add(new Stored(o.key(), o.size()));
                if (out.size() >= limit) {
                    return out;
                }
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
        return out;
    }

    /**
     * A short-lived PUT URL. The content type and exact length are part of the signature, so S3
     * refuses an upload that differs from what the API approved.
     */
    public String presignUpload(String key, String contentType, long contentLength) {
        init();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(put)
                .build()).url().toString();
    }

    /** A short-lived GET URL for displaying one photo. */
    public String presignView(String key) {
        init();
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VIEW_URL_TTL)
                .getObjectRequest(get)
                .build()).url().toString();
    }

    public void delete(String key) {
        init();
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
