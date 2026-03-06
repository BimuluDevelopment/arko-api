package dk.arko.api.database.migration;

import dk.arko.api.database.pool.ConnectionPool;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Database migration manager powered by Flyway.
 * Supports versioned migrations, repeatable migrations, and multi-schema setups.
 * Each plugin gets its own migration namespace to avoid conflicts.
 */
public class MigrationManager {

    private final ConnectionPool pool;
    private final Logger logger;

    public MigrationManager(ConnectionPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    /**
     * Run migrations for a plugin.
     *
     * @param pluginName    Unique plugin name (used for migration history table)
     * @param migrationPath Classpath location of migrations (e.g., "db/migrations/myplugin")
     */
    public MigrateResult migrate(String pluginName, String migrationPath) {
        Flyway flyway = buildFlyway(pluginName, migrationPath);
        logger.info("[ArkoAPI Migration] Running migrations for '" + pluginName + "'...");
        MigrateResult result = flyway.migrate();
        logger.info("[ArkoAPI Migration] Applied " + result.migrationsExecuted + " migration(s) for '" + pluginName + "'");
        return result;
    }

    /**
     * Run migrations from a default path: "db/migrations/{pluginName}".
     */
    public MigrateResult migrate(String pluginName) {
        return migrate(pluginName, "db/migrations/" + pluginName.toLowerCase());
    }

    /**
     * Run migrations with a specific schema.
     */
    public MigrateResult migrate(String pluginName, String migrationPath, String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(pool.getPrimaryDataSource())
                .locations("classpath:" + migrationPath)
                .table("flyway_" + pluginName.toLowerCase())
                .schemas(schema)
                .createSchemas(true)
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();
        logger.info("[ArkoAPI Migration] Running migrations for '" + pluginName + "' in schema '" + schema + "'...");
        MigrateResult result = flyway.migrate();
        logger.info("[ArkoAPI Migration] Applied " + result.migrationsExecuted + " migration(s) for '" + pluginName + "'");
        return result;
    }

    /**
     * Get pending migration info for a plugin.
     */
    public List<MigrationInfo> getPendingMigrations(String pluginName, String migrationPath) {
        Flyway flyway = buildFlyway(pluginName, migrationPath);
        return Arrays.stream(flyway.info().pending()).toList();
    }

    /**
     * Get all migration info for a plugin.
     */
    public List<MigrationInfo> getAllMigrations(String pluginName, String migrationPath) {
        Flyway flyway = buildFlyway(pluginName, migrationPath);
        return Arrays.stream(flyway.info().all()).toList();
    }

    /**
     * Validate migrations without running them.
     */
    public boolean validate(String pluginName, String migrationPath) {
        try {
            Flyway flyway = buildFlyway(pluginName, migrationPath);
            flyway.validate();
            return true;
        } catch (Exception e) {
            logger.warning("[ArkoAPI Migration] Validation failed for '" + pluginName + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Repair migration history (fix checksums, remove failed entries).
     */
    public void repair(String pluginName, String migrationPath) {
        Flyway flyway = buildFlyway(pluginName, migrationPath);
        flyway.repair();
        logger.info("[ArkoAPI Migration] Repaired migration history for '" + pluginName + "'");
    }

    /**
     * Clean the database (DANGEROUS - drops all objects). Only use in dev.
     */
    public void clean(String pluginName, String migrationPath) {
        Flyway flyway = buildFlyway(pluginName, migrationPath);
        flyway.clean();
        logger.warning("[ArkoAPI Migration] CLEANED database for '" + pluginName + "' - all objects dropped!");
    }

    private Flyway buildFlyway(String pluginName, String migrationPath) {
        return Flyway.configure()
                .dataSource(pool.getPrimaryDataSource())
                .locations("classpath:" + migrationPath)
                .table("flyway_" + pluginName.toLowerCase())
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();
    }
}
