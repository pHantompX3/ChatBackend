package com.wayden.messenger.conversation.application;

import com.wayden.messenger.common.api.PaginationPolicy;
import com.wayden.messenger.common.http.RequestAuditContext;
import com.wayden.messenger.conversation.application.ConversationCursorCodec.ConversationCursor;
import com.wayden.messenger.conversation.application.ConversationCursorCodec.MemberCursor;
import com.wayden.messenger.conversation.application.ConversationRepository.ConversationView;
import com.wayden.messenger.conversation.domain.Conversation;
import com.wayden.messenger.conversation.domain.ConversationId;
import com.wayden.messenger.conversation.domain.ConversationMember;
import com.wayden.messenger.conversation.domain.ConversationRole;
import com.wayden.messenger.conversation.domain.ConversationTitle;
import com.wayden.messenger.conversation.domain.ConversationType;
import com.wayden.messenger.conversation.domain.DirectParticipantPair;
import com.wayden.messenger.identity.application.UserRepository;
import com.wayden.messenger.identity.domain.User;
import com.wayden.messenger.identity.domain.UserId;
import com.wayden.messenger.identity.domain.UserStatus;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

@ApplicationScoped
@Transactional
public class ConversationServiceImpl implements ConversationService {

  private final ConversationRepository conversationRepository;
  private final UserRepository userRepository;
  private final ConversationCursorCodec cursorCodec;
  private final Clock clock;
  private final RequestAuditContext auditContext;

  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Clock and request audit context are container-managed collaborators.")
  public ConversationServiceImpl(
      ConversationRepository conversationRepository,
      UserRepository userRepository,
      ConversationCursorCodec cursorCodec,
      Clock clock,
      RequestAuditContext auditContext) {
    this.conversationRepository = conversationRepository;
    this.userRepository = userRepository;
    this.cursorCodec = cursorCodec;
    this.clock = clock;
    this.auditContext = auditContext;
  }

  @Override
  public DirectResult createDirect(UserId actorUserId, UserId targetUserId) {
    requireActiveUser(actorUserId);
    requireActiveUser(targetUserId);
    DirectParticipantPair pair;
    try {
      pair = DirectParticipantPair.of(actorUserId, targetUserId);
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.ValidationException(exception.getMessage());
    }

    var existing = conversationRepository.findDirect(pair, true);
    if (existing.isPresent()) {
      audit("conversation.direct.resolved", existing.orElseThrow().id(), targetUserId);
      return new DirectResult(
          new ConversationView(existing.orElseThrow(), ConversationRole.MEMBER), false);
    }

    Instant now = clock.instant();
    Conversation conversation =
        new Conversation(
            ConversationId.newId(), ConversationType.DIRECT, null, actorUserId, 1, now, now);
    try {
      conversationRepository.createDirect(conversation, pair, now);
    } catch (ConversationExceptions.DuplicateDirectPairException exception) {
      Conversation winner =
          conversationRepository
              .findDirect(pair, false)
              .orElseThrow(
                  () -> new IllegalStateException("Direct conversation race had no winner"));
      audit("conversation.direct.resolved", winner.id(), targetUserId);
      return new DirectResult(new ConversationView(winner, ConversationRole.MEMBER), false);
    }
    audit("conversation.direct.created", conversation.id(), targetUserId);
    return new DirectResult(new ConversationView(conversation, ConversationRole.MEMBER), true);
  }

  @Override
  public ConversationView createGroup(
      UserId actorUserId, String rawTitle, List<UserId> rawInitialMemberIds) {
    requireActiveUser(actorUserId);
    ConversationTitle title;
    try {
      title = new ConversationTitle(rawTitle);
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.ValidationException(exception.getMessage());
    }

    LinkedHashSet<UserId> memberIds =
        new LinkedHashSet<>(rawInitialMemberIds == null ? List.of() : rawInitialMemberIds);
    memberIds.remove(actorUserId);
    memberIds.forEach(this::requireActiveUser);

    Instant now = clock.instant();
    Conversation conversation =
        new Conversation(
            ConversationId.newId(), ConversationType.GROUP, title, actorUserId, 1, now, now);
    conversationRepository.createGroup(conversation, actorUserId, List.copyOf(memberIds), now);
    audit("conversation.group.created", conversation.id(), null);
    return new ConversationView(conversation, ConversationRole.OWNER);
  }

