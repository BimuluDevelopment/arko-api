package dk.arko.api.database.repository;

import dk.arko.api.database.pool.ConnectionPool;
import dk.arko.api.database.query.QueryBuilder;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Generic repository base class providing CRUD operations with async support.
 * Extend this for each entity type in your plugin.
 *
 * Usage:
 *   public class PlayerRepository extends Repository<UUID, PlayerData> {
 *       public PlayerRepository(ConnectionPool pool) {
 *           super(pool, "players", "uuid");
 *       }
 *       @Override
 *       protected PlayerData fromRow(Map<String, Object> row) { ... }
 *       @Override
 *       protected Map<String, Object> toRow(PlayerData entity) { ... }
 *       @Override
 *       protected UUID getId(PlayerData entity) { return entity.uuid(); }
 *   }
 */
public abstract class Repository<ID, T> {

    protected final ConnectionPool pool;
    protected final String table;
    protected final String idColumn;

    protected Repository(ConnectionPool pool, String table, String idColumn) {
        this.pool = pool;
        this.table = table;
        this.idColumn = idColumn;
    }

    /**
     * Convert a database row to an entity.
     */
    protected abstract T fromRow(Map<String, Object> row);

    /**
     * Convert an entity to a database row (column -> value map).
     */
    protected abstract Map<String, Object> toRow(T entity);

    /**
     * Get the ID from an entity.
     */
    protected abstract ID getId(T entity);

    // ─── CRUD ──────────────────────────────────────────────────

    public Optional<T> findById(ID id) throws SQLException {
        return QueryBuilder.select(table)
                .where(idColumn, id)
                .executeFirst(pool, this::fromRow);
    }

    public CompletableFuture<Optional<T>> findByIdAsync(ID id) {
        return QueryBuilder.select(table)
                .where(idColumn, id)
                .executeFirstAsync(pool, this::fromRow);
    }

    public List<T> findAll() throws SQLException {
        return QueryBuilder.select(table)
                .execute(pool, this::fromRow);
    }

    public CompletableFuture<List<T>> findAllAsync() {
        return QueryBuilder.select(table)
                .executeAsync(pool, this::fromRow);
    }

    public List<T> findWhere(String column, Object value) throws SQLException {
        return QueryBuilder.select(table)
                .where(column, value)
                .execute(pool, this::fromRow);
    }

    public CompletableFuture<List<T>> findWhereAsync(String column, Object value) {
        return QueryBuilder.select(table)
                .where(column, value)
                .executeAsync(pool, this::fromRow);
    }

    public List<T> findByIds(List<ID> ids) throws SQLException {
        return QueryBuilder.select(table)
                .whereIn(idColumn, ids)
                .execute(pool, this::fromRow);
    }

    public void insert(T entity) throws SQLException {
        Map<String, Object> row = toRow(entity);
        QueryBuilder qb = QueryBuilder.insert(table);
        row.forEach(qb::value);
        qb.executeUpdate(pool);
    }

    public CompletableFuture<Integer> insertAsync(T entity) {
        Map<String, Object> row = toRow(entity);
        QueryBuilder qb = QueryBuilder.insert(table);
        row.forEach(qb::value);
        return qb.executeUpdateAsync(pool);
    }

    public void update(T entity) throws SQLException {
        Map<String, Object> row = toRow(entity);
        ID id = getId(entity);
        QueryBuilder qb = QueryBuilder.update(table);
        row.forEach((col, val) -> {
            if (!col.equals(idColumn)) qb.value(col, val);
        });
        qb.where(idColumn, id).executeUpdate(pool);
    }

    public CompletableFuture<Integer> updateAsync(T entity) {
        Map<String, Object> row = toRow(entity);
        ID id = getId(entity);
        QueryBuilder qb = QueryBuilder.update(table);
        row.forEach((col, val) -> {
            if (!col.equals(idColumn)) qb.value(col, val);
        });
        return qb.where(idColumn, id).executeUpdateAsync(pool);
    }

    /**
     * Insert or update (upsert) an entity.
     */
    public void save(T entity) throws SQLException {
        Map<String, Object> row = toRow(entity);
        QueryBuilder qb = QueryBuilder.upsert(table, idColumn);
        row.forEach(qb::value);
        qb.executeUpdate(pool);
    }

    public CompletableFuture<Integer> saveAsync(T entity) {
        Map<String, Object> row = toRow(entity);
        QueryBuilder qb = QueryBuilder.upsert(table, idColumn);
        row.forEach(qb::value);
        return qb.executeUpdateAsync(pool);
    }

    /**
     * Batch save multiple entities.
     */
    public CompletableFuture<int[]> saveBatchAsync(List<T> entities) {
        if (entities.isEmpty()) return CompletableFuture.completedFuture(new int[0]);
        Map<String, Object> sampleRow = toRow(entities.get(0));
        List<String> columns = new ArrayList<>(sampleRow.keySet());
        List<List<Object>> rows = entities.stream()
                .map(e -> new ArrayList<>(toRow(e).values()))
                .map(list -> (List<Object>) list)
                .toList();
        return QueryBuilder.batchUpsert(pool, table, columns, List.of(idColumn), rows);
    }

    public void delete(ID id) throws SQLException {
        QueryBuilder.delete(table).where(idColumn, id).executeUpdate(pool);
    }

    public CompletableFuture<Integer> deleteAsync(ID id) {
        return QueryBuilder.delete(table).where(idColumn, id).executeUpdateAsync(pool);
    }

    public boolean exists(ID id) throws SQLException {
        return QueryBuilder.select(table)
                .columns("1")
                .where(idColumn, id)
                .limit(1)
                .executeFirst(pool)
                .isPresent();
    }

    public CompletableFuture<Boolean> existsAsync(ID id) {
        return QueryBuilder.select(table)
                .columns("1")
                .where(idColumn, id)
                .limit(1)
                .executeFirstAsync(pool)
                .thenApply(Optional::isPresent);
    }

    public long count() throws SQLException {
        return QueryBuilder.select(table)
                .columns("COUNT(*) as count")
                .executeFirst(pool)
                .map(row -> ((Number) row.get("count")).longValue())
                .orElse(0L);
    }

    public CompletableFuture<Long> countAsync() {
        return QueryBuilder.select(table)
                .columns("COUNT(*) as count")
                .executeFirstAsync(pool)
                .thenApply(opt -> opt.map(row -> ((Number) row.get("count")).longValue()).orElse(0L));
    }

    // ─── Pagination ────────────────────────────────────────────

    public Page<T> findPage(int page, int pageSize) throws SQLException {
        long total = count();
        List<T> items = QueryBuilder.select(table)
                .limit(pageSize)
                .offset(page * pageSize)
                .execute(pool, this::fromRow);
        return new Page<>(items, page, pageSize, total);
    }

    public Page<T> findPage(int page, int pageSize, String orderBy, boolean ascending) throws SQLException {
        long total = count();
        List<T> items = QueryBuilder.select(table)
                .orderBy(orderBy, ascending)
                .limit(pageSize)
                .offset(page * pageSize)
                .execute(pool, this::fromRow);
        return new Page<>(items, page, pageSize, total);
    }

    /**
     * Custom query builder starting from this repository's table.
     */
    public QueryBuilder query() {
        return QueryBuilder.select(table);
    }

    // ─── Inner Classes ─────────────────────────────────────────

    public record Page<T>(List<T> items, int page, int pageSize, long totalItems) {
        public int totalPages() { return (int) Math.ceil((double) totalItems / pageSize); }
        public boolean hasNext() { return page < totalPages() - 1; }
        public boolean hasPrevious() { return page > 0; }
    }
}
