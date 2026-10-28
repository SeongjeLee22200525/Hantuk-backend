package app.hantteok.bootstrap;

import app.hantteok.repository.DatabaseRepository;
import app.hantteok.common.error.ApiException;
import static app.hantteok.repository.DatabaseRepository.*;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo credentials and invitations are installed only in the local profile. */
@Component
public class DemoData implements ApplicationRunner {
    private final DatabaseRepository db;
    private final PasswordEncoder passwords;
    private final boolean enabled;
    DemoData(DatabaseRepository db,PasswordEncoder passwords,@Value("${app.demo-enabled}") boolean enabled) { this.db=db; this.passwords=passwords; this.enabled=enabled; }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(!enabled || db.count("SELECT COUNT(*) FROM app_users WHERE id='demo-user'")>0) return;
        user("demo-user","demo@hantteok.app","demo1234","다정한 이웃","USER");
        user("demo-merchant","merchant@hantteok.app","merchant1234","온기 가게","USER");
        user("demo-admin","admin@hantteok.app","admin1234","데모 운영자","ADMIN");
        user("demo-donor","donor@hantteok.app","donor1234","졸업생 선배","USER");
        group("school","한마음대학교","배움과 응원이 모이는 우리 학교","hanmaeum.ac.kr","CAMPUS2026");
        group("volunteer","온기 봉사단","작은 실천으로 따뜻한 동네를 만드는 사람들","","WARM2026");
        group("village","성북 청년마을","서로의 일상을 응원하는 동네 공동체","","YOUTH2026");
        db.exec("INSERT INTO memberships(user_id,community_id,verified_at) VALUES('demo-user','school',?)",db.time());
        shop("s1","온기 식탁","한식","🍚","서울 성북구 보문로 34길 12","도보 3분","갓 지은 밥 한 그릇에 정성을 담습니다.","매일 11:00 – 20:30 · 일요일 휴무",0xFFF2DEBCL);
        menu("s1","따뜻한 제육 덮밥",8000); menu("s1","정성 가득 비빔밥",7000); menu("s1","오늘의 김치찌개",8500);
        shop("s2","모퉁이 커피","카페","☕","서울 성북구 동소문로 22길 8","도보 5분","향긋한 커피와 작은 응원을 건넵니다.","매일 09:00 – 22:00",0xFFDCE8DBL);
        menu("s2","아메리카노",4500); menu("s2","바닐라 라테",5500); menu("s2","딸기 크림 케이크",6000);
        shop("s3","소소 베이커리","베이커리","🥐","서울 성북구 보문로 30길 21","도보 7분","매일 아침 구워내는 작은 행복.","매일 08:00 – 20:00 · 월요일 휴무",0xFFF6DDCDL);
        menu("s3","버터 크루아상",4000); menu("s3","소금빵",3000); menu("s3","햄 치즈 샌드위치",6500);
        shop("s4","초록 한 그릇","샐러드","🥗","서울 성북구 동소문로 18길 6","도보 9분","신선한 재료로 채우는 기분 좋은 한 끼.","평일 10:00 – 20:00 · 주말 11:00 – 18:00",0xFFE2EAC8L);
        menu("s4","치킨 그린볼",9000); menu("s4","두부 포케",8500);
        benefit("b1","demo-donor","s1","school","졸업생 선배","밥은 꼭 챙겨 먹어요. 오늘 한 끼는 제가 쏠게요!",200000,145000,5000,27);
        benefit("b2","demo-donor","s2","school","익명의 이웃","커피 한 잔의 여유를.",100000,68000,4500,12);
        db.exec("INSERT INTO benefit_menus(benefit_id,name) VALUES('b2','아메리카노'),('b2','바닐라 라테')");
        benefit("b3","demo-user","s3","school","다정한 이웃","따뜻한 하루 보내세요.",50000,35000,4000,20);
        benefit("b4","demo-donor","s4","volunteer","동네 주민","여러분을 응원해요.",150000,120000,7000,15);
        db.notice("demo-user","우리 학교에 따뜻한 한턱이 도착했어요","온기 식탁에서 든든한 한 끼를 만나보세요.","b1","usage");
    }
    private void user(String id,String email,String password,String nickname,String role) {
        db.exec("INSERT INTO app_users(id,email,password_hash,nickname,role,created_at) VALUES(?,?,?,?,?,?)",id,email,passwords.encode(password),nickname,role,db.time());
    }
    private void group(String id,String name,String description,String domain,String code) {
        db.exec("INSERT INTO communities(id,name,description,email_domain,invite_hash) VALUES(?,?,?,?,?)",id,name,description,domain,passwords.encode(code));
    }
    private void shop(String id,String name,String category,String emoji,String address,String distance,String description,String hours,long color) {
        db.exec("INSERT INTO shops(id,owner_id,name,category,emoji,address,distance,description,hours,color) VALUES(?,'demo-merchant',?,?,?,?,?,?,?,?)",id,name,category,emoji,address,distance,description,hours,color);
    }
    private void menu(String shop,String name,long price) { db.exec("INSERT INTO shop_menus(shop_id,name,price) VALUES(?,?,?)",shop,name,price); }
    private void benefit(String id,String donorId,String shop,String community,String donor,String message,long total,long remaining,long limit,int days) {
        db.exec("INSERT INTO benefits(id,donor_id,shop_id,community_id,donor,message,total,remaining,person_limit,created_at,expires_at,payment_key) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",id,donorId,shop,community,donor,message,total,remaining,limit,at(db.now().minus(1,ChronoUnit.DAYS)),at(db.now().plus(days,ChronoUnit.DAYS)),"seed-"+id);
    }
}
