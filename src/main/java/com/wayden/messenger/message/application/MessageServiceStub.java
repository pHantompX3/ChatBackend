package com.wayden.messenger.message.application;

import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.message.api.MessageResponse;
import com.wayden.messenger.message.api.SendMessageRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MessageServiceStub implements MessageService {

    private static final Logger LOG = Logger.getLogger(MessageServiceStub.class);

    private final RequestAuditContext requestAuditContext;

    @Override
    public MessageResponse send(SendMessageRequest request) {
        //requestAuditContext.redactDeviceIdentity();
        //requestAuditContext.putCustomAttribute("redaction", "device");
        LOG.infof("service.send requestId=%s", requestAuditContext.getRequestId());
        throw new WebApplicationException("Not implemented yet", Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public List<MessageResponse> listByConversation(String conversationId, int limit) {
        //requestAuditContext.redactNetworkIdentity();
        //requestAuditContext.putCustomAttribute("redaction", "network");
        LOG.infof("service.listByConversation requestId=%s", requestAuditContext.getRequestId());
        throw new WebApplicationException("Not implemented yet", Response.Status.NOT_IMPLEMENTED);
    }
}
