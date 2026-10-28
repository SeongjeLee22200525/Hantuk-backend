package app.hantteok.service;

import app.hantteok.dto.Requests;
import app.hantteok.repository.Database;
import app.hantteok.common.error.ApiException;
import static app.hantteok.repository.Database.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitService {
    final Database db;
    private final String paymentMode;
    BenefitService(Database db,@Value("${app.payment-mode}") String paymentMode) { this.db=db; this.paymentMode=paymentMode; }
    String state(Map<String,Object> b) {
        if(!"active".equals(str(b,"status"))) return str(b,"status");
        if(num(b,"remaining")<=0) return "exhausted";
        return !instant(b,"expires_at").isAfter(db.now())?"expired":"active";
    }
    long used(String user,String benefit) {
        return db.count("SELECT COALESCE(SUM(applied),0) FROM usages WHERE user_id=? AND benefit_id=?",user,benefit);
    }
    long allowance(String user,Map<String,Object> b) {
        if(!state(b).equals("active") || db.count("SELECT COUNT(*) FROM memberships WHERE user_id=? AND community_id=?",user,b.get("community_id"))==0) return 0;
        return Math.max(0,Math.min(num(b,"remaining"),num(b,"person_limit")-used(user,str(b,"id"))));
    }
    Map<String,Object> view(String user,Map<String,Object> b) {
        return map("id",b.get("id"),"shopId",b.get("shop_id"),"communityId",b.get("community_id"),"donor",b.get("donor"),"message",b.get("message"),
            "total",b.get("total"),"remaining",b.get("remaining"),"limit",b.get("person_limit"),"createdAt",instant(b,"created_at"),"expiresAt",instant(b,"expires_at"),
            "status",b.get("status"),"state",state(b),"mine",user.equals(str(b,"donor_id")),"allowance",allowance(user,b),"usedByMe",used(user,str(b,"id")),
            "menus",db.all("SELECT name FROM benefit_menus WHERE benefit_id=? ORDER BY name",b.get("id")).stream().map(x->x.get("name")).toList(),"paymentMode",paymentMode);
    }
    public List<Map<String,Object>> list(String user,String community,String shop,boolean mine,String state) {
        return db.all("SELECT * FROM benefits ORDER BY created_at DESC,id").stream()
            .filter(b->community==null || community.equals(str(b,"community_id"))).filter(b->shop==null || shop.equals(str(b,"shop_id")))
            .filter(b->!mine || user.equals(str(b,"donor_id"))).filter(b->state==null || state.equals(state(b))).map(b->view(user,b)).toList();
    }
    public Map<String,Object> detail(String user,String id) { return view(user,db.one("SELECT * FROM benefits WHERE id=?",id)); }
    @Transactional
    public Map<String,Object> pay(String user,Requests.Payment r) {
        db.lockUser(user);
        var existing=db.all("SELECT * FROM benefits WHERE donor_id=? AND payment_key=?",user,r.paymentKey());
        if(!existing.isEmpty()) {
            var b=existing.get(0);
            var menus=db.all("SELECT name FROM benefit_menus WHERE benefit_id=?",b.get("id")).stream().map(x->str(x,"name")).collect(java.util.stream.Collectors.toSet());
            if(!r.shopId().equals(str(b,"shop_id")) || !r.communityId().equals(str(b,"community_id")) || r.amount()!=num(b,"total") || r.limit()!=num(b,"person_limit")
                || !r.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).equals(instant(b,"expires_at")) || !r.donor().trim().equals(str(b,"donor")) || !r.message().trim().equals(str(b,"message")) || !menus.equals(new HashSet<>(r.menus())))
                throw new ApiException(409,"IDEMPOTENCY_CONFLICT","같은 paymentKey로 다른 결제 내용을 보낼 수 없습니다.");
            return view(user,b);
        }
        if(!paymentMode.equals("mock")) throw new ApiException(501,"PAYMENT_PROVIDER_REQUIRED","결제 제공자를 연결해야 합니다.");
        if(r.simulateFailure()) throw new ApiException(422,"PAYMENT_FAILED","모의 결제에 실패했습니다. 선결제는 생성되지 않았습니다.");
        if(r.limit()>r.amount() || !r.expiresAt().isAfter(db.now()) || r.donor().trim().isEmpty()) throw ApiException.bad("금액, 이름과 이용 기간을 확인해 주세요.");
        db.one("SELECT id FROM communities WHERE id=?",r.communityId());
        db.one("SELECT id FROM shops WHERE id=? FOR UPDATE",r.shopId());
        if(new HashSet<>(r.menus()).size()!=r.menus().size()) throw ApiException.bad("메뉴가 중복되었습니다.");
        for(String menu:r.menus()) if(db.count("SELECT COUNT(*) FROM shop_menus WHERE shop_id=? AND name=?",r.shopId(),menu)==0) throw ApiException.bad("가게에서 제공하는 메뉴를 선택해 주세요.");
        String id=id("B");
        db.exec("INSERT INTO benefits(id,donor_id,shop_id,community_id,donor,message,total,remaining,person_limit,created_at,expires_at,payment_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id,user,r.shopId(),r.communityId(),r.donor().trim(),r.message().trim(),r.amount(),r.amount(),r.limit(),db.time(),at(r.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)),r.paymentKey());
        for(String menu:r.menus()) db.exec("INSERT INTO benefit_menus(benefit_id,name) VALUES(?,?)",id,menu);
        db.notice(user,"한턱이 도착했어요!",r.amount()+"원의 마음을 전했어요.",id,"usage");
        return detail(user,id);
    }
    Map<String,Object> usageView(Map<String,Object> u) {
        return map("id",u.get("id"),"requestId",u.get("request_id"),"benefitId",u.get("benefit_id"),"shopId",u.get("shop_id"),"menu",u.get("menu"),
            "orderAmount",u.get("order_amount"),"applied",u.get("applied"),"extra",num(u,"order_amount")-num(u,"applied"),"date",instant(u,"used_at"),
            "settled",u.get("settled_at")!=null,"settledAt",u.get("settled_at")==null?null:instant(u,"settled_at"));
    }
    public List<Map<String,Object>> usages(String user) {
        return db.all("SELECT * FROM usages WHERE user_id=? ORDER BY used_at DESC,id",user).stream().map(this::usageView).toList();
    }
    public Map<String,Object> usage(String user,String id) {
        return usageView(db.one("SELECT * FROM usages WHERE id=? AND user_id=?",id,user));
    }
    public List<Map<String,Object>> donorUsages(String user,String benefit) {
        var b=db.one("SELECT donor_id FROM benefits WHERE id=?",benefit);
        if(!user.equals(str(b,"donor_id"))) throw ApiException.forbidden();
        // Never expose recipient IDs or verification emails to the donor.
        return db.all("SELECT * FROM usages WHERE benefit_id=? ORDER BY used_at DESC,id",benefit).stream().map(this::usageView).toList();
    }
}
