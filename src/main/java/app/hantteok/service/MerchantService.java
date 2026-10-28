package app.hantteok.service;

import app.hantteok.repository.DatabaseRepository;
import app.hantteok.common.error.ApiException;
import static app.hantteok.repository.DatabaseRepository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {
    private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
    private final DatabaseRepository db;
    private final BenefitService benefits;
    MerchantService(DatabaseRepository db,BenefitService benefits) { this.db=db; this.benefits=benefits; }
    public Map<String,Object> dashboard(String user,String shop) {
        db.ownShop(user,shop);
        var transactions=transactions(user,shop);
        long remaining=db.count("SELECT COALESCE(SUM(remaining),0) FROM benefits WHERE shop_id=? AND status='active' AND expires_at>?",shop,db.time());
        return map("shopId",shop,"availableBalance",remaining,"usedAmount",transactions.stream().mapToLong(t->num(t,"applied")).sum(),
            "unsettledAmount",transactions.stream().filter(t->!Boolean.TRUE.equals(t.get("settled"))).mapToLong(t->num(t,"applied")).sum(),"usageCount",transactions.size());
    }
    public List<Map<String,Object>> transactions(String user,String shop) {
        db.ownShop(user,shop);
        return db.all("SELECT * FROM usages WHERE shop_id=? ORDER BY used_at DESC,id",shop).stream().map(benefits::usageView).toList();
    }
    public List<Map<String,Object>> settlements(String user,String shop) {
        db.ownShop(user,shop);
        Map<LocalDate,List<Map<String,Object>>> days=new TreeMap<>(Comparator.reverseOrder());
        for(var u:db.all("SELECT * FROM usages WHERE shop_id=? ORDER BY used_at DESC,id",shop)) {
            var day=instant(u,"used_at").atZone(SEOUL).toLocalDate();
            days.computeIfAbsent(day,k->new ArrayList<>()).add(u);
        }
        return days.entrySet().stream().map(e->summary(e.getKey(),e.getValue())).toList();
    }
    private Map<String,Object> summary(LocalDate day,List<Map<String,Object>> rows) {
        return map("day",day,"total",rows.stream().mapToLong(u->num(u,"applied")).sum(),
            "settledAmount",rows.stream().filter(u->u.get("settled_at")!=null).mapToLong(u->num(u,"applied")).sum(),
            "complete",!rows.isEmpty() && rows.stream().allMatch(u->u.get("settled_at")!=null),"usages",rows.stream().map(benefits::usageView).toList(),"paymentMode","mock");
    }
    private List<Map<String,Object>> dayRows(String shop,LocalDate day) {
        return db.all("SELECT * FROM usages WHERE shop_id=? AND used_at>=? AND used_at<? ORDER BY used_at,id",shop,
            at(day.atStartOfDay(SEOUL).toInstant()),at(day.plusDays(1).atStartOfDay(SEOUL).toInstant()));
    }
    public Map<String,Object> settlement(String user,String shop,LocalDate day) { db.ownShop(user,shop); return summary(day,dayRows(shop,day)); }
    @Transactional
    public Map<String,Object> settle(String user,String shop,LocalDate day) {
        db.ownShop(user,shop);
        db.one("SELECT id FROM shops WHERE id=? FOR UPDATE",shop);
        var rows=dayRows(shop,day);
        if(rows.isEmpty()) throw ApiException.conflict("정산할 사용 완료 거래가 없습니다.");
        // Mark exactly the selected rows; a concurrent new usage remains unsettled.
        for(var u:rows) if(u.get("settled_at")==null) db.exec("UPDATE usages SET settled_at=? WHERE id=? AND settled_at IS NULL",db.time(),u.get("id"));
        return summary(day,dayRows(shop,day));
    }
}
