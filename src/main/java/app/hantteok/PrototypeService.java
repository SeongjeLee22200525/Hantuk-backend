package app.hantteok;

import static app.hantteok.Database.*;
import static app.hantteok.PrototypeAuth.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-owned prototype domain. A DB row lock serializes commands even across
 * application instances; this favors simple correctness at capstone scale.
 * Clients submit commands, never replacement state or financial balances. */
@Service
@SuppressWarnings("unchecked")
public class PrototypeService {
    static final long MAX=9007199254740991L;
    private final Database db; private final PrototypeAuth auth; private final ObjectMapper json;
    private final SecureRandom random=new SecureRandom();
    PrototypeService(Database db,PrototypeAuth auth,ObjectMapper json) { this.db=db;this.auth=auth;this.json=json; }
    private String encode(Object o) { try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);} }
    private Map<String,Object> decode(Object o) { try{return json.readValue(o.toString(),new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);} }
    private List<Map<String,Object>> rows(Map<String,Object> s,String field) { return (List<Map<String,Object>>)s.get(field); }
    private Map<String,Object> object(Map<String,Object> r,String field) { if(!(r.get(field) instanceof Map)) throw ApiException.bad(field+" 형식을 확인해 주세요."); return (Map<String,Object>)r.get(field); }
    private Map<String,Object> find(Map<String,Object> s,String table,String id) { return rows(s,table).stream().filter(r->id.equals(str(r,"id"))).findFirst().orElseThrow(ApiException::missing); }
    private boolean same(Object a,Object b) { return Objects.equals(a,b); }
    private List<String> members(Map<String,Object> s,String group) { return (List<String>)((Map<String,Object>)s.get("members")).computeIfAbsent(group,k->new ArrayList<>()); }
    private boolean member(Map<String,Object> s,String group,String user) { return members(s,group).contains(user); }
    private boolean owner(Map<String,Object> s,String shop,String actor) { return actor.equals(str(find(s,"stores",shop),"ownerId")); }
    private void require(boolean condition,String message) { if(!condition) throw ApiException.conflict(message); }
    private long amount(Map<String,Object> data,String field) {
        Object v=data.get(field);
        if(!(v instanceof Integer || v instanceof Long) || ((Number)v).longValue()<1 || ((Number)v).longValue()>MAX) throw ApiException.bad("금액은 1원 이상의 정수로 입력해 주세요.");
        return ((Number)v).longValue();
    }
    private long number(Map<String,Object> data,String field) { return ((Number)data.get(field)).longValue(); }
    private boolean available(Map<String,Object> s,Map<String,Object> offer,String user) {
        var today=auth.today(); return member(s,str(offer,"groupId"),user) && number(offer,"usedAmount")<number(offer,"totalAmount")
            && !today.isBefore(date(str(offer,"startsOn"))) && !today.isAfter(date(str(offer,"expiresOn")));
    }
    private Map<String,Object> liveOffer(Map<String,Object> s,Map<String,Object> request) {
        require(!Set.of("completed","cancelled","paymentCancelled").contains(str(request,"status")) && Instant.parse(str(request,"expiresAt")).isAfter(db.now()),"만료되었거나 처리된 이용 코드입니다.");
        var offer=find(s,"offers",str(request,"offerId"));
        require(available(s,offer,str(request,"userId")),"소속·잔액·사용 기한을 다시 확인해 주세요."); return offer;
    }
    @Transactional(readOnly=true) public Map<String,Object> snapshot(String actor) {
        var record=db.one("SELECT * FROM prototype_state WHERE id=1"); return project(decode(record.get("document")),actor,number(record,"revision"));
    }
    private Map<String,Object> project(Map<String,Object> s,String actor,long revision) {
        var groups=rows(s,"groups").stream().map(g->{var copy=new LinkedHashMap<>(g);copy.remove("inviteHash");return copy;}).toList();
        var groupApps=rows(s,"groupApplications").stream().filter(a->actor.equals(a.get("userId")) || actor.equals(find(s,"groups",str(a,"groupId")).get("ownerId"))).toList();
        var visibleRequests=rows(s,"requests").stream().filter(r->actor.equals(r.get("userId")) || actor.equals(r.get("seenBy"))).toList();
        Map<String,Object> memberships=new LinkedHashMap<>();
        for(var g:groups) {
            var ids=new ArrayList<String>();
            if(member(s,str(g,"id"),actor)) ids.add(actor);
            for(var r:visibleRequests) if(same(r.get("groupId"),g.get("id")) && member(s,str(g,"id"),str(r,"userId")) && !ids.contains(str(r,"userId"))) ids.add(str(r,"userId"));
            memberships.put(str(g,"id"),ids);
        }
        var offers=rows(s,"offers").stream().filter(o->member(s,str(o,"groupId"),actor) || actor.equals(o.get("donorId")) || owner(s,str(o,"storeId"),actor)).toList();
        var receipts=rows(s,"receipts").stream().filter(r->actor.equals(r.get("userId")) || actor.equals(r.get("donorId")) || owner(s,str(r,"storeId"),actor)).map(r->{
            var copy=new LinkedHashMap<>(r);
            if(!actor.equals(r.get("userId")) && !actor.equals(r.get("donorId"))) copy.put("thanks",null);
            return copy;
        }).toList();
        var donations=rows(s,"donations").stream().filter(d->actor.equals(d.get("donorId")) || actor.equals(find(s,"groups",str(d,"groupId")).get("ownerId")) || (d.get("storeId")!=null && owner(s,str(d,"storeId"),actor))).toList();
        var apps=rows(s,"storeApplications").stream().filter(a->auth.admin(actor) || actor.equals(a.get("ownerId"))).toList();
        return map("account",auth.profile(actor),"isAdmin",auth.admin(actor),"revision",revision,"groups",groups,"members",memberships,"groupApplications",groupApps,"stores",rows(s,"stores"),"storeApplications",apps,"offers",offers,"donations",donations,"requests",visibleRequests,"receipts",receipts,
            "groupFavorites",((Map<String,Object>)s.get("groupFavorites")).getOrDefault(actor,List.of()),"storeFavorites",((Map<String,Object>)s.get("storeFavorites")).getOrDefault(actor,List.of()),"serverTime",db.now().toString());
    }
    @Transactional public Map<String,Object> command(String actor,String commandId,String action,Map<String,Object> data) {
        if(commandId==null || !commandId.matches("[A-Za-z0-9-]{8,80}") || data==null) throw ApiException.bad("명령 식별자와 입력값을 확인해 주세요.");
        var record=db.one("SELECT * FROM prototype_state WHERE id=1 FOR UPDATE");
        var state=decode(record.get("document"));
        String fingerprint;
        try{fingerprint=AuthService.digest(action+"\n"+json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(data));}catch(Exception e){throw ApiException.bad("입력 형식을 확인해 주세요.");}
        var past=db.all("SELECT * FROM prototype_commands WHERE user_id=? AND command_id=?",actor,commandId);
        if(!past.isEmpty()) {
            require(fingerprint.equals(str(past.get(0),"fingerprint")),"같은 명령 ID로 다른 내용을 보낼 수 없습니다.");
            return map("result",decode(past.get(0).get("result_json")),"snapshot",project(state,actor,number(record,"revision")));
        }
        Map<String,Object> result=execute(state,actor,action,data);
        long revision=number(record,"revision")+1;
        db.exec("UPDATE prototype_state SET revision=?,document=? WHERE id=1",revision,encode(state));
        db.exec("INSERT INTO prototype_commands(user_id,command_id,fingerprint,result_json,created_at) VALUES(?,?,?,?,?)",actor,commandId,fingerprint,encode(result),db.time());
        return map("result",result,"snapshot",project(state,actor,revision));
    }
    private Map<String,Object> execute(Map<String,Object> s,String actor,String action,Map<String,Object> d) {
        return switch(action) {
            case "group.join" -> join(s,actor,d,false);
            case "group.invite" -> join(s,actor,d,true);
            case "group.approve" -> approveGroup(s,actor,d);
            case "group.favorite" -> favorite(s,actor,d,"groups","groupFavorites","groupId");
            case "store.favorite" -> favorite(s,actor,d,"stores","storeFavorites","storeId");
            case "store.apply" -> applyStore(s,actor,d);
            case "store.approve" -> review(s,actor,d,true);
            case "store.reject" -> review(s,actor,d,false);
            case "store.thumbnail" -> thumbnail(s,actor,d);
            case "store.menu.save" -> menu(s,actor,d,false);
            case "store.menu.remove" -> menu(s,actor,d,true);
            case "donation.complete" -> donate(s,actor,d);
            case "usage.begin" -> begin(s,actor,d);
            case "usage.find" -> scan(s,actor,d);
            case "usage.amount" -> requestAmount(s,actor,d);
            case "usage.approve" -> approveUsage(s,actor,d);
            case "usage.cancel" -> cancel(s,actor,d);
            case "usage.reverse" -> reverse(s,actor,d);
            case "usage.thanks" -> thanks(s,actor,d);
            default -> throw ApiException.bad("지원하지 않는 명령입니다.");
        };
    }
    private Map<String,Object> favorite(Map<String,Object> s,String actor,Map<String,Object> d,String table,String field,String idField) {
        String id=text(d,idField,80,true); find(s,table,id);
        var favorites=(List<String>)((Map<String,Object>)s.get(field)).computeIfAbsent(actor,k->new ArrayList<>());
        boolean added=!favorites.remove(id); if(added) favorites.add(id); return map("added",added);
    }
    private Map<String,Object> join(Map<String,Object> s,String actor,Map<String,Object> d,boolean invitation) {
        String id=text(d,"groupId",80,true); var group=find(s,"groups",id);
        require(!member(s,id,actor),"이미 가입한 그룹입니다.");
        require(invitation==Boolean.TRUE.equals(group.get("locked")),invitation?"가입 신청을 이용해 주세요.":"초대 코드를 입력해 주세요.");
        if(invitation) {
            require(auth.invitationMatches(text(d,"code",100,true),str(group,"inviteHash")),"초대 코드가 일치하지 않습니다.");
            members(s,id).add(actor); return map("id",id);
        }
        require(rows(s,"groupApplications").stream().noneMatch(a->same(a.get("groupId"),id) && same(a.get("userId"),actor)),"이미 신청한 그룹입니다.");
        String application=id("application-");
        rows(s,"groupApplications").add(map("id",application,"groupId",id,"userId",actor,"userName",auth.profile(actor).get("name"),"introduction",graphemeText(d,"introduction",100,false),"createdAt",db.now().toString(),"approved",false)); return map("id",application);
    }
    private Map<String,Object> approveGroup(Map<String,Object> s,String actor,Map<String,Object> d) {
        var a=find(s,"groupApplications",text(d,"applicationId",80,true));
        if(!actor.equals(find(s,"groups",str(a,"groupId")).get("ownerId"))) throw ApiException.forbidden();
        if(!Boolean.TRUE.equals(a.get("approved"))) { members(s,str(a,"groupId")).add(str(a,"userId")); a.put("approved",true); }
        return map("id",a.get("id"));
    }
    private String normalized(String text) { return text.toLowerCase(Locale.ROOT).replaceAll("\\s+",""); }
    private Map<String,Object> applyStore(Map<String,Object> s,String actor,Map<String,Object> d) {
        String previousId=text(d,"resubmissionOf",80,false); Map<String,Object> prev=null;
        if(!previousId.isEmpty()) {
            prev=find(s,"storeApplications",previousId);
            if(!actor.equals(prev.get("ownerId"))) throw ApiException.forbidden();
            require("rejected".equals(prev.get("status")) && prev.get("resubmissionId")==null,"반려된 최신 신청만 재신청할 수 있습니다.");
        }
        String name=text(d,"name",50,true),address=text(d,"address",150,true),category=text(d,"category",30,true),description=text(d,"description",100,false);
        String registration=text(d,"registrationNumber",20,true).replace("-",""),phone=text(d,"phoneNumber",20,true).replace("-","");
        if(!registration.matches("[0-9]{10}") || !phone.matches("0[0-9]{8,10}") || !List.of("한식","중식","일식","양식","분식","카페·디저트","기타").contains(category)) throw ApiException.bad("사업자등록번호·전화번호·업종을 확인해 주세요.");
        String root=prev==null?null:str(prev,"rootApplicationId");
        require(rows(s,"stores").stream().noneMatch(x->same(x.get("registrationNumber"),registration) && normalized(str(x,"address")).equals(normalized(address))),"이미 등록된 가게입니다.");
        require(rows(s,"storeApplications").stream().noneMatch(x->!same(x.get("rootApplicationId"),root) && same(x.get("registrationNumber"),registration) && normalized(str(x,"address")).equals(normalized(address))),"이미 신청한 가게입니다.");
        String id=id("store-application-");
        rows(s,"storeApplications").add(map("id",id,"ownerId",actor,"ownerName",auth.profile(actor).get("name"),"name",name,"registrationNumber",registration,"address",address,"category",category,"phoneNumber",phone,"description",description,"createdAt",db.now().toString(),"rootApplicationId",root==null?id:root,"previousApplicationId",prev==null?null:prev.get("id"),"submissionNumber",prev==null?1:number(prev,"submissionNumber")+1,"storeId",null,"status","pending","rejectionReason",null,"reviewedAt",null,"resubmissionId",null));
        if(prev!=null) prev.put("resubmissionId",id); return map("id",id);
    }
    private Map<String,Object> review(Map<String,Object> s,String actor,Map<String,Object> d,boolean approve) {
        if(!auth.admin(actor)) throw ApiException.forbidden();
        var a=find(s,"storeApplications",text(d,"applicationId",80,true));
        if(approve && "approved".equals(a.get("status"))) return map("id",a.get("storeId"));
        require("pending".equals(a.get("status")),"심사 대기 신청만 처리할 수 있습니다.");
        a.put("reviewedAt",db.now().toString());
        if(!approve) { a.put("rejectionReason",graphemeText(d,"reason",300,true));a.put("status","rejected");return map("id",a.get("id")); }
        String id="registered-"+a.get("id");
        rows(s,"stores").add(map("id",id,"ownerId",a.get("ownerId"),"name",a.get("name"),"address",a.get("address"),"description",str(a,"description").isEmpty()?a.get("name")+"에서 따뜻한 한 끼를 나눠요.":a.get("description"),"category",a.get("category"),"registrationNumber",a.get("registrationNumber"),"phoneNumber",a.get("phoneNumber"),"tags",List.of(a.get("category")),"thumbnail","assets/images/group-team.png","menu",new ArrayList<>()));
        a.put("storeId",id);a.put("status","approved"); return map("id",id);
    }
    private Map<String,Object> owned(Map<String,Object> s,String actor,Map<String,Object> d) { var store=find(s,"stores",text(d,"storeId",100,true)); if(!actor.equals(store.get("ownerId"))) throw ApiException.forbidden(); return store; }
    private Map<String,Object> thumbnail(Map<String,Object> s,String actor,Map<String,Object> d) {
        var store=owned(s,actor,d); String image=text(d,"thumbnail",100,true);
        require(List.of("assets/images/group-team.png","assets/images/group-campus.png","assets/images/group-walk.png").contains(image),"사용할 사진을 선택해 주세요."); store.put("thumbnail",image);return map("id",store.get("id"));
    }
    private Map<String,Object> menu(Map<String,Object> s,String actor,Map<String,Object> d,boolean remove) {
        var store=owned(s,actor,d);var list=(List<Map<String,Object>>)store.get("menu");
        require(d.containsKey("expectedMenu") && same(d.get("expectedMenu"),list),"메뉴가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        Object supplied=d.get("index");
        if(supplied!=null && (!(supplied instanceof Integer || supplied instanceof Long) || ((Number)supplied).longValue() < -1 || ((Number)supplied).longValue()>100)) throw ApiException.bad("메뉴 순서를 확인해 주세요.");
        int index=supplied==null?-1:((Number)supplied).intValue();
        if(remove) { require(index>=0 && index<list.size(),"메뉴를 다시 확인해 주세요.");list.remove(index); }
        else {
            require(index>=-1 && index<list.size(),"메뉴를 다시 확인해 주세요.");
            var item=object(d,"item");String name=text(item,"name",50,true),description=text(item,"description",150,false);long price=amount(item,"price");
            for(int i=0;i<list.size();i++) if(i!=index && normalized(str(list.get(i),"name")).equals(normalized(name))) throw ApiException.conflict("이미 등록된 메뉴 이름입니다.");
            var saved=map("name",name,"description",description,"price",price,"recommended",Boolean.TRUE.equals(item.get("recommended")));
            if(index<0) list.add(saved);else list.set(index,saved);
        }
        return map("id",store.get("id"));
    }
    private Map<String,Object> donate(Map<String,Object> s,String actor,Map<String,Object> d) {
        String checkout=text(d,"checkoutId",100,true),type=text(d,"type",20,true),group=text(d,"groupId",80,true);
        if(!Set.of("money","prepaid").contains(type)) throw ApiException.bad("후원 유형을 확인해 주세요.");
        find(s,"groups",group);long amount=amount(d,"amount");String store=text(d,"storeId",100,false),deadline=text(d,"expiresOn",10,false);
        for(var old:rows(s,"donations")) if(actor.equals(old.get("donorId")) && checkout.equals(old.get("checkoutId"))) {
            require(type.equals(old.get("type")) && group.equals(old.get("groupId")) && amount==number(old,"amount") && store.equals(str(old,"storeId")) && deadline.equals(str(old,"expiresOn")),"이미 완료한 결제 정보가 다릅니다."); return map("id",old.get("id"));
        }
        if(type.equals("prepaid")) { find(s,"stores",store);require(!date(deadline).isBefore(auth.today()),"오늘 이후의 사용 기한을 입력해 주세요."); }
        else if(!store.isEmpty() || !deadline.isEmpty()) throw ApiException.bad("금액 후원에는 가게와 사용 기한을 지정하지 않습니다.");
        String id=id("donation-");String name=str(auth.profile(actor),"name");
        rows(s,"donations").add(map("id",id,"checkoutId",checkout,"type",type,"groupId",group,"donorId",actor,"donorName",name,"amount",amount,"createdAt",db.now().toString(),"storeId",store.isEmpty()?null:store,"expiresOn",deadline.isEmpty()?null:deadline));
        if(type.equals("prepaid")) rows(s,"offers").add(map("id","prepaid-"+id,"storeId",store,"groupId",group,"storeName",find(s,"stores",store).get("name"),"totalAmount",amount,"usedAmount",0,"startsOn",auth.today().toString(),"expiresOn",deadline,"donorId",actor,"donorName",name));
        return map("id",id);
    }
    private Map<String,Object> begin(Map<String,Object> s,String actor,Map<String,Object> d) {
        var offer=find(s,"offers",text(d,"offerId",100,true)); require(available(s,offer,actor),"현재 소속과 선결제 잔액·기한을 확인해 주세요.");
        for(var r:rows(s,"requests")) if(actor.equals(r.get("userId")) && same(r.get("offerId"),offer.get("id")) && Set.of("awaitingStore","awaitingUser").contains(str(r,"status")) && Instant.parse(str(r,"expiresAt")).isAfter(db.now())) return map("id",r.get("id"));
        String code=null;
        for(int i=0;i<1000;i++) {String candidate=String.valueOf(100000+random.nextInt(900000)); if(rows(s,"requests").stream().noneMatch(r->candidate.equals(r.get("code")))) {code=candidate;break;} }
        require(code!=null,"이용 코드를 다시 발급해 주세요.");
        String id=id("usage-");
        rows(s,"requests").add(map("id",id,"code",code,"offerId",offer.get("id"),"storeId",offer.get("storeId"),"groupId",offer.get("groupId"),"userId",actor,"userName",auth.profile(actor).get("name"),"createdAt",db.now().toString(),"expiresAt",db.now().plus(Duration.ofMinutes(10)).toString(),"status","awaitingStore","amount",null,"cashierId",null,"receiptId",null,"seenBy",null));return map("id",id);
    }
    private Map<String,Object> scan(Map<String,Object> s,String actor,Map<String,Object> d) {
        var shop=owned(s,actor,d);String code=text(d,"code",100,true).replaceFirst("^hantuck:prepaid:","");
        var r=rows(s,"requests").stream().filter(x->code.equals(x.get("code")) && same(x.get("storeId"),shop.get("id"))).findFirst().orElseThrow(()->ApiException.bad("현재 가게의 이용 코드를 확인해 주세요."));
        liveOffer(s,r);r.put("seenBy",actor);return map("id",r.get("id"));
    }
    private Map<String,Object> requestAmount(Map<String,Object> s,String actor,Map<String,Object> d) {
        var shop=owned(s,actor,d);var r=find(s,"requests",text(d,"requestId",100,true));if(!same(r.get("storeId"),shop.get("id"))) throw ApiException.forbidden();
        var offer=liveOffer(s,r);long amount=amount(d,"amount");
        require("awaitingStore".equals(r.get("status")),"이미 금액 확인을 요청한 코드입니다.");require(amount<=number(offer,"totalAmount")-number(offer,"usedAmount"),"현재 잔액 이내의 금액을 입력해 주세요.");
        r.put("amount",amount);r.put("cashierId",actor);r.put("seenBy",actor);r.put("status","awaitingUser");return map("id",r.get("id"));
    }
    private Map<String,Object> ownRequest(Map<String,Object> s,String actor,Map<String,Object> d) {var r=find(s,"requests",text(d,"requestId",100,true));if(!actor.equals(r.get("userId"))) throw ApiException.forbidden();return r;}
    private Map<String,Object> approveUsage(Map<String,Object> s,String actor,Map<String,Object> d) {
        var r=ownRequest(s,actor,d);
        if("completed".equals(r.get("status"))) return map("id",r.get("receiptId"));
        var offer=liveOffer(s,r);
        require("awaitingUser".equals(r.get("status")) && r.get("amount")!=null && r.get("cashierId")!=null && owner(s,str(r,"storeId"),str(r,"cashierId")),"가게에서 요청한 금액을 먼저 확인해 주세요.");
        long amount=number(r,"amount"),remaining=number(offer,"totalAmount")-number(offer,"usedAmount");require(amount>0 && amount<=remaining,"남은 잔액을 확인해 주세요.");
        String id=id("usage-receipt-");offer.put("usedAmount",number(offer,"usedAmount")+amount);
        rows(s,"receipts").add(map("id",id,"requestId",r.get("id"),"offerId",offer.get("id"),"groupId",offer.get("groupId"),"storeId",offer.get("storeId"),"donorId",offer.get("donorId"),"userId",actor,"userName",auth.profile(actor).get("name"),"amount",amount,"remainingAmount",remaining-amount,"createdAt",db.now().toString(),"thanks",null,"cancellation",null));
        r.put("receiptId",id);r.put("status","completed");return map("id",id);
    }
    private Map<String,Object> cancel(Map<String,Object> s,String actor,Map<String,Object> d) {
        var r=ownRequest(s,actor,d);require(r.get("receiptId")==null,"사용 완료된 결제는 가게에서 취소할 수 있습니다.");r.put("status","cancelled");return map("id",r.get("id"));
    }
    private Map<String,Object> reverse(Map<String,Object> s,String actor,Map<String,Object> d) {
        var store=owned(s,actor,d);var receipt=find(s,"receipts",text(d,"receiptId",100,true));
        if(!same(store.get("id"),receipt.get("storeId"))) throw ApiException.forbidden();
        if(receipt.get("cancellation")!=null) return map("id",receipt.get("id"));
        var r=find(s,"requests",str(receipt,"requestId"));require("completed".equals(r.get("status")),"사용 완료된 결제만 취소할 수 있습니다.");
        var offer=find(s,"offers",str(receipt,"offerId"));long amount=number(receipt,"amount");require(number(offer,"usedAmount")>=amount,"복구할 원래 선결제를 확인해 주세요.");
        offer.put("usedAmount",number(offer,"usedAmount")-amount);
        receipt.put("cancellation",map("ownerId",actor,"reason",graphemeText(d,"reason",300,false),"createdAt",db.now().toString(),"remainingAmount",number(offer,"totalAmount")-number(offer,"usedAmount"),"expiresOn",offer.get("expiresOn")));
        r.put("status","paymentCancelled");return map("id",receipt.get("id"));
    }
    private Map<String,Object> thanks(Map<String,Object> s,String actor,Map<String,Object> d) {
        var receipt=find(s,"receipts",text(d,"receiptId",100,true));if(!actor.equals(receipt.get("userId"))) throw ApiException.forbidden();
        require(receipt.get("cancellation")==null,"취소된 결제에는 감사 메시지를 보낼 수 없습니다.");
        String text=graphemeText(d,"message",500,false);Map<String,Object> photo=null;
        if(d.get("photo")!=null) {
            var image=object(d,"photo");String name=text(image,"name",255,true),data=text(image,"base64",7*1024*1024,true);byte[] bytes;
            try{bytes=Base64.getDecoder().decode(data);}catch(IllegalArgumentException e){throw ApiException.bad("올바른 사진을 첨부해 주세요.");}
            boolean png=bytes.length>=8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
            boolean jpg=bytes.length>=3 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
            boolean webp=bytes.length>=12 && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            if(bytes.length>5*1024*1024 || !(png||jpg||webp)) throw ApiException.bad("JPG·PNG·WebP 사진 1장(최대 5MB)을 선택해 주세요.");
            photo=map("name",name,"base64",Base64.getEncoder().encodeToString(bytes));
        }
        require(!text.isEmpty() || photo!=null,"메시지 또는 사진을 입력해 주세요.");
        if(receipt.get("thanks")!=null) {var old=(Map<String,Object>)receipt.get("thanks");require(same(old.get("text"),text) && same(old.get("photo"),photo),"이미 감사 메시지를 보냈습니다.");return map("id",receipt.get("id"));}
        receipt.put("thanks",map("text",text,"photo",photo,"createdAt",db.now().toString()));return map("id",receipt.get("id"));
    }
}
