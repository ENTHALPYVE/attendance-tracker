package com.college.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Creates the tables (schema.sql) and the demo data (DataSeeder) when the database can be reached.
 *
 * Spring's own start-up script runner would stop the whole server if the database is down
 * (e.g. Aiven powered the free database off), so the website itself would not open either.
 * Instead, the server always starts: the pages load, and anything that needs data answers
 * "the database cannot be reached" (503) until it is back. This class tries at start-up and
 * then every 30 seconds until it succeeds once; no restart is needed after powering the database on.
 */
@Component
public class DatabaseSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSetup.class);

    private final DataSource dataSource;
    private final DataSeeder seeder;

    private volatile boolean done;

    public DatabaseSetup(DataSource dataSource, DataSeeder seeder) {
        this.dataSource = dataSource;
        this.seeder = seeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        trySetup();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    public void retry() {
        if (!done) {
            trySetup();
        }
    }

    public boolean isDone() {
        return done;
    }

    synchronized void trySetup() {
        if (done) {
            return;
        }
        try {
            // Every statement is CREATE TABLE IF NOT EXISTS, so running it again is harmless.
            new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
            seeder.seedIfEmpty();
            done = true;
            log.info("Database ready.");
        } catch (RuntimeException e) {
            log.warn("Database not reachable yet, the website runs without it and will retry in 30 s: {}",
                    e.getMessage());
        }
    }
}
