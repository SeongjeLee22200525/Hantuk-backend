package app.hantteok.service;

import app.hantteok.dto.Requests;
import app.hantteok.repository.DatabaseRepository;
import app.hantteok.common.error.ApiException;
import static app.hantteok.repository.DatabaseRepository.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final DatabaseRepository db;
    private final PasswordEncoder passwords;
    private final boolean demo;
    CatalogService(DatabaseRepository db,PasswordEncoder passwords,@Value("${app.demo-enabled}") boolean demo) { this.db=db; this.passwords=passwords; this.demo=demo; }
    public List<Map<String,Object>> communities(String user) {
        return db.all("SELECT c.*, CASE WHEN m.user_id IS NULL THEN FALSE ELSE TRUE END AS verified FROM communities c LEFT JOIN memberships m ON m.community_id=c.id AND m.user_id=? ORDER BY c.id",user).stream()
            .map(c->map("id",c.get("id"),"name",c.get("name"),"description",c.get("description"),"emailDomain",c.get("email_domain"),"verified",c.get("verified"))).toList();
    }
    @Transactional
    public Map<String,Object> verify(String user,String community,Requests.Verification r) {
        db.lockUser(user);
        var c=db.one("SELECT * FROM communities WHERE id=?",community);
        String domain=str(c,"email_domain");
        if(!domain.isEmpty()) {
            if(!demo) throw new ApiException(501,"EMAIL_PROVIDER_REQUIRED","실제 이메일 인증 제공자를 연결해야 합니다.");
            if(r.email()==null || !r.email().trim().toLowerCase(Locale.ROOT).matches("[^\\s@]+@"+java.util.regex.Pattern.quote(domain)) || !"123456".equals(r.code()))
                throw ApiException.bad("학교 이메일과 인증번호를 확인해 주세요.");
        } else if(!passwords.matches(r.code(),str(c,"invite_hash"))) throw ApiException.bad("초대 코드를 확인해 주세요.");
        if(db.count("SELECT COUNT(*) FROM memberships WHERE user_id=? AND community_id=?",user,community)==0)
            db.exec("INSERT INTO memberships(user_id,community_id,verified_at) VALUES(?,?,?)",user,community,db.time());
        return map("communityId",community,"verified",true,"demo",demo);
    }
    public List<Map<String,Object>> shops(String query,String category) {
        return db.all("SELECT * FROM shops ORDER BY id").stream().filter(s->query==null || str(s,"name").contains(query) || str(s,"address").contains(query))
            .filter(s->category==null || str(s,"category").equals(category)).map(this::shopView).toList();
    }
    public Map<String,Object> shop(String id) { return shopView(db.one("SELECT * FROM shops WHERE id=?",id)); }
    private Map<String,Object> shopView(Map<String,Object> s) {
        return map("id",s.get("id"),"name",s.get("name"),"category",s.get("category"),"emoji",s.get("emoji"),"address",s.get("address"),"distance",s.get("distance"),
            "description",s.get("description"),"hours",s.get("hours"),"color",s.get("color"),"menu",db.all("SELECT name,price FROM shop_menus WHERE shop_id=? ORDER BY name",s.get("id")));
    }
    @Transactional
    public Map<String,Object> edit(String user,String id,Requests.ShopEdit r) {
        db.ownShop(user,id);
        db.one("SELECT id FROM shops WHERE id=? FOR UPDATE",id);
        var names=r.menu().stream().map(m->m.name().trim()).toList();
        if(names.stream().anyMatch(String::isEmpty) || new HashSet<>(names).size()!=names.size()) throw ApiException.bad("메뉴 이름은 비어 있거나 중복될 수 없습니다.");
        db.exec("UPDATE shops SET description=?,hours=? WHERE id=?",r.description().trim(),r.hours().trim(),id);
        db.exec("DELETE FROM shop_menus WHERE shop_id=?",id);
        for(var m:r.menu()) db.exec("INSERT INTO shop_menus(shop_id,name,price) VALUES(?,?,?)",id,m.name().trim(),m.price());
        return shop(id);
    }
}
