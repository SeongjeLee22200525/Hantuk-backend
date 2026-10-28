package app.hantteok.config;

import app.hantteok.common.persistence.Database;
import app.hantteok.common.error.ApiException;
import app.hantteok.legacy.AuthService;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthService auth;
    private final String[] origins;
    WebConfig(AuthService auth,@Value("${app.cors-origins}") String origins) {
        this.auth=auth; this.origins=origins.split(",");
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if("OPTIONS".equals(request.getMethod())) return true;
                String path=request.getRequestURI();
                if(path.startsWith("/api/v2/")) return true; // v2 controller validates its own opaque sessions.
                if(path.equals("/api/health") || ("POST".equals(request.getMethod()) && (path.equals("/api/auth/login") || path.equals("/api/auth/register")))) return true;
                request.setAttribute("userId",auth.authenticate(request.getHeader("Authorization")));
                return true;
            }
        }).addPathPatterns("/api/**");
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST","PATCH","DELETE","OPTIONS")
            .allowedHeaders("Authorization","Content-Type").maxAge(3600);
    }
}
