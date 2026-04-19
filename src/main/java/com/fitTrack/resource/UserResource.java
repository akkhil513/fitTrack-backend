package com.fitTrack.resource;

import com.fitTrack.mapper.UserMapper;
import com.fitTrack.model.UserProfile;
import com.fitTrack.repository.UserRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    @Inject
    UserRepository userRepository;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/create")
    @RolesAllowed("**")
    public Response createUser(UserProfile user) {
        user.setUserId((jwt.getSubject()));
        userRepository.saveUser(UserMapper.toMap(user));
        return Response.ok("{\"message\": \"Yaay! " + user.getFirstName() + ", your account has been created successfully!\"}").build();
    }

    @GET
    @Path("/{userId}")
    @RolesAllowed("**")
    public Response getUser(@PathParam("userId") String userId) {
        var item = userRepository.getUser(userId);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(UserMapper.toUser(item)).build();
    }

    @PUT
    @Path("/update/{userId}")
    @RolesAllowed("**")
    public Response updateUser(@PathParam("userId") String userId, UserProfile user) {
        var existing = userRepository.getUser(userId);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).build();
        }
        userRepository.updateUser(userId, user.getFirstName(), user.getLastName());
        return Response.ok("{\"message\": \"Profile updated successfully!\"}").build();
    }
}