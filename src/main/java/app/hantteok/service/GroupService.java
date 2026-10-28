package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Group membership applications and owner approvals. */
@Service
@SuppressWarnings("unchecked")
public class GroupService {
    private final DatabaseRepository db;
    private final PrototypeAuthService auth;

    public GroupService(DatabaseRepository db, PrototypeAuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    Map<String, Object> joinGroup(
            Map<String, Object> state, String actor, Map<String, Object> data, boolean invitation) {
        String id = text(data, "groupId", 80, true);
        var group = find(state, "groups", id);
        require(!member(state, id, actor), "이미 가입한 그룹입니다.");
        require(
                invitation == Boolean.TRUE.equals(group.get("locked")),
                invitation ? "가입 신청을 이용해 주세요." : "초대 코드를 입력해 주세요.");
        if (invitation) {
            require(
                    auth.invitationMatches(text(data, "code", 100, true), str(group, "inviteHash")),
                    "초대 코드가 일치하지 않습니다.");
            members(state, id).add(actor);
            return map("id", id);
        }
        require(
                rows(state, "groupApplications").stream()
                        .noneMatch(
                                application ->
                                        same(application.get("groupId"), id)
                                                && same(application.get("userId"), actor)),
                "이미 신청한 그룹입니다.");
        String applicationId = id("application-");
        rows(state, "groupApplications")
                .add(
                        map(
                                "id",
                                applicationId,
                                "groupId",
                                id,
                                "userId",
                                actor,
                                "userName",
                                auth.profile(actor).get("name"),
                                "introduction",
                                graphemeText(data, "introduction", 100, false),
                                "createdAt",
                                db.now().toString(),
                                "approved",
                                false));
        return map("id", applicationId);
    }

    Map<String, Object> approveGroup(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var application = find(state, "groupApplications", text(data, "applicationId", 80, true));
        if (!actor.equals(find(state, "groups", str(application, "groupId")).get("ownerId"))) {
            throw ApiException.forbidden();
        }
        if (!Boolean.TRUE.equals(application.get("approved"))) {
            members(state, str(application, "groupId")).add(str(application, "userId"));
            application.put("approved", true);
        }
        return map("id", application.get("id"));
    }
}