  @Override
  public ConversationPage list(UserId actorUserId, String rawCursor, Integer rawLimit) {
    ConversationCursor cursor;
    int limit;
    try {
      PaginationPolicy.requireValidCursorLength(rawCursor);
      cursor = cursorCodec.decodeConversation(rawCursor);
      limit = PaginationPolicy.resolveLimit(rawLimit, 50, 100);
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.ValidationException(exception.getMessage());
    }
    List<ConversationView> rows =
        conversationRepository.listAccessible(actorUserId, cursor, limit + 1);
    boolean hasNext = rows.size() > limit;
    List<ConversationView> items = hasNext ? rows.subList(0, limit) : rows;
    String nextCursor = null;
    if (hasNext) {
      Conversation last = items.get(items.size() - 1).conversation();
      nextCursor =
          cursorCodec.encodeConversation(new ConversationCursor(last.updatedAt(), last.id()));
    }
    return new ConversationPage(items, nextCursor);
  }

  @Override
  public ConversationView get(UserId actorUserId, ConversationId conversationId) {
    return requireAccessible(actorUserId, conversationId);
  }

  @Override
  public MemberPage listMembers(
      UserId actorUserId, ConversationId conversationId, String rawCursor, Integer rawLimit) {
    requireAccessible(actorUserId, conversationId);
    MemberCursor cursor;
    int limit;
    try {
      PaginationPolicy.requireValidCursorLength(rawCursor);
      cursor = cursorCodec.decodeMember(rawCursor, conversationId);
      limit = PaginationPolicy.resolveLimit(rawLimit, 50, 100);
    } catch (IllegalArgumentException exception) {
      throw new ConversationExceptions.ValidationException(exception.getMessage());
    }
    List<ConversationMember> rows =
        conversationRepository.listActiveMembers(conversationId, cursor, limit + 1);
    boolean hasNext = rows.size() > limit;
    List<ConversationMember> items = hasNext ? rows.subList(0, limit) : rows;
    String nextCursor = null;
    if (hasNext) {
      ConversationMember last = items.get(items.size() - 1);
      nextCursor =
          cursorCodec.encodeMember(
              new MemberCursor(conversationId, last.joinedAt(), last.userId()));
    }
    return new MemberPage(items, nextCursor);
  }

  @Override
  public void addMember(UserId actorUserId, ConversationId conversationId, UserId targetUserId) {
    ConversationView view = requireGroupManager(actorUserId, conversationId);
    requireActiveUser(targetUserId);
    conversationRepository.addOrReactivateMember(conversationId, targetUserId, clock.instant());
    audit("conversation.member.added", view.conversation().id(), targetUserId);
  }

