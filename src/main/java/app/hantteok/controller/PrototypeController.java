package app.hantteok.controller;

import app.hantteok.service.PrototypeAuthService;
import app.hantteok.service.PrototypeService;
import app.hantteok.repository.DatabaseRepository;
import app.hantteok.common.error.ApiException;
import static app.hantteok.repository.DatabaseRepository.map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2")
public class PrototypeController {
    private final PrototypeAuthService auth;private final PrototypeService service;
    PrototypeController(PrototypeAuthService auth,PrototypeService service){this.auth=auth;this.service=service;}
    @GetMapping("/health") Object health(){return map("status","UP","apiVersion",2,"demo",auth.demoEnabled(),"paymentMode","mock");}
    @GetMapping("/auth/availability") Object availability(@RequestParam String id){return map("available",auth.available(id));}
    @PostMapping("/auth/email-challenges") Object challenge(@RequestBody Map<String,Object> data){return auth.challenge(data);}
    @PostMapping("/auth/email-verify") Object verify(@RequestBody Map<String,Object> data){var result=auth.verify(data);if(!Boolean.TRUE.equals(result.get("verified")))throw ApiException.bad(result.get("message").toString());return result;}
    @PostMapping("/auth/register") Object register(@RequestBody Map<String,Object> data){return auth.register(data);}
    @PostMapping("/auth/login") Object login(@RequestBody Map<String,Object> data){return auth.login(data);}
    @PostMapping("/auth/logout") Object logout(@RequestHeader(value="Authorization",required=false) String header){auth.logout(header);return map("loggedOut",true);}
    @GetMapping("/snapshot") Object snapshot(@RequestHeader(value="Authorization",required=false) String header){return service.snapshot(auth.user(header));}
    record Command(@NotBlank @Size(min=8,max=80) String commandId,@NotBlank @Size(max=50) String action,@NotNull Map<String,Object> data){}
    @PostMapping("/commands") Object command(@RequestHeader(value="Authorization",required=false) String header,@Valid @RequestBody Command command){return service.command(auth.user(header),command.commandId(),command.action(),command.data());}
}
