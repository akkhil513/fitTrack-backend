package com.fitTrack.resource;

import com.fitTrack.mapper.LogMapper;
import com.fitTrack.model.FitLog;
import com.fitTrack.repository.LogRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.stream.Collectors;

@Path("/logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class LogResource {

    @Inject
    LogRepository logRepository;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/createLog")
    @RolesAllowed("**")
    public Response saveLog(FitLog log) {
        log.setUserId(jwt.getSubject());
        logRepository.saveLog(LogMapper.toMap(log));
        return Response.ok("{\"message\": \"Log saved successfully!\"}").build();
    }

    // GET /logs/{userId}/{date} — gets log for specific date
    @GET
    @Path("/{userId}/{date}")
    @RolesAllowed("**")
    public Response getLog(@PathParam("userId") String userId, @PathParam("date") String date) {
        var item = logRepository.getLog(userId, date);
        if (item == null || item.isEmpty()) {
            return Response.status(404).build();
        }
        return Response.ok(LogMapper.toLog(item)).build();
    }

    // GET /logs/{userId} — gets all logs for a user
    @GET
    @Path("/{userId}")
    @RolesAllowed("**")
    public Response getLogs(@PathParam("userId") String userId) {
        var items = logRepository.getLogs(userId);
        var logs = items.stream().map(LogMapper::toLog).collect(Collectors.toList());
        return Response.ok(logs).build();
    }

    @PUT
    @Path("/update/{userId}/{date}")
    @RolesAllowed("**")
    public Response updateLog(@PathParam("userId") String userId, @PathParam("date") String date, FitLog log) {
        var existing = logRepository.getLog(userId, date);
        if (existing == null || existing.isEmpty()) {
            return Response.status(404).entity("{\"message\": \"Log not found\"}").build();
        }
        log.setUserId(userId);
        log.setDate(date);
        logRepository.updateLog(userId, date, LogMapper.toMap(log));
        return Response.ok("{\"message\": \"Log updated successfully!\"}").build();
    }
}