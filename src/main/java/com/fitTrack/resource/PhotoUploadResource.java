package com.fitTrack.resource;

import com.fitTrack.repository.UserRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.util.Base64;
import java.util.UUID;

@Path("/users/photo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PhotoUploadResource {

    @Inject
    UserRepository userRepository;

    private final S3Client s3Client = S3Client.builder()
        .region(software.amazon.awssdk.regions.Region.US_EAST_1)
        .build();

    private static final String BUCKET = "fittrack-user-uploads";

    @Data
    public static class PhotoRequest {
        private String userId;
        private String base64Image;
        private String contentType;
    }

    @POST
    @Path("/upload")
    public Response uploadPhoto(PhotoRequest request) {
        try {
            // Decode base64
            String base64 = request.getBase64Image()
                .replaceAll("^data:image/[a-z]+;base64,", "");
            byte[] imageBytes = Base64.getDecoder().decode(base64);

            // Generate unique filename
            String ext = request.getContentType().contains("png") ? "png" : "jpg";
            String key = "profiles/" + request.getUserId() + "/" + UUID.randomUUID() + "." + ext;

            // Upload to S3
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(key)
                    .contentType(request.getContentType())
                    .build(),
                RequestBody.fromBytes(imageBytes)
            );

            // Build public URL
            String photoUrl = "https://" + BUCKET + ".s3.amazonaws.com/" + key;

            // Save URL to DynamoDB
            userRepository.updatePhotoUrl(request.getUserId(), photoUrl);

            return Response.ok("{\"photoUrl\": \"" + photoUrl + "\"}").build();

        } catch (Exception e) {
            return Response.serverError()
                .entity("{\"error\": \"" + e.getMessage() + "\"}")
                .build();
        }
    }
}

