package com.novatech.firstday;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class Configuration {
    @Bean Env env() { return new Env(); }
    @Bean DataSource dataSource(Env env) {
        HikariConfig config = new HikariConfig();
        String schema = System.getProperty("firstday.testSchema", "");
        String url = "jdbc:postgresql://" + env.get("DB_HOST","127.0.0.1") + ":" + env.get("DB_PORT","5435") + "/" + env.get("DB_NAME","firstday_java_db");
        if (!schema.isEmpty()) {
            if (!schema.matches("java_test_[a-f0-9]+")) throw new IllegalArgumentException("Invalid test schema");
            url += "?currentSchema=" + schema + ",public";
        }
        config.setJdbcUrl(url);
        config.setUsername(env.get("DB_USER","firstday_user"));
        config.setPassword(env.get("DB_PASSWORD",""));
        config.setMaximumPoolSize(6);
        config.setConnectionTimeout(5000);
        config.setInitializationFailTimeout(-1);
        config.setPoolName("firstday-java");
        return new HikariDataSource(config);
    }
    public static class Env {
        private final Dotenv values;
        public Env() {
            Path root = Path.of("").toAbsolutePath();
            if (root.getFileName().toString().equals("backend")) root = root.getParent();
            Path file = Path.of(System.getProperty("firstday.env",
                System.getenv().getOrDefault("FIRSTDAY_ENV_FILE", root.resolve(".env").toString())));
            values = Dotenv.configure().directory(file.toAbsolutePath().getParent().toString())
                .filename(file.getFileName().toString()).ignoreIfMissing().load();
        }
        public String get(String key, String fallback) {
            String value=values.get(key);
            if(value==null)return fallback;
            if(value.length()>=2 && value.charAt(0)==39 && value.charAt(value.length()-1)==39)
                return value.substring(1,value.length()-1).replace("\\'", "'");
            return value;
        }
    }
}
