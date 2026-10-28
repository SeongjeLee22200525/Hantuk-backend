package app.hantteok.legacy;

import app.hantteok.common.persistence.Database;
import app.hantteok.common.error.ApiException;
import static app.hantteok.common.persistence.Database.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final Database db;
    private final AuthService auth;
    private final CatalogService catalog;
    private final BenefitService benefits;
    private final QrService qr;
    private final SupportService support;
    private final MerchantService merchant;
    private final boolean demo;
    ApiController(Database db,AuthService auth,CatalogService catalog,BenefitService benefits,QrService qr,SupportService support,MerchantService merchant,@Value("${app.demo-enabled}") boolean demo) {
        this.db=db; this.auth=auth; this.catalog=catalog; this.benefits=benefits; this.qr=qr; this.support=support; this.merchant=merchant; this.demo=demo;
    }
    @GetMapping("/health") public Object health() { db.count("SELECT COUNT(*) FROM communities"); return map("status","UP","demo",demo,"paymentMode","mock"); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED) public Object register(@Valid @RequestBody Requests.Register r) { return auth.register(r); }
    @PostMapping("/auth/login") public Object login(@Valid @RequestBody Requests.Login r) { return auth.login(r); }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(HttpServletRequest r) { auth.logout(r.getHeader("Authorization")); }
    @GetMapping("/me") public Object me(@RequestAttribute String userId) { return auth.profile(userId); }
    @PatchMapping("/me") public Object profile(@RequestAttribute String userId,@Valid @RequestBody Requests.Profile r) { return auth.updateProfile(userId,r); }
    @PatchMapping("/me/notification-settings") public Object settings(@RequestAttribute String userId,@Valid @RequestBody Requests.Settings r) { return auth.settings(userId,r); }
    @GetMapping("/communities") public Object communities(@RequestAttribute String userId) { return catalog.communities(userId); }
    @PostMapping("/communities/{id}/verify") public Object verify(@RequestAttribute String userId,@PathVariable String id,@Valid @RequestBody Requests.Verification r) { return catalog.verify(userId,id,r); }
    @GetMapping("/shops") public Object shops(@RequestParam(required=false) String query,@RequestParam(required=false) String category) { return catalog.shops(query,category); }
    @GetMapping("/shops/{id}") public Object shop(@PathVariable String id) { return catalog.shop(id); }
    @GetMapping("/benefits") public Object benefits(@RequestAttribute String userId,@RequestParam(required=false) String communityId,@RequestParam(required=false) String shopId,@RequestParam(defaultValue="false") boolean mine,@RequestParam(required=false) String state) { return benefits.list(userId,communityId,shopId,mine,state); }
    @GetMapping("/benefits/{id}") public Object benefit(@RequestAttribute String userId,@PathVariable String id) { return benefits.detail(userId,id); }
    @PostMapping("/benefits") @ResponseStatus(HttpStatus.CREATED) public Object pay(@RequestAttribute String userId,@Valid @RequestBody Requests.Payment r) { return benefits.pay(userId,r); }
    @GetMapping("/benefits/{id}/usages") public Object donorUsages(@RequestAttribute String userId,@PathVariable String id) { return benefits.donorUsages(userId,id); }
    @GetMapping("/usages") public Object usages(@RequestAttribute String userId) { return benefits.usages(userId); }
    @GetMapping("/usages/{id}") public Object usage(@RequestAttribute String userId,@PathVariable String id) { return benefits.usage(userId,id); }
    @PostMapping("/qr-requests") @ResponseStatus(HttpStatus.CREATED) public Object issue(@RequestAttribute String userId,@Valid @RequestBody Requests.Issue r) { return qr.issue(userId,r.benefitId()); }
    @GetMapping("/qr-requests/{id}") public Object request(@RequestAttribute String userId,@PathVariable String id) { return qr.get(userId,id); }
    @PostMapping("/qr-requests/{id}/approve") public Object approve(@RequestAttribute String userId,@PathVariable String id) { return qr.approve(userId,id); }
    @PostMapping("/qr-requests/{id}/cancel") public Object cancel(@RequestAttribute String userId,@PathVariable String id) { return qr.cancel(userId,id); }
    @GetMapping("/tickets") public Object tickets(@RequestAttribute String userId) { return support.list(userId); }
    @GetMapping("/tickets/{id}") public Object ticket(@RequestAttribute String userId,@PathVariable String id) { return support.get(userId,id); }
    @PostMapping("/tickets") @ResponseStatus(HttpStatus.CREATED) public Object ticket(@RequestAttribute String userId,@Valid @RequestBody Requests.Ticket r) { return support.create(userId,r); }
    @GetMapping("/admin/tickets") public Object allTickets(@RequestAttribute String userId) { return support.adminList(userId); }
    @PostMapping("/admin/tickets/{id}/advance") public Object advance(@RequestAttribute String userId,@PathVariable String id) { return support.advance(userId,id); }
    @GetMapping("/merchant/shops/{id}/dashboard") public Object dashboard(@RequestAttribute String userId,@PathVariable String id) { return merchant.dashboard(userId,id); }
    @PatchMapping("/merchant/shops/{id}") public Object edit(@RequestAttribute String userId,@PathVariable String id,@Valid @RequestBody Requests.ShopEdit r) { return catalog.edit(userId,id,r); }
    @GetMapping("/merchant/shops/{id}/benefits") public Object shopBenefits(@RequestAttribute String userId,@PathVariable String id) { db.ownShop(userId,id); return benefits.list(userId,null,id,false,null); }
    @GetMapping("/merchant/shops/{id}/transactions") public Object transactions(@RequestAttribute String userId,@PathVariable String id) { return merchant.transactions(userId,id); }
    @GetMapping("/merchant/qr-requests/{id}") public Object merchantQr(@RequestAttribute String userId,@PathVariable String id) { return qr.merchantGet(userId,id); }
    @PostMapping("/merchant/qr-requests/{id}/order") public Object order(@RequestAttribute String userId,@PathVariable String id,@Valid @RequestBody Requests.Order r) { return qr.order(userId,id,r); }
    @GetMapping("/merchant/shops/{id}/settlements") public Object settlements(@RequestAttribute String userId,@PathVariable String id) { return merchant.settlements(userId,id); }
    @GetMapping("/merchant/shops/{id}/settlements/{day}") public Object settlement(@RequestAttribute String userId,@PathVariable String id,@PathVariable LocalDate day) { return merchant.settlement(userId,id,day); }
    @PostMapping("/merchant/shops/{id}/settlements/{day}/complete") public Object settle(@RequestAttribute String userId,@PathVariable String id,@PathVariable LocalDate day) { return merchant.settle(userId,id,day); }
    @GetMapping("/notices") public Object notices(@RequestAttribute String userId) {
        return db.all("SELECT * FROM notices WHERE user_id=? ORDER BY created_at DESC,id",userId).stream().map(n->map("id",n.get("id"),"title",n.get("title"),"body",n.get("body"),"targetId",n.get("target_id"),"read",n.get("is_read"),"date",instant(n,"created_at"))).toList();
    }
    @PostMapping("/notices/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@RequestAttribute String userId,@PathVariable String id) {
        if(db.exec("UPDATE notices SET is_read=TRUE WHERE id=? AND user_id=?",id,userId)==0) throw ApiException.missing();
    }
}
