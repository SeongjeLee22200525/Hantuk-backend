package app.hantteok.service;

import app.hantteok.dto.Requests;
import app.hantteok.bootstrap.DemoData;
import app.hantteok.repository.Database;
import app.hantteok.common.error.ApiException;
import app.hantteok.support.TestTimeConfig;
import app.hantteok.support.TestClock;
import static app.hantteok.repository.Database.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:hantteok-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.demo-enabled=true"})
@AutoConfigureMockMvc
@Import(TestTimeConfig.class)
@TestExecutionListeners(listeners=DependencyInjectionTestExecutionListener.class,mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class ServerIntegrationTest {
    @Autowired Database db;
    @Autowired DemoData seed;
    @Autowired QrService qr;
    @Autowired BenefitService benefits;
    @Autowired SupportService support;
    @Autowired MerchantService merchant;
    @Autowired AuthService auth;
    @Autowired CatalogService catalog;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TestClock clock;

    @BeforeEach void reset() {
        clock.time=Instant.parse("2026-10-23T08:00:00Z");
        for(String table:List.of("notices","support_tickets","usages","use_requests","benefit_menus","benefits","shop_menus","shops","memberships","communities","auth_sessions","app_users")) db.exec("DELETE FROM "+table);
        seed.run(new DefaultApplicationArguments());
    }
    private String issued(String user,String benefit) { return str(qr.issue(user,benefit),"id"); }
    private String pending(String user,String benefit,String menu,long amount) {
        String id=issued(user,benefit); qr.order("demo-merchant",id,new Requests.Order(menu,amount)); return id;
    }
    private Requests.Payment payment(String key) { return new Requests.Payment("s1","school",30000,4000,clock.instant().plus(Duration.ofDays(10)),"이웃","힘내세요",List.of(),key,false); }
    private String login(String email,String password) throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email",email,"password",password))))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    @Test void authenticationRegistrationIsolationAndLogout() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"demo@hantteok.app\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        var result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email","new@example.com","password","password123","nickname","새 이웃","agreed",true))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.verified").isEmpty()).andExpect(jsonPath("$.user.merchantShopIds").isEmpty()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new@example.com")).andExpect(jsonPath("$.password_hash").doesNotExist());
        mvc.perform(get("/api/merchant/shops/s1/dashboard").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        assertThat(auth.authenticate("Bearer "+login("new@example.com","password123"))).isNotBlank();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email","NEW@example.com","password","password123","nickname","새 이웃","agreed",true)))).andExpect(status().isConflict());
    }
    @Test void sessionsExpireAndTokensAreHashed() throws Exception {
        String token=login("demo@hantteok.app","demo1234");
        assertThat(db.count("SELECT COUNT(*) FROM auth_sessions WHERE token_hash=?",token)).isZero();
        clock.time=clock.time.plus(Duration.ofHours(24));
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void orderDoesNotDebitApprovalSplitsAndDuplicateFails() {
        String id=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        assertThat(num(benefits.detail("demo-user","b1"),"remaining")).isEqualTo(145000);
        var usage=qr.approve("demo-user",id);
        assertThat(num(usage,"applied")).isEqualTo(5000);
        assertThat(num(usage,"extra")).isEqualTo(3000);
        assertThat(num(benefits.detail("demo-user","b1"),"remaining")).isEqualTo(140000);
        assertThat(num(benefits.detail("demo-user","b1"),"allowance")).isZero();
        assertThatThrownBy(()->qr.approve("demo-user",id)).isInstanceOf(ApiException.class);
        assertThat(db.count("SELECT COUNT(*) FROM usages")).isEqualTo(1);
    }
    @Test void cumulativePersonalLimitAppliesAcrossRequests() {
        qr.approve("demo-user",pending("demo-user","b1","따뜻한 제육 덮밥",3000));
        var second=qr.approve("demo-user",pending("demo-user","b1","따뜻한 제육 덮밥",8000));
        assertThat(num(second,"applied")).isEqualTo(2000);
        assertThat(num(second,"extra")).isEqualTo(6000);
        assertThatThrownBy(()->issued("demo-user","b1")).isInstanceOf(ApiException.class);
    }
    @Test void verificationAndMenusAndMerchantOwnershipAreEnforced() {
        assertThatThrownBy(()->issued("demo-user","b4")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->catalog.verify("demo-user","volunteer",new Requests.Verification(null,"WRONG"))).isInstanceOf(ApiException.class);
        catalog.verify("demo-user","volunteer",new Requests.Verification(null,"WARM2026"));
        assertThat(issued("demo-user","b4")).isNotBlank();
        String id=issued("demo-user","b2");
        assertThatThrownBy(()->qr.order("demo-merchant",id,new Requests.Order("딸기 크림 케이크",6000))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->qr.order("demo-user",id,new Requests.Order("아메리카노",4500))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->qr.approve("demo-donor",id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->qr.get("demo-donor",id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->catalog.verify("demo-donor","school",new Requests.Verification("x@evil.com","123456"))).isInstanceOf(ApiException.class);
        catalog.verify("demo-donor","school",new Requests.Verification("student@hanmaeum.ac.kr","123456"));
    }
    @Test void expiryReissueAndCancellationInvalidatePriorRequests() {
        String old=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        String current=issued("demo-user","b1");
        assertThat(str(qr.get("demo-user",old),"status")).isEqualTo("cancelled");
        assertThatThrownBy(()->qr.approve("demo-user",old)).isInstanceOf(ApiException.class);
        clock.time=clock.time.plus(Duration.ofMinutes(3));
        assertThat(str(qr.get("demo-user",current),"status")).isEqualTo("expired");
        assertThatThrownBy(()->qr.order("demo-merchant",current,new Requests.Order("따뜻한 제육 덮밥",8000))).isInstanceOf(ApiException.class);
        String next=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        qr.cancel("demo-user",next);
        assertThatThrownBy(()->qr.approve("demo-user",next)).isInstanceOf(ApiException.class);
        String expires=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        clock.time=clock.time.plus(Duration.ofMinutes(3));
        assertThatThrownBy(()->qr.approve("demo-user",expires)).isInstanceOf(ApiException.class);
        assertThat(db.count("SELECT COUNT(*) FROM usages")).isZero();
    }
    @Test void expiredBenefitCannotBeIssuedOrApproved() {
        String id=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        db.exec("UPDATE benefits SET expires_at=? WHERE id='b1'",db.time());
        assertThatThrownBy(()->qr.approve("demo-user",id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->issued("demo-user","b1")).isInstanceOf(ApiException.class);
    }
    @Test void refundFreezesPendingApprovalAndOnlyAdminCanComplete() {
        String qrId=pending("demo-user","b3","버터 크루아상",4000);
        var ticket=support.create("demo-user",new Requests.Ticket("b3","취소·환불","남은 금액을 환불해 주세요","영수증 123"));
        String id=str(ticket,"id");
        assertThatThrownBy(()->qr.approve("demo-user",qrId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->support.advance("demo-user",id)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->support.create("demo-user",new Requests.Ticket("b1","취소·환불","타인의 결제 환불 요청",""))).isInstanceOf(ApiException.class);
        assertThat(str(support.advance("demo-admin",id),"status")).isEqualTo("검토 중");
        assertThat(num(support.advance("demo-admin",id),"refundAmount")).isEqualTo(35000);
        assertThat(str(benefits.detail("demo-user","b3"),"state")).isEqualTo("cancelled");
        assertThat(num(benefits.detail("demo-user","b3"),"remaining")).isEqualTo(35000);
        assertThat(num(support.advance("demo-admin",id),"refundAmount")).isEqualTo(35000);
    }
    @Test void paymentsAreIdempotentAndFailureDoesNotCreateBenefit() {
        var first=benefits.pay("demo-user",payment("key-1"));
        assertThat(benefits.pay("demo-user",payment("key-1")).get("id")).isEqualTo(first.get("id"));
        var r=payment("failure");
        assertThatThrownBy(()->benefits.pay("demo-user",new Requests.Payment(r.shopId(),r.communityId(),r.amount(),r.limit(),r.expiresAt(),r.donor(),r.message(),r.menus(),r.paymentKey(),true))).isInstanceOf(ApiException.class);
        assertThat(db.count("SELECT COUNT(*) FROM benefits WHERE payment_key='failure'")).isZero();
        assertThatThrownBy(()->benefits.pay("demo-user",new Requests.Payment("s1","school",40000,4000,r.expiresAt(),"이웃","힘내세요",List.of(),"key-1",false))).isInstanceOf(ApiException.class);
    }
    @Test void simultaneousDuplicateApprovalsDebitExactlyOnce() throws Exception {
        String id=pending("demo-user","b1","따뜻한 제육 덮밥",8000);
        var results=race(()->qr.approve("demo-user",id),()->qr.approve("demo-user",id));
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(num(benefits.detail("demo-user","b1"),"remaining")).isEqualTo(140000);
        assertThat(db.count("SELECT COUNT(*) FROM usages WHERE request_id=?",id)).isEqualTo(1);
    }
    @Test void concurrentUsersCannotOverdrawSharedBalance() throws Exception {
        catalog.verify("demo-donor","school",new Requests.Verification("other@hanmaeum.ac.kr","123456"));
        db.exec("UPDATE benefits SET remaining=1500 WHERE id='b1'");
        String first=pending("demo-user","b1","따뜻한 제육 덮밥",1000);
        String second=pending("demo-donor","b1","따뜻한 제육 덮밥",1000);
        var results=race(()->qr.approve("demo-user",first),()->qr.approve("demo-donor",second));
        assertThat(results).containsExactly(true,true);
        assertThat(num(benefits.detail("demo-user","b1"),"remaining")).isZero();
        assertThat(db.count("SELECT SUM(applied) FROM usages WHERE benefit_id='b1'")).isEqualTo(1500);
    }
    @Test void settlementCountsOnlyCompletedUsagesAndIsIdempotent() {
        pending("demo-user","b2","아메리카노",4500);
        assertThat(merchant.settlements("demo-merchant","s2")).isEmpty();
        qr.approve("demo-user",pending("demo-user","b1","따뜻한 제육 덮밥",8000));
        LocalDate day=clock.instant().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        assertThat(num(merchant.settle("demo-merchant","s1",day),"settledAmount")).isEqualTo(5000);
        assertThat(num(merchant.settle("demo-merchant","s1",day),"settledAmount")).isEqualTo(5000);
        assertThatThrownBy(()->merchant.settle("demo-user","s1",day)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->merchant.settle("demo-merchant","s2",day)).isInstanceOf(ApiException.class);
        assertThat(num(merchant.dashboard("demo-merchant","s1"),"unsettledAmount")).isZero();
    }
    @Test void donorHistoryDoesNotRevealRecipientIdentity() {
        qr.approve("demo-user",pending("demo-user","b1","따뜻한 제육 덮밥",8000));
        var history=benefits.donorUsages("demo-donor","b1");
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).doesNotContainKeys("userId","user_id","email");
        assertThat(benefits.usages("demo-donor")).isEmpty();
        assertThatThrownBy(()->benefits.donorUsages("demo-user","b1")).isInstanceOf(ApiException.class);
    }
    @Test void inputValidationCorsAndFullHttpQrFlow() throws Exception {
        String user=login("demo@hantteok.app","demo1234"), merchantToken=login("merchant@hantteok.app","merchant1234");
        mvc.perform(options("/api/qr-requests").header("Origin","http://127.0.0.1:8080").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://127.0.0.1:8080"));
        mvc.perform(post("/api/benefits").header("Authorization","Bearer "+user).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        var r=mvc.perform(post("/api/qr-requests").header("Authorization","Bearer "+user).contentType(MediaType.APPLICATION_JSON).content("{\"benefitId\":\"b1\"}"))
            .andExpect(status().isCreated()).andReturn();
        String id=json.readTree(r.getResponse().getContentAsByteArray()).get("id").asText();
        mvc.perform(post("/api/merchant/qr-requests/"+id+"/order").header("Authorization","Bearer "+merchantToken).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("menu","따뜻한 제육 덮밥","amount",8000))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending"));
        mvc.perform(post("/api/qr-requests/"+id+"/approve").header("Authorization","Bearer "+user)).andExpect(status().isOk()).andExpect(jsonPath("$.applied").value(5000)).andExpect(jsonPath("$.extra").value(3000));
        mvc.perform(get("/api/benefits/b1").header("Authorization","Bearer "+user)).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(140000));
    }
    @SafeVarargs private final List<Boolean> race(Callable<?>... actions) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(actions.length);
        CountDownLatch ready=new CountDownLatch(actions.length),go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var action:actions) futures.add(pool.submit(()->{ ready.countDown(); go.await(); try { action.call(); return true; } catch(ApiException e) { return false; } }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
            List<Boolean> results=new ArrayList<>();
            for(var f:futures) results.add(f.get(15,TimeUnit.SECONDS));
            return results;
        } finally { go.countDown(); pool.shutdownNow(); }
    }
}
