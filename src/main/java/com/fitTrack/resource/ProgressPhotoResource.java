package com.fitTrack.resource;

import com.fitTrack.security.CallerIdentity;
import com.fitTrack.service.ProgressPhotoKeys;
import com.fitTrack.service.ProgressPhotoStore;
import com.fitTrack.service.SlidingWindowLimiter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.Data;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Private progress photos. The bucket is never public: the app uploads with a short-lived signed
 * PUT URL and views photos through short-lived signed GET URLs. Every route checks the caller owns
 * the user id, and every key is checked to sit under that user's own prefix.
 */
@Path("/photos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProgressPhotoResource {

    private static final Logger LOG = Logger.getLogger(ProgressPhotoResource.class);

    /** Best effort, per warm container: stops one user from minting upload URLs in a loop. */
    private static final SlidingWindowLimiter UPLOAD_LIMIT = new SlidingWindowLimiter(30, Duration.ofHours(1));

    @Inject
    ProgressPhotoStore store;

    @Data
    public static class UploadRequest {
        private String date;
        private String angle;
        private String contentType;
        private Long size;
    }

    private static Response bad(String message) {
        return Response.status(400).entity(Map.of("message", message)).build();
    }

    private static Response unavailable() {
        return Response.status(503).entity(Map.of("message", "Photos are not available right now")).build();
    }

    @POST
    @Path("/{userId}/upload-url")
    public Response uploadUrl(@HeaderParam("Authorization") String authorization,
                              @PathParam("userId") String userId,
                              UploadRequest body) {
        CallerIdentity.requireSelf(authorization, userId);
        if (!store.isConfigured()) {
            return unavailable();
        }
        if (body == null) {
            return bad("Missing request body");
        }
        String key;
        try {
            ProgressPhotoKeys.requireContentType(body.getContentType());
            ProgressPhotoKeys.requireSize(body.getSize());
            key = ProgressPhotoKeys.newKey(userId, body.getDate(), body.getAngle(), UUID.randomUUID().toString());
        } catch (IllegalArgumentException e) {
            // Messages are fixed strings from ProgressPhotoKeys, never echoed user input.
            return bad(e.getMessage());
        }
        if (!UPLOAD_LIMIT.tryAcquire(userId, System.currentTimeMillis())) {
            return Response.status(429).entity(Map.of("message", "Too many uploads. Try again later.")).build();
        }
        try {
            if (store.list(ProgressPhotoKeys.prefixFor(userId), ProgressPhotoKeys.MAX_PHOTOS).size()
                    >= ProgressPhotoKeys.MAX_PHOTOS) {
                return bad("Photo limit reached. Delete some photos first.");
            }
            String url = store.presignUpload(key, ProgressPhotoKeys.CONTENT_TYPE, body.getSize());
            return Response.ok(Map.of(
                    "key", key,
                    "uploadUrl", url,
                    "contentType", ProgressPhotoKeys.CONTENT_TYPE,
                    "expiresInSeconds", ProgressPhotoStore.UPLOAD_URL_TTL.toSeconds())).build();
        } catch (RuntimeException e) {
            LOG.errorf("Could not prepare photo upload: %s", e.getClass().getSimpleName());
            return Response.status(502).entity(Map.of("message", "Could not prepare the upload")).build();
        }
    }

    @GET
    @Path("/{userId}")
    public Response list(@HeaderParam("Authorization") String authorization,
                         @PathParam("userId") String userId) {
        CallerIdentity.requireSelf(authorization, userId);
        if (!store.isConfigured()) {
            return unavailable();
        }
        try {
            List<Map<String, Object>> photos = new ArrayList<>();
            var stored = store.list(ProgressPhotoKeys.prefixFor(userId), ProgressPhotoKeys.MAX_PHOTOS);
            stored.sort(Comparator.comparing(ProgressPhotoStore.Stored::key).reversed());
            for (var s : stored) {
                var ref = ProgressPhotoKeys.parse(s.key());
                if (ref.isEmpty() || !ref.get().userId().equals(userId)) {
                    continue;
                }
                photos.add(Map.of(
                        "key", s.key(),
                        "date", ref.get().date(),
                        "angle", ref.get().angle(),
                        "size", s.size(),
                        "url", store.presignView(s.key())));
            }
            return Response.ok(Map.of("photos", photos)).build();
        } catch (RuntimeException e) {
            LOG.errorf("Could not list photos: %s", e.getClass().getSimpleName());
            return Response.status(502).entity(Map.of("message", "Could not load photos")).build();
        }
    }

    @DELETE
    @Path("/{userId}")
    public Response delete(@HeaderParam("Authorization") String authorization,
                           @PathParam("userId") String userId,
                           @QueryParam("key") String key) {
        CallerIdentity.requireSelf(authorization, userId);
        if (!store.isConfigured()) {
            return unavailable();
        }
        if (!ProgressPhotoKeys.belongsTo(userId, key)) {
            return bad("Invalid photo");
        }
        try {
            store.delete(key);
            return Response.noContent().build();
        } catch (RuntimeException e) {
            LOG.errorf("Could not delete photo: %s", e.getClass().getSimpleName());
            return Response.status(502).entity(Map.of("message", "Could not delete the photo")).build();
        }
    }
}
