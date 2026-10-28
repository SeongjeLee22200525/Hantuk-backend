package app.hantteok.prototype;

import app.hantteok.common.persistence.Database;
import app.hantteok.common.error.ApiException;
import static app.hantteok.common.persistence.Database.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrototypeSeed implements ApplicationRunner {
    private final Database db; private final PasswordEncoder passwords; private final ObjectMapper json; private final boolean demo;
    PrototypeSeed(Database db,PasswordEncoder passwords,ObjectMapper json,@Value("${app.demo-enabled}") boolean demo) { this.db=db;this.passwords=passwords;this.json=json;this.demo=demo; }
    @Override @Transactional public void run(ApplicationArguments args) throws Exception {
        if(db.count("SELECT COUNT(*) FROM prototype_state WHERE id=1")>0) return;
        var state=map("groups",new ArrayList<>(),"members",new LinkedHashMap<>(),"groupApplications",new ArrayList<>(),"stores",new ArrayList<>(),"storeApplications",new ArrayList<>(),"donations",new ArrayList<>(),"offers",new ArrayList<>(),"requests",new ArrayList<>(),"receipts",new ArrayList<>(),"groupFavorites",new LinkedHashMap<>(),"storeFavorites",new LinkedHashMap<>());
        if(demo) {
            user("hantuck01","한나눔","demo@hantuck.example","USER");
            for(int i=1;i<=3;i++) user("leader0"+i,"예시 그룹 "+i+" 그룹장","leader0"+i+"@hantuck.example","USER");
            user("store01","예시 가게 1 사장님","store01@hantuck.example","USER"); user("admin01","한턱 관리자","admin@hantuck.example","ADMIN");
            var groups=(List<Object>)state.get("groups"); var members=(Map<String,Object>)state.get("members");
            String[] cats={"학교·동문","직장·팀","취미·모임"},images={"campus","team","walk"};
            String[][] tags={{"대학생","캠퍼스","선후배"},{"직장인","점심","동료"},{"산책","주말","친목"}};
            String[] descriptions={"캠퍼스에서 작은 응원을 나누는 그룹이에요.","동료들과 따뜻한 한 끼를 나눠요.","함께 걷고 마음을 나누는 초대 모임이에요."};
            for(int i=1;i<=3;i++) {
                groups.add(map("id","group-"+i,"code","HG000"+i,"name","예시 그룹 "+i,"category",cats[i-1],"tags",List.of(tags[i-1]),"description",descriptions[i-1],"ownerId","leader0"+i,"thumbnail","assets/images/group-"+images[i-1]+".png","locked",i==3,"inviteHash",i==3?passwords.encode("123456"):""));
                members.put("group-"+i,new ArrayList<>(List.of("leader0"+i)));
            }
            ((List<Object>)state.get("stores")).add(map("id","example-store-1","name","예시 가게 1","ownerId","store01","category","한식","registrationNumber","0000000000","phoneNumber","0200000000","address","서울특별시 마포구 나눔로 12, 1층 (예시 주소)","description","정성껏 차린 한 끼로 따뜻한 마음을 나눠요.","tags",List.of("한식","든든한 한 끼","함께하는 식사"),"thumbnail","assets/images/group-team.png","menu",List.of(menu("따뜻한 집밥 정식","밥과 국, 매일 준비하는 반찬을 함께 담았어요.",9000,true),menu("제육볶음 한 상","매콤한 제육볶음에 밥과 반찬을 곁들였어요.",10000,false),menu("채소 비빔밥","다채로운 채소를 담은 가벼운 한 끼예요.",8000,false))));
            ((List<Object>)state.get("offers")).add(map("id","example-store-1","storeId","example-store-1","groupId","group-3","storeName","예시 가게 1","totalAmount",100000,"usedAmount",35000,"startsOn","2026-10-28","expiresOn","2026-11-27","donorId","hantuck01","donorName","한나눔"));
        }
        db.exec("INSERT INTO prototype_state(id,revision,document) VALUES(1,0,?)",json.writeValueAsString(state));
    }
    private Map<String,Object> menu(String n,String d,int p,boolean r) { return map("name",n,"description",d,"price",p,"recommended",r); }
    private void user(String id,String name,String email,String role) { db.exec("INSERT INTO prototype_accounts(id,password_hash,name,birth_date,email,phone_number,role,created_at) VALUES(?,?,?,?,?,?,?,?)",id,passwords.encode("welcome123!"),name,java.sql.Date.valueOf("2001-03-15"),email,"01000000000",role,db.time()); }
}
