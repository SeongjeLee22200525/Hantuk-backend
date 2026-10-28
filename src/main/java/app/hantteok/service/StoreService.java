package app.hantteok.service;

import static app.hantteok.common.state.StateData.*;
import static app.hantteok.common.validation.RequestValues.*;
import static app.hantteok.repository.DatabaseRepository.*;

import app.hantteok.common.error.ApiException;
import app.hantteok.repository.DatabaseRepository;

import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Store applications, administrator reviews, thumbnails, and menus. */
@Service
@SuppressWarnings("unchecked")
public class StoreService {
    private final DatabaseRepository db;
    private final PrototypeAuthService auth;

    public StoreService(DatabaseRepository db, PrototypeAuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    Map<String, Object> applyStore(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        String previousId = text(data, "resubmissionOf", 80, false);
        Map<String, Object> previousApplication = null;
        if (!previousId.isEmpty()) {
            previousApplication = find(state, "storeApplications", previousId);
            if (!actor.equals(previousApplication.get("ownerId"))) {
                throw ApiException.forbidden();
            }
            require(
                    "rejected".equals(previousApplication.get("status"))
                            && previousApplication.get("resubmissionId") == null,
                    "반려된 최신 신청만 재신청할 수 있습니다.");
        }
        String name = text(data, "name", 50, true),
                address = text(data, "address", 150, true),
                category = text(data, "category", 30, true),
                description = text(data, "description", 100, false);
        String registration = text(data, "registrationNumber", 20, true).replace("-", ""),
                phone = text(data, "phoneNumber", 20, true).replace("-", "");
        if (!registration.matches("[0-9]{10}")
                || !phone.matches("0[0-9]{8,10}")
                || !List.of("한식", "중식", "일식", "양식", "분식", "카페·디저트", "기타").contains(category)) {
            throw ApiException.bad("사업자등록번호·전화번호·업종을 확인해 주세요.");
        }
        String rootApplicationId =
                previousApplication == null ? null : str(previousApplication, "rootApplicationId");
        require(
                rows(state, "stores").stream()
                        .noneMatch(
                                existing ->
                                        same(existing.get("registrationNumber"), registration)
                                                && normalized(str(existing, "address"))
                                                        .equals(normalized(address))),
                "이미 등록된 가게입니다.");
        require(
                rows(state, "storeApplications").stream()
                        .noneMatch(
                                existing ->
                                        !same(existing.get("rootApplicationId"), rootApplicationId)
                                                && same(
                                                        existing.get("registrationNumber"),
                                                        registration)
                                                && normalized(str(existing, "address"))
                                                        .equals(normalized(address))),
                "이미 신청한 가게입니다.");
        String id = id("store-application-");
        rows(state, "storeApplications")
                .add(
                        map(
                                "id",
                                id,
                                "ownerId",
                                actor,
                                "ownerName",
                                auth.profile(actor).get("name"),
                                "name",
                                name,
                                "registrationNumber",
                                registration,
                                "address",
                                address,
                                "category",
                                category,
                                "phoneNumber",
                                phone,
                                "description",
                                description,
                                "createdAt",
                                db.now().toString(),
                                "rootApplicationId",
                                rootApplicationId == null ? id : rootApplicationId,
                                "previousApplicationId",
                                previousApplication == null ? null : previousApplication.get("id"),
                                "submissionNumber",
                                previousApplication == null
                                        ? 1
                                        : number(previousApplication, "submissionNumber") + 1,
                                "storeId",
                                null,
                                "status",
                                "pending",
                                "rejectionReason",
                                null,
                                "reviewedAt",
                                null,
                                "resubmissionId",
                                null));
        if (previousApplication != null) {
            previousApplication.put("resubmissionId", id);
        }
        return map("id", id);
    }

    Map<String, Object> reviewApplication(
            Map<String, Object> state, String actor, Map<String, Object> data, boolean approve) {
        if (!auth.admin(actor)) {
            throw ApiException.forbidden();
        }
        var application = find(state, "storeApplications", text(data, "applicationId", 80, true));
        if (approve && "approved".equals(application.get("status"))) {
            return map("id", application.get("storeId"));
        }
        require("pending".equals(application.get("status")), "심사 대기 신청만 처리할 수 있습니다.");
        application.put("reviewedAt", db.now().toString());
        if (!approve) {
            application.put("rejectionReason", graphemeText(data, "reason", 300, true));
            application.put("status", "rejected");
            return map("id", application.get("id"));
        }
        String id = "registered-" + application.get("id");
        rows(state, "stores")
                .add(
                        map(
                                "id",
                                id,
                                "ownerId",
                                application.get("ownerId"),
                                "name",
                                application.get("name"),
                                "address",
                                application.get("address"),
                                "description",
                                str(application, "description").isEmpty()
                                        ? application.get("name") + "에서 따뜻한 한 끼를 나눠요."
                                        : application.get("description"),
                                "category",
                                application.get("category"),
                                "registrationNumber",
                                application.get("registrationNumber"),
                                "phoneNumber",
                                application.get("phoneNumber"),
                                "tags",
                                List.of(application.get("category")),
                                "thumbnail",
                                "assets/images/group-team.png",
                                "menu",
                                new ArrayList<>()));
        application.put("storeId", id);
        application.put("status", "approved");
        return map("id", id);
    }

    Map<String, Object> updateThumbnail(
            Map<String, Object> state, String actor, Map<String, Object> data) {
        var store = owned(state, actor, data);
        String image = text(data, "thumbnail", 100, true);
        require(
                List.of(
                                "assets/images/group-team.png",
                                "assets/images/group-campus.png",
                                "assets/images/group-walk.png")
                        .contains(image),
                "사용할 사진을 선택해 주세요.");
        store.put("thumbnail", image);
        return map("id", store.get("id"));
    }

    Map<String, Object> updateMenu(
            Map<String, Object> state, String actor, Map<String, Object> data, boolean remove) {
        var store = owned(state, actor, data);
        var menus = (List<Map<String, Object>>) store.get("menu");
        require(
                data.containsKey("expectedMenu") && same(data.get("expectedMenu"), menus),
                "메뉴가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        Object supplied = data.get("index");
        if (supplied != null
                && (!(supplied instanceof Integer || supplied instanceof Long)
                        || ((Number) supplied).longValue() < -1
                        || ((Number) supplied).longValue() > 100)) {
            throw ApiException.bad("메뉴 순서를 확인해 주세요.");
        }
        int index = supplied == null ? -1 : ((Number) supplied).intValue();
        if (remove) {
            require(index >= 0 && index < menus.size(), "메뉴를 다시 확인해 주세요.");
            menus.remove(index);
        } else {
            require(index >= -1 && index < menus.size(), "메뉴를 다시 확인해 주세요.");
            var item = object(data, "item");
            String name = text(item, "name", 50, true),
                    description = text(item, "description", 150, false);
            long price = amount(item, "price");
            for (int i = 0; i < menus.size(); i++) {
                if (i != index && normalized(str(menus.get(i), "name")).equals(normalized(name))) {
                    throw ApiException.conflict("이미 등록된 메뉴 이름입니다.");
                }
            }
            var saved =
                    map(
                            "name",
                            name,
                            "description",
                            description,
                            "price",
                            price,
                            "recommended",
                            Boolean.TRUE.equals(item.get("recommended")));
            if (index < 0) {
                menus.add(saved);
            } else {
                menus.set(index, saved);
            }
        }
        return map("id", store.get("id"));
    }
}