  @Override
  public void removeMember(UserId actorUserId, ConversationId conversationId, UserId targetUserId) {
    ConversationView actor = requireGroupManager(actorUserId, conversationId);
    if (actorUserId.equals(targetUserId)) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Use the leave operation to remove your own membership");
    }
    var target = conversationRepository.findMembership(conversationId, targetUserId);
    if (target.isEmpty() || !target.orElseThrow().isActive()) {
      return;
    }
    ConversationRole targetRole = target.orElseThrow().role();
    if (targetRole == ConversationRole.OWNER) {
      throw new ConversationExceptions.OwnershipRequiredException(
          "Ownership must be transferred before removing the owner");
    }
    if (actor.actorRole() == ConversationRole.ADMIN && targetRole != ConversationRole.MEMBER) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Conversation admins may remove members only");
    }
    conversationRepository.markMemberLeft(conversationId, targetUserId, clock.instant());
    audit("conversation.member.removed", conversationId, targetUserId);
  }

  @Override
  public void leave(UserId actorUserId, ConversationId conversationId) {
    ConversationView actor = requireGroup(actorUserId, conversationId);
    if (actor.actorRole() == ConversationRole.OWNER) {
      throw new ConversationExceptions.OwnershipRequiredException(
          "Ownership must be transferred before the owner can leave");
    }
    conversationRepository.markMemberLeft(conversationId, actorUserId, clock.instant());
    audit("conversation.member.left", conversationId, actorUserId);
  }

  @Override
  public void changeRole(
      UserId actorUserId,
      ConversationId conversationId,
      UserId targetUserId,
      ConversationRole role) {
    ConversationView actor = requireGroup(actorUserId, conversationId);
    if (actor.actorRole() != ConversationRole.OWNER) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Only the conversation owner may change member roles");
    }
    if (role == null || role == ConversationRole.OWNER) {
      throw new ConversationExceptions.ValidationException(
          "Role must be ADMIN or MEMBER; use ownership transfer for OWNER");
    }
    ConversationMember target = requireActiveMember(conversationId, targetUserId);
    if (target.role() == ConversationRole.OWNER) {
      throw new ConversationExceptions.OwnershipRequiredException(
          "The owner role changes only through ownership transfer");
    }
    conversationRepository.changeRole(conversationId, targetUserId, role, clock.instant());
    audit("conversation.member.role.changed", conversationId, targetUserId);
  }

  @Override
  public void transferOwnership(
      UserId actorUserId, ConversationId conversationId, UserId targetUserId) {
    ConversationView actor = requireGroup(actorUserId, conversationId);
    if (actor.actorRole() != ConversationRole.OWNER) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Only the conversation owner may transfer ownership");
    }
    if (actorUserId.equals(targetUserId)) {
      return;
    }
    requireActiveMember(conversationId, targetUserId);
    conversationRepository.transferOwnership(
        conversationId, actorUserId, targetUserId, clock.instant());
    audit("conversation.ownership.transferred", conversationId, targetUserId);
  }

  private ConversationView requireAccessible(UserId actorUserId, ConversationId conversationId) {
    return conversationRepository
        .findAccessible(conversationId, actorUserId)
        .orElseThrow(ConversationExceptions.AccessDeniedException::new);
  }

  private ConversationView requireGroup(UserId actorUserId, ConversationId conversationId) {
    ConversationView view = requireAccessible(actorUserId, conversationId);
    if (view.conversation().type() != ConversationType.GROUP) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Membership changes are not supported for direct conversations");
    }
    return view;
  }

  private ConversationView requireGroupManager(UserId actorUserId, ConversationId conversationId) {
    ConversationView view = requireGroup(actorUserId, conversationId);
    if (view.actorRole() != ConversationRole.OWNER && view.actorRole() != ConversationRole.ADMIN) {
      throw new ConversationExceptions.RoleForbiddenException(
          "Conversation owner or admin role is required");
    }
    return view;
  }

  private ConversationMember requireActiveMember(
      ConversationId conversationId, UserId targetUserId) {
    ConversationMember member =
        conversationRepository
            .findMembership(conversationId, targetUserId)
            .orElseThrow(ConversationExceptions.AccessDeniedException::new);
    if (!member.isActive()) {
      throw new ConversationExceptions.AccessDeniedException();
    }
    return member;
  }

  private User requireActiveUser(UserId userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(ConversationExceptions.UserNotFoundException::new);
    if (user.status() != UserStatus.ACTIVE) {
      throw new ConversationExceptions.UserNotFoundException();
    }
    return user;
  }

  private void audit(String event, ConversationId conversationId, UserId targetUserId) {
    auditContext.putCustomAttribute("identityEvent", event);
    auditContext.putCustomAttribute("targetConversationId", conversationId.value().toString());
    if (targetUserId != null) {
      auditContext.putCustomAttribute("targetUserId", targetUserId.value().toString());
    }
  }
}
