package vn.edu.solop;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod","laragon"})
class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder encoder;
    private final String password;

    BootstrapAdmin(JdbcTemplate db, BCryptPasswordEncoder encoder,
                   @Value("${SOLOP_ADMIN_PASSWORD:}") String password) {
        this.db=db;
        this.encoder=encoder;
        this.password=password;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if(db.queryForObject("SELECT COUNT(*) FROM nguoi_dung WHERE vai_tro='ADMIN' AND bi_khoa=0",Integer.class)>0) return;
        if(password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) {
            throw new IllegalStateException("Set SOLOP_ADMIN_PASSWORD with 12 characters or more for first production start");
        }
        db.update("""
                INSERT INTO nguoi_dung(ma_so,ho_ten,vai_tro,mat_khau) VALUES ('admin','Ban đào tạo','ADMIN',?)
                """,encoder.encode(password));
    }
}
