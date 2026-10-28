package app.hantteok;

import static app.hantteok.Database.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:prototype-v2-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.demo-enabled=true"})
@AutoConfigureMockMvc
@Import(ServerIntegrationTest.TimeConfig.class)
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
@SuppressWarnings("unchecked")
class PrototypeIntegrationTest {
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired Database db; @Autowired PrototypeSeed seed; @Autowired PrototypeAuth auth; @Autowired PrototypeService service;
    @Autowired ServerIntegrationTest.TestClock clock; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @BeforeEach void reset() throws Exception {
        clock.time=Instant.parse("2026-10-28T04:00:00Z");
        for(String t:List.of("prototype_commands","prototype_sessions","prototype_accounts","prototype_email_challenges","prototype_state"))db.exec("DELETE FROM "+t);
        seed.run(new DefaultApplicationArguments());
    }
    Map<String,Object> command(String actor,String action,Map<String,Object> data){return (Map<String,Object>)service.command(actor,UUID.randomUUID().toString(),action,data).get("result");}
    List<Map<String,Object>> rows(String actor,String field){return (List<Map<String,Object>>)service.snapshot(actor).get(field);}
    Map<String,Object> byId(String actor,String field,String id){return rows(actor,field).stream().filter(x->id.equals(x.get("id"))).findFirst().orElseThrow();}
    String donate(String actor,long amount){return str(command(actor,"donation.complete",map("checkoutId",UUID.randomUUID().toString(),"type","prepaid","groupId","group-3","storeId","example-store-1","amount",amount,"expiresOn","2026-10-28")),"id");}
    String request(String actor,String offer){return str(command(actor,"usage.begin",map("offerId",offer)),"id");}
    void amount(String request,long value){command("store01","usage.amount",map("requestId",request,"storeId","example-store-1","amount",value));}
    String approve(String actor,String request){return str(command(actor,"usage.approve",map("requestId",request)),"id");}
    String register(String id) {
        var c=auth.challenge(map("email",id+"@example.test"));var proof=auth.verify(map("challengeId",c.get("challengeId"),"code","123456"));
        return str(auth.register(map("id",id,"password","password123!","name","새 회원","birthDate","2001-03-15","phoneNumber","01012345678","email",id+"@example.test","verificationToken",proof.get("verificationToken"))),"id");
    }
    @Test void accountAndEmailProofAreServerValidated() {
        String id=register("newuser01");assertThat(id).isEqualTo("newuser01");
        var login=auth.login(map("id","NEWUSER01","password","password123!"));assertThat(login).containsKey("accessToken");
        assertThat(auth.available("newuser01")).isFalse();
        assertThat(db.one("SELECT password_hash FROM prototype_accounts WHERE id=?",id).get("password_hash")).isNotEqualTo("password123!");
        assertThatThrownBy(()->auth.login(map("id",id,"password","wrong"))).isInstanceOf(ApiException.class);
        String token=(String)login.get("accessToken");assertThat(auth.user("Bearer "+token)).isEqualTo(id);
        assertThat(db.count("SELECT COUNT(*) FROM prototype_sessions WHERE token_hash=?",token)).isZero();
        auth.logout("Bearer "+token);assertThatThrownBy(()->auth.user("Bearer "+token)).isInstanceOf(ApiException.class);
    }
    @Test void challengeAttemptsExpiryAndBindingCannotBeBypassed() {
        var c=auth.challenge(map("email","test@example.test"));
        for(int i=0;i<5;i++)assertThat(auth.verify(map("challengeId",c.get("challengeId"),"code","wrong")).get("verified")).isEqualTo(false);
        assertThat(auth.verify(map("challengeId",c.get("challengeId"),"code","123456")).get("verified")).isEqualTo(false);
        var good=auth.challenge(map("email","other@example.test"));auth.verify(map("challengeId",good.get("challengeId"),"code","123456"));
        assertThatThrownBy(()->auth.register(map("id","wrongmail","password","password123!","name","새 회원","birthDate","2001-01-01","phoneNumber","01012345678","email","test@example.test","verificationToken",good.get("challengeId")))).isInstanceOf(ApiException.class);
        clock.time=clock.time.plus(Duration.ofMinutes(10));
        assertThat(auth.verify(map("challengeId",good.get("challengeId"),"code","123456")).get("verified")).isEqualTo(false);
        var production=new PrototypeAuth(db,passwords,false);
        assertThatThrownBy(()->production.challenge(map("email","test@example.test"))).isInstanceOf(ApiException.class);
    }
    @Test void groupApplicationsInvitationAndFavoritesEnforceOwnership() {
        var app=command("hantuck01","group.join",map("groupId","group-1","introduction","반가워요","userId","admin01"));
        assertThat(rows("hantuck01","groupApplications").get(0).get("userId")).isEqualTo("hantuck01");
        assertThatThrownBy(()->command("leader02","group.approve",map("applicationId",app.get("id"),"ownerId","leader01"))).isInstanceOf(ApiException.class);
        command("leader01","group.approve",map("applicationId",app.get("id")));
        assertThat((List<String>)((Map<String,Object>)service.snapshot("hantuck01").get("members")).get("group-1")).contains("hantuck01");
        assertThatThrownBy(()->command("hantuck01","group.invite",map("groupId","group-3","code","wrong"))).isInstanceOf(ApiException.class);
        command("hantuck01","group.invite",map("groupId","group-3","code","123456"));
        String command=UUID.randomUUID().toString();
        service.command("hantuck01",command,"group.favorite",map("groupId","group-1"));service.command("hantuck01",command,"group.favorite",map("groupId","group-1"));
        assertThat((List<String>)service.snapshot("hantuck01").get("groupFavorites")).containsExactly("group-1");
        assertThatThrownBy(()->service.command("hantuck01",command,"group.favorite",map("groupId","group-2"))).isInstanceOf(ApiException.class);
        assertThat((List<String>)service.snapshot("leader01").get("groupFavorites")).isEmpty();
    }
    @Test void userPerceivedCharactersMatchFlutterLimits() {
        String family="👨‍👩‍👧‍👦";
        command("hantuck01","group.join",map("groupId","group-1","introduction",family.repeat(100)));
        assertThatThrownBy(()->command("hantuck01","group.join",map("groupId","group-2","introduction",family.repeat(101)))).isInstanceOf(ApiException.class);
    }
    @Test void donationsPreserveCheckoutAndTodayDeadlineAndDoNotGrantMembership() {
        Map<String,Object> data=map("checkoutId","same-checkout","type","prepaid","groupId","group-3","storeId","example-store-1","amount",10000,"expiresOn","2026-10-28");
        var first=command("hantuck01","donation.complete",data);assertThat(command("hantuck01","donation.complete",data)).isEqualTo(first);
        assertThat(rows("hantuck01","donations")).hasSize(1);
        assertThatThrownBy(()->request("hantuck01","prepaid-"+first.get("id"))).isInstanceOf(ApiException.class);
        var changed=new LinkedHashMap<>(data);changed.put("amount",20000);
        assertThatThrownBy(()->command("hantuck01","donation.complete",changed)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->command("hantuck01","donation.complete",map("checkoutId","past","type","prepaid","groupId","group-3","storeId","example-store-1","amount",10000,"expiresOn","2026-10-27"))).isInstanceOf(ApiException.class);
        command("hantuck01","donation.complete",map("checkoutId","money","type","money","groupId","group-1","amount",5000));
        assertThat(rows("leader01","donations")).hasSize(1);
    }
    @Test void fullUsageAndReversalRestoreExactlyOnceIncludingExpiredFunds() {
        String donation=donate("hantuck01",10000),offer="prepaid-"+donation;
        String req=request("leader03",offer);assertThat(request("leader03",offer)).isEqualTo(req);
        assertThat(byId("leader03","offers",offer).get("usedAmount")).isEqualTo(0);
        String code=str(byId("leader03","requests",req),"code");
        command("store01","usage.find",map("code",code,"storeId","example-store-1"));
        amount(req,9000);
        assertThat(byId("leader03","offers",offer).get("usedAmount")).isEqualTo(0);
        assertThatThrownBy(()->approve("hantuck01",req)).isInstanceOf(ApiException.class);
        String receipt=approve("leader03",req);assertThat(approve("leader03",req)).isEqualTo(receipt);
        assertThat(((Number)byId("leader03","offers",offer).get("usedAmount")).intValue()).isEqualTo(9000);
        clock.time=clock.time.plus(Duration.ofDays(1));
        command("store01","usage.reverse",map("receiptId",receipt,"storeId","example-store-1","reason","잘못 입력"));
        command("store01","usage.reverse",map("receiptId",receipt,"storeId","example-store-1","reason","중복 요청"));
        assertThat(((Number)byId("leader03","offers",offer).get("usedAmount")).intValue()).isZero();
        assertThat(byId("leader03","offers",offer).get("expiresOn")).isEqualTo("2026-10-28");
        assertThatThrownBy(()->request("leader03",offer)).isInstanceOf(ApiException.class);
        assertThat(byId("leader03","requests",req).get("status")).isEqualTo("paymentCancelled");
    }
    @Test void expiryCancellationAndInsufficientBalanceNeverDebit() {
        String req=request("leader03","example-store-1");
        assertThatThrownBy(()->amount(req,66000)).isInstanceOf(ApiException.class);
        amount(req,9000);clock.time=clock.time.plus(Duration.ofMinutes(10));
        assertThatThrownBy(()->approve("leader03",req)).isInstanceOf(ApiException.class);
        String next=request("leader03","example-store-1");command("leader03","usage.cancel",map("requestId",next));
        assertThatThrownBy(()->amount(next,9000)).isInstanceOf(ApiException.class);
        assertThat(((Number)byId("leader03","offers","example-store-1").get("usedAmount")).intValue()).isEqualTo(35000);
    }
    @Test void thanksPhotosReachOnlyDonorAndRecipientAndCannotBeReplaced() {
        String req=request("leader03","example-store-1");amount(req,9000);String receipt=approve("leader03",req);
        var payload=map("receiptId",receipt,"message","감사합니다","photo",map("name","thanks.png","base64","iVBORw0KGgo="));
        command("leader03","usage.thanks",payload);command("leader03","usage.thanks",payload);
        assertThat(byId("hantuck01","receipts",receipt).get("thanks")).isNotNull();
        assertThat(byId("store01","receipts",receipt).get("thanks")).isNull();
        assertThat(rows("leader01","receipts")).isEmpty();
        assertThatThrownBy(()->command("hantuck01","usage.thanks",payload)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->command("leader03","usage.thanks",map("receiptId",receipt,"message","다른 내용"))).isInstanceOf(ApiException.class);
    }
    @Test void storeReviewHistoryAndMenuOwnershipAreServerEnforced() {
        var data=map("name","테스트 가게","registrationNumber","1234567890","address","서울 테스트 1","category","한식","phoneNumber","0212345678","description","소개");
        String first=str(command("hantuck01","store.apply",data),"id");
        assertThatThrownBy(()->command("hantuck01","store.approve",map("applicationId",first,"adminId","admin01"))).isInstanceOf(ApiException.class);
        command("admin01","store.reject",map("applicationId",first,"reason","정보 확인 필요"));
        var again=new LinkedHashMap<>(data);again.put("resubmissionOf",first);again.put("name","수정 가게");
        String second=str(command("hantuck01","store.apply",again),"id");
        String store=str(command("admin01","store.approve",map("applicationId",second)),"id");
        assertThat(rows("hantuck01","storeApplications")).hasSize(2);
        assertThat(byId("hantuck01","storeApplications",first).get("name")).isEqualTo("테스트 가게");
        assertThatThrownBy(()->command("hantuck01","store.apply",again)).isInstanceOf(ApiException.class);
        var menu=map("storeId",store,"expectedMenu",List.of(),"item",map("name","밥","price",9000,"description","정식","recommended",true));
        assertThatThrownBy(()->command("store01","store.menu.save",menu)).isInstanceOf(ApiException.class);
        command("hantuck01","store.menu.save",menu);
        assertThatThrownBy(()->command("hantuck01","store.menu.save",menu)).isInstanceOf(ApiException.class);
        assertThat((List<?>)byId("hantuck01","stores",store).get("menu")).hasSize(1);
        assertThatThrownBy(()->command("leader01","store.thumbnail",map("storeId",store,"thumbnail","assets/images/group-team.png"))).isInstanceOf(ApiException.class);
    }
    @Test void concurrentApprovalsSerializeAtDatabaseAndCannotOverdraw() throws Exception {
        register("seconduser");command("seconduser","group.invite",map("groupId","group-3","code","123456"));
        String offer="prepaid-"+donate("hantuck01",10000),a=request("leader03",offer),b=request("seconduser",offer);amount(a,9000);amount(b,9000);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            var f=pool.submit(()->{go.await();try{approve("leader03",a);return true;}catch(ApiException e){return false;}});
            var g=pool.submit(()->{go.await();try{approve("seconduser",b);return true;}catch(ApiException e){return false;}});go.countDown();
            assertThat(List.of(f.get(10,TimeUnit.SECONDS),g.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(((Number)byId("hantuck01","offers",offer).get("usedAmount")).intValue()).isEqualTo(9000);
            assertThat(rows("hantuck01","receipts")).hasSize(1);
        } finally{go.countDown();pool.shutdownNow();}
    }
    @Test void httpV2RequiresItsOwnSessionAndSupportsCors() throws Exception {
        mvc.perform(get("/api/v2/snapshot")).andExpect(status().isUnauthorized());
        var login=mvc.perform(post("/api/v2/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"hantuck01\",\"password\":\"welcome123!\"}")).andExpect(status().isOk()).andReturn();
        String token=json.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asText();
        mvc.perform(get("/api/v2/snapshot").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.account.id").value("hantuck01")).andExpect(jsonPath("$.groups[2].inviteHash").doesNotExist());
        mvc.perform(options("/api/v2/commands").header("Origin","http://127.0.0.1:8080").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://127.0.0.1:8080"));
        clock.time=clock.time.plus(Duration.ofHours(24));mvc.perform(get("/api/v2/snapshot").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
}
