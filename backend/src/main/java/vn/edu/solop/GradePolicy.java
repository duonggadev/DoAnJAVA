package vn.edu.solop;

import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class GradePolicy {
    private final JdbcTemplate db;
    private final Clock clock;
    GradePolicy(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}

    boolean eligible(long registration) {
        var rows=db.queryForList("WITH metrics AS ("+AcademicController.METRICS+") "+"""
                SELECT m.total,m.attended,COALESCE(h.nguong_chuyen_can,80) AS threshold
                FROM metrics m JOIN dang_ky d ON d.id=m.registration_id JOIN lop_hoc l ON l.id=d.lop_hoc_id
                LEFT JOIN hoc_ky h ON h.ma=l.hoc_ky WHERE d.id=?
                """,clock.millis(),AcademicController.cutoff(clock),registration);
        if(rows.isEmpty())throw AdminController.missing();
        var row=rows.getFirst();
        long total=((Number)row.get("total")).longValue();
        return total==0 || ((Number)row.get("attended")).longValue()*100>=total*((Number)row.get("threshold")).longValue();
    }
}