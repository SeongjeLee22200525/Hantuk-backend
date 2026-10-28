package app.hantteok.legacy;

import app.hantteok.common.persistence.Database;
import app.hantteok.common.error.ApiException;
import static app.hantteok.common.persistence.Database.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportService {
    private final Database db;
    private final AuthService auth;
    SupportService(Database db,AuthService auth) { this.db=db; this.auth=auth; }
    private Map<String,Object> view(Map<String,Object> t) {
        return map("id",t.get("id"),"targetId",t.get("target_id"),"type",t.get("type"),"reason",t.get("reason"),"evidence",t.get("evidence"),
            "status",t.get("status"),"date",instant(t,"created_at"),"refundAmount",t.get("refund_amount"));
    }
    public List<Map<String,Object>> list(String user) { return db.all("SELECT * FROM support_tickets WHERE user_id=? ORDER BY created_at DESC,id",user).stream().map(this::view).toList(); }
    public Map<String,Object> get(String user,String id) { return view(db.one("SELECT * FROM support_tickets WHERE id=? AND user_id=?",id,user)); }
    @Transactional
    public Map<String,Object> create(String user,Requests.Ticket r) {
        if(!Set.of("취소·환불","문제 신고","정산 문의").contains(r.type()) || r.reason().trim().length()<5) throw ApiException.bad("신청 유형과 사유를 확인해 주세요.");
        db.lockUser(user);
        if(r.type().equals("취소·환불")) {
            var b=db.lockBenefit(r.targetId());
            if(!user.equals(str(b,"donor_id"))) throw ApiException.forbidden();
            if(num(b,"remaining")<=0 || !"active".equals(str(b,"status"))) throw ApiException.conflict("남은 잔액이 있는 본인의 선결제만 환불할 수 있습니다.");
            db.exec("UPDATE benefits SET status='refund' WHERE id=?",r.targetId());
            // Approval rechecks benefit state under the same benefit lock.
        } else if(r.type().equals("정산 문의")) {
            db.ownShop(user,r.targetId());
        } else if(db.count("SELECT COUNT(*) FROM benefits WHERE id=?",r.targetId())==0
            && db.count("SELECT COUNT(*) FROM usages WHERE id=? AND user_id=?",r.targetId(),user)==0
            && db.count("SELECT COUNT(*) FROM shops WHERE id=?",r.targetId())==0) throw ApiException.missing();
        String id=id("CS");
        db.exec("INSERT INTO support_tickets(id,user_id,target_id,type,reason,evidence,created_at) VALUES(?,?,?,?,?,?,?)",
            id,user,r.targetId(),r.type(),r.reason().trim(),r.evidence().trim(),db.time());
        db.notice(user,r.type()+" 신청이 접수되었어요","마이페이지에서 처리 상태를 확인하세요.",id,"refund");
        return get(user,id);
    }
    public List<Map<String,Object>> adminList(String admin) {
        auth.admin(admin);
        return db.all("SELECT * FROM support_tickets ORDER BY created_at DESC,id").stream().map(this::view).toList();
    }
    @Transactional
    public Map<String,Object> advance(String admin,String id) {
        auth.admin(admin);
        var initial=db.one("SELECT * FROM support_tickets WHERE id=?",id);
        db.lockUser(str(initial,"user_id"));
        Map<String,Object> b=null;
        if("취소·환불".equals(str(initial,"type"))) b=db.lockBenefit(str(initial,"target_id"));
        var t=db.one("SELECT * FROM support_tickets WHERE id=? FOR UPDATE",id);
        if("처리 완료".equals(str(t,"status"))) return view(t);
        String next="접수".equals(str(t,"status"))?"검토 중":"처리 완료";
        long refund=0;
        if(next.equals("처리 완료") && b!=null) {
            if(!"refund".equals(str(b,"status"))) throw ApiException.conflict("환불 검토 상태가 아닙니다.");
            refund=num(b,"remaining");
            // Keep remaining as the refund audit amount, matching the Flutter prototype.
            db.exec("UPDATE benefits SET status='cancelled' WHERE id=?",b.get("id"));
            db.notice(str(t,"user_id"),"환불 처리가 완료되었어요","모의 환불 금액 "+refund+"원",str(b,"id"),"refund");
        }
        db.exec("UPDATE support_tickets SET status=?,refund_amount=? WHERE id=?",next,refund,id);
        return view(db.one("SELECT * FROM support_tickets WHERE id=?",id));
    }
}
