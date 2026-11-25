package vn.edu.solop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.util.Locale;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
class SecurityConfig {
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(JdbcTemplate db) {
        return username -> db.query("""
                SELECT ma_so, mat_khau, vai_tro, ban_phien FROM nguoi_dung WHERE ma_so = ? AND bi_khoa=0
                """, (row, index) -> new AccountUser(row.getString(1), row.getString(2), row.getString(3),
                row.getInt(4)), username)
                .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, JdbcTemplate db, AttemptLimits limits,OperationsStore operations) throws Exception {
        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/csrf", "/api/login", "/api/qr-tickets").permitAll()
                        .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/sessions/*/open", "/api/classes/*/sessions",
                                "/api/registrations/*/grades", "/api/classes/*/grades-import", "/api/session-management/**").hasRole("GIANG_VIEN")
                        .requestMatchers("/api/enrollment", "/api/enrollment/**").hasRole("SINH_VIEN")
                        .requestMatchers("/api/sessions/*/qr", "/api/leave/*/decision", "/api/classes/*/finalize").hasRole("GIANG_VIEN")
                        .requestMatchers("/api/sessions/*/check-in", "/api/sessions/*/check-in-context", "/api/my-study", "/api/my-program", "/api/sessions/*/leave", "/api/sessions/*/leave-file").hasRole("SINH_VIEN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(login -> login.loginProcessingUrl("/api/login")
                        .successHandler((request, response, user) -> {
                            limits.refund(loginKey(request));
                            request.getSession().setAttribute("solop.loginVersion",UUID.randomUUID().toString());
                            response.setStatus(204);
                        })
                        .failureHandler((request, response, error) -> response.setStatus(401)))
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, user) -> response.setStatus(204)))
                .requestCache(cache -> cache.disable())
                .addFilterAfter(new RequestAuditFilter(operations),org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        if ("POST".equals(request.getMethod()) && "/api/login".equals(request.getServletPath())
                                && (!limits.reserve("login-ip:" + request.getRemoteAddr(), 120, 60_000)
                                    || !limits.reserve(loginKey(request), 8, 60_000))) {
                            response.setStatus(429);
                            response.setHeader("Retry-After", "60");
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                            throws IOException, ServletException {
                        var context = SecurityContextHolder.getContext();
                        var auth = context.getAuthentication();
                        if (auth != null && auth.getPrincipal() instanceof AccountUser current) {
                            var revisions = db.query("SELECT mat_khau, vai_tro, ban_phien FROM nguoi_dung WHERE ma_so = ? AND bi_khoa=0",
                                    (row, index) -> revision(row.getString(1), row.getString(2), row.getInt(3)), current.getUsername());
                            if (!revisions.contains(current.revision)) {
                                var session = request.getSession(false);
                                if (session != null) session.invalidate();
                                SecurityContextHolder.clearContext();
                                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                return;
                            }
                        }
                        chain.doFilter(request, response);
                    }
                }, AuthorizationFilter.class)
                .build();
    }

    private static String loginKey(HttpServletRequest request) {
        String code = request.getParameter("username");
        return "login:" + (code != null && code.length() <= 20 ? code.toLowerCase(Locale.ROOT) : "<invalid>");
    }

    private static UUID revision(String hash, String role, int version) {
        return UUID.nameUUIDFromBytes((hash + "/" + role + "/" + version).getBytes(StandardCharsets.UTF_8));
    }

    private static final class AccountUser extends User {
        private final UUID revision;

        AccountUser(String code, String hash, String role, int version) {
            super(code, hash, AuthorityUtils.createAuthorityList("ROLE_" + role));
            this.revision = revision(hash, role, version);
        }
    }
}
