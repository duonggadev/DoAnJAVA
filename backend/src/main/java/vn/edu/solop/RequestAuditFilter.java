package vn.edu.solop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

class RequestAuditFilter extends OncePerRequestFilter {
    private final OperationsStore operations;
    RequestAuditFilter(OperationsStore operations){this.operations=operations;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String requestId=UUID.randomUUID().toString();
        response.setHeader("X-Request-Id",requestId);
        String before=actor(request);
        boolean failed=false;
        try{chain.doFilter(request,response);}
        catch(IOException|ServletException|RuntimeException error){failed=true;throw error;}
        finally {
            if(request.getRequestURI().startsWith("/api/") && !java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod())) {
                String after=actor(request);
                try{operations.audit(!after.equals("anonymous")?after:before,request.getMethod(),request.getRequestURI(),failed?500:response.getStatus(),requestId);}
                catch(org.springframework.dao.DataAccessException error){logger.error("Audit persistence failed for request "+requestId);}
            }
        }
    }
    private String actor(HttpServletRequest request) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal()))return authentication.getName();
        String login=request.getRequestURI().equals("/api/login")?request.getParameter("username"):null;
        return login!=null && login.matches("[A-Za-z0-9._-]{1,20}")?login:"anonymous";
    }
}
