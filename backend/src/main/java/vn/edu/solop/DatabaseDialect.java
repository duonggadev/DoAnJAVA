package vn.edu.solop;

import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
class DatabaseDialect {
    private final boolean mysql;
    DatabaseDialect(DataSource source) throws java.sql.SQLException {
        try(var connection=source.getConnection()) {
            String product=connection.getMetaData().getDatabaseProductName();
            mysql=product.contains("MySQL") || product.contains("MariaDB");
        }
    }
    boolean mysql(){return mysql;}
    String choose(String portable,String mysqlSql){return mysql?mysqlSql:portable;}
}
