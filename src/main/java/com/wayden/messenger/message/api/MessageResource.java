package com.wayden.messenger.message.api;

import com.wayden.messenger.common.api.ApiRoutes;
import com.wayden.messenger.common.http.AuditOperation;
import com.wayden.messenger.message.application.MessageService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Path(ApiRoutes.API_V1)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MessageResource {

    private final MessageService messageService;

    @POST
    @Path("/messages")
    @AuditOperation("send.message")
    public MessageResponse sendMessage(@Valid SendMessageRequest request) {
        return messageService.send(request);
    }

    @GET
    @Path("/conversations/{conversationId}/messages")
    @AuditOperation("list.messages")
    public List<MessageResponse> listMessages(
            @PathParam("conversationId") String conversationId,
            @QueryParam("limit") Integer limit
    ) {
        int resolvedLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        return messageService.listByConversation(conversationId, resolvedLimit);
    }
}
