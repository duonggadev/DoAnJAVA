package vn.edu.solop;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static vn.edu.solop.AdminController.*;

@RestController
@RequestMapping("/api/account")
class AccountController {
    private final JdbcTemplate db;
    private final BCryptPasswordEncoder passwords;
    AccountController(JdbcTemplate db,BCryptPasswordEncoder passwords){this.db=db;this.passwords=passwords;}

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void password(@RequestBody PasswordInput input,Principal actor,HttpServletRequest request) {
        if(input.currentPassword()==null || input.newPassword()==null || input.newPassword().length()<8
                || input.newPassword().getBytes(StandardCharsets.UTF_8).length>72)throw bad("Mật khẩu mới cần ít nhất 8 ký tự và tối đa 72 byte.");
        var row=db.queryForMap("SELECT id,mat_khau FROM nguoi_dung WHERE ma_so=?",actor.getName());
        if(!passwords.matches(input.currentPassword(),row.get("mat_khau").toString()))throw bad("Mật khẩu hiện tại chưa đúng.");
        if(passwords.matches(input.newPassword(),row.get("mat_khau").toString()))throw bad("Mật khẩu mới phải khác mật khẩu hiện tại.");
        updated(db.update("UPDATE nguoi_dung SET mat_khau=?,ban_phien=ban_phien+1 WHERE id=? AND mat_khau=?",
                passwords.encode(input.newPassword()),row.get("id"),row.get("mat_khau")));
        var session=request.getSession(false);
        if(session!=null)session.invalidate();
        SecurityContextHolder.clearContext();
    }
    record PasswordInput(String currentPassword,String newPassword){}
}