package com.fitTrack.resource;

import com.fitTrack.mapper.UserMapper;
import com.fitTrack.model.UserProfile;
import com.fitTrack.repository.UserRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserRepository userRepository;

    @POST
    @Path("/create")
    public Response createUser(UserProfile user) {
        userRepository.saveUser(UserMapper.toMap(user));
        return Response.ok("{\"message\": \"Yaay! " + user.getFirstName() + ", your account has been created successfully!\"}").build();
    }

    @GET
    @Path("/{userId}")
    public Response getUser(@PathParam("userId") String userId) {
        var item = userRepository.getUser(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(UserMapper.toUser(item)).build();
    }

    @PUT
    @Path("/update/{userId}")
    public Response updateUser(@PathParam("userId") String userId, UserProfile user) {
        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateUser(userId, user.getFirstName(), user.getLastName(), user.getStartDate());
        return Response.ok("{\"message\": \"Profile updated successfully!\"}").build();
    }

    // GET /users/check/{username} — check if username is taken
    @GET
    @Path("/check/{username}")
    public Response checkUsername(@PathParam("username") String username) {
        boolean taken = userRepository.isUsernameTaken(username);
        if (taken) {
            return Response.status(409)
                    .entity("{\"message\": \"Username already taken\", \"available\": false}")
                    .build();
        }
        return Response.ok("{\"message\": \"Username available\", \"available\": true}").build();
    }

    @PUT
    @Path("/measurements/{userId}")
    public Response updateMeasurements(@PathParam("userId") String userId, UserProfile user) {
        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateMeasurements(userId, user.getMeasurements(), user.getMeasurementHistory() != null ? user.getMeasurementHistory() : " ");
        return Response.ok("{\"message\": \"Measurements saved!\"}").build();
    }
}