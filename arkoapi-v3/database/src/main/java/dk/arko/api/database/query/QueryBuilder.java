package dk.arko.api.database.query;

import dk.arko.api.database.pool.ConnectionPool;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Fluent query builder for type-safe PostgreSQL queries.
 * Supports SELECT, INSERT, UPDATE, DELETE, UPSERT, and raw queries.
 * All operations support async execution via CompletableFuture.
 *
 * Usage:
 *   QueryBuilder.select("players")
 *       .columns("uuid", "name", "balance")
 *       .where("uuid", uuid)
 *       .orderBy("balance", false)
 *       .limit(10)
 *       .executeAsync(pool)
 *       .thenApply(rows -> ...);
 */
public class QueryBuilder {

    private enum QueryType { SELECT, INSERT, UPDATE, DELETE, UPSERT, RAW }

    private QueryType type;
    private String table;
    private List<String> columns = new ArrayList<>();
    private final List<WhereClause> whereClauses = new ArrayList<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<String> orderBys = new ArrayList<>();
    private String rawSql;
    private final List<Object> rawParams = new ArrayList<>();
    private int limit = -1;
    private int offset = -1;
    private String[] conflictColumns;
    private String groupBy;
    private String having;
    private String joinClause;
    private boolean distinct = false;
    private String returning;

    private QueryBuilder() {}

    // ─── Static Factories ──────────────────────────────────────

    public static QueryBuilder select(String table) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.SELECT;
        qb.table = table;
        return qb;
    }

    public static QueryBuilder insert(String table) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.INSERT;
        qb.table = table;
        return qb;
    }

    public static QueryBuilder update(String table) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.UPDATE;
        qb.table = table;
        return qb;
    }

    public static QueryBuilder delete(String table) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.DELETE;
        qb.table = table;
        return qb;
    }

    /**
     * UPSERT (INSERT ... ON CONFLICT ... DO UPDATE).
     */
    public static QueryBuilder upsert(String table, String... conflictColumns) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.UPSERT;
        qb.table = table;
        qb.conflictColumns = conflictColumns;
        return qb;
    }

    /**
     * Raw SQL query.
     */
    public static QueryBuilder raw(String sql, Object... params) {
        QueryBuilder qb = new QueryBuilder();
        qb.type = QueryType.RAW;
        qb.rawSql = sql;
        qb.rawParams.addAll(Arrays.asList(params));
        return qb;
    }

    // ─── Query Modifiers ───────────────────────────────────────

    public QueryBuilder columns(String... cols) {
        this.columns.addAll(Arrays.asList(cols));
        return this;
    }

    public QueryBuilder distinct() {
        this.distinct = true;
        return this;
    }

    public QueryBuilder value(String column, Object value) {
        this.values.put(column, value);
        return this;
    }

    public QueryBuilder values(Map<String, Object> values) {
        this.values.putAll(values);
        return this;
    }

    public QueryBuilder where(String column, Object value) {
        whereClauses.add(new WhereClause(column, "=", value, "AND"));
        return this;
    }

    public QueryBuilder where(String column, String operator, Object value) {
        whereClauses.add(new WhereClause(column, operator, value, "AND"));
        return this;
    }

    public QueryBuilder orWhere(String column, Object value) {
        whereClauses.add(new WhereClause(column, "=", value, "OR"));
        return this;
    }

    public QueryBuilder orWhere(String column, String operator, Object value) {
        whereClauses.add(new WhereClause(column, operator, value, "OR"));
        return this;
    }

    public QueryBuilder whereIn(String column, List<?> values) {
        whereClauses.add(new WhereClause(column, "IN", values, "AND"));
        return this;
    }

    public QueryBuilder whereNotNull(String column) {
        whereClauses.add(new WhereClause(column, "IS NOT NULL", null, "AND"));
        return this;
    }

    public QueryBuilder whereNull(String column) {
        whereClauses.add(new WhereClause(column, "IS NULL", null, "AND"));
        return this;
    }

    public QueryBuilder whereLike(String column, String pattern) {
        whereClauses.add(new WhereClause(column, "LIKE", pattern, "AND"));
        return this;
    }

    public QueryBuilder whereBetween(String column, Object min, Object max) {
        whereClauses.add(new WhereClause(column, "BETWEEN", new Object[]{min, max}, "AND"));
        return this;
    }

    public QueryBuilder orderBy(String column, boolean ascending) {
        orderBys.add(column + (ascending ? " ASC" : " DESC"));
        return this;
    }

    public QueryBuilder orderBy(String column) {
        return orderBy(column, true);
    }

    public QueryBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public QueryBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public QueryBuilder groupBy(String column) {
        this.groupBy = column;
        return this;
    }

    public QueryBuilder having(String condition) {
        this.having = condition;
        return this;
    }

    public QueryBuilder join(String joinType, String table, String condition) {
        this.joinClause = (this.joinClause == null ? "" : this.joinClause + " ") +
                joinType + " JOIN " + table + " ON " + condition;
        return this;
    }

    public QueryBuilder innerJoin(String table, String condition) {
        return join("INNER", table, condition);
    }

    public QueryBuilder leftJoin(String table, String condition) {
        return join("LEFT", table, condition);
    }

    public QueryBuilder returning(String... cols) {
        this.returning = String.join(", ", cols);
        return this;
    }

    // ─── SQL Building ──────────────────────────────────────────

    public PreparedQuery build() {
        return switch (type) {
            case SELECT -> buildSelect();
            case INSERT -> buildInsert();
            case UPDATE -> buildUpdate();
            case DELETE -> buildDelete();
            case UPSERT -> buildUpsert();
            case RAW -> new PreparedQuery(rawSql, rawParams);
        };
    }

    private PreparedQuery buildSelect() {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (distinct) sql.append("DISTINCT ");
        sql.append(columns.isEmpty() ? "*" : String.join(", ", columns));
        sql.append(" FROM ").append(table);
        if (joinClause != null) sql.append(" ").append(joinClause);
        appendWhere(sql, params);
        if (groupBy != null) sql.append(" GROUP BY ").append(groupBy);
        if (having != null) sql.append(" HAVING ").append(having);
        if (!orderBys.isEmpty()) sql.append(" ORDER BY ").append(String.join(", ", orderBys));
        if (limit > 0) sql.append(" LIMIT ").append(limit);
        if (offset > 0) sql.append(" OFFSET ").append(offset);
        return new PreparedQuery(sql.toString(), params);
    }

    private PreparedQuery buildInsert() {
        List<Object> params = new ArrayList<>(values.values());
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table);
        sql.append(" (").append(String.join(", ", values.keySet())).append(")");
        sql.append(" VALUES (").append("?,".repeat(values.size()));
        sql.setLength(sql.length() - 1);
        sql.append(")");
        if (returning != null) sql.append(" RETURNING ").append(returning);
        return new PreparedQuery(sql.toString(), params);
    }

    private PreparedQuery buildUpdate() {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        values.forEach((col, val) -> {
            sql.append(col).append(" = ?, ");
            params.add(val);
        });
        sql.setLength(sql.length() - 2);
        appendWhere(sql, params);
        if (returning != null) sql.append(" RETURNING ").append(returning);
        return new PreparedQuery(sql.toString(), params);
    }

    private PreparedQuery buildDelete() {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        appendWhere(sql, params);
        if (returning != null) sql.append(" RETURNING ").append(returning);
        return new PreparedQuery(sql.toString(), params);
    }

    private PreparedQuery buildUpsert() {
        List<Object> params = new ArrayList<>(values.values());
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table);
        sql.append(" (").append(String.join(", ", values.keySet())).append(")");
        sql.append(" VALUES (").append("?,".repeat(values.size()));
        sql.setLength(sql.length() - 1);
        sql.append(") ON CONFLICT (").append(String.join(", ", conflictColumns)).append(") DO UPDATE SET ");
        values.keySet().stream()
                .filter(col -> !Arrays.asList(conflictColumns).contains(col))
                .forEach(col -> {
                    sql.append(col).append(" = EXCLUDED.").append(col).append(", ");
                });
        sql.setLength(sql.length() - 2);
        if (returning != null) sql.append(" RETURNING ").append(returning);
        return new PreparedQuery(sql.toString(), params);
    }

    @SuppressWarnings("unchecked")
    private void appendWhere(StringBuilder sql, List<Object> params) {
        if (whereClauses.isEmpty()) return;
        sql.append(" WHERE ");
        for (int i = 0; i < whereClauses.size(); i++) {
            WhereClause wc = whereClauses.get(i);
            if (i > 0) sql.append(" ").append(wc.connector).append(" ");

            if (wc.operator.equals("IN")) {
                List<?> inValues = (List<?>) wc.value;
                sql.append(wc.column).append(" IN (").append("?,".repeat(inValues.size()));
                sql.setLength(sql.length() - 1);
                sql.append(")");
                params.addAll(inValues);
            } else if (wc.operator.equals("IS NULL") || wc.operator.equals("IS NOT NULL")) {
                sql.append(wc.column).append(" ").append(wc.operator);
            } else if (wc.operator.equals("BETWEEN")) {
                Object[] range = (Object[]) wc.value;
                sql.append(wc.column).append(" BETWEEN ? AND ?");
                params.add(range[0]);
                params.add(range[1]);
            } else {
                sql.append(wc.column).append(" ").append(wc.operator).append(" ?");
                params.add(wc.value);
            }
        }
    }

    // ─── Execution ─────────────────────────────────────────────

    /**
     * Execute a SELECT query and return results.
     */
    public List<Map<String, Object>> execute(ConnectionPool pool) throws SQLException {
        PreparedQuery pq = build();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(pq.sql())) {
            setParameters(stmt, pq.params());
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        }
    }

    /**
     * Execute a SELECT and map results to objects.
     */
    public <T> List<T> execute(ConnectionPool pool, Function<Map<String, Object>, T> mapper) throws SQLException {
        return execute(pool).stream().map(mapper).toList();
    }

    /**
     * Execute a SELECT and return the first result.
     */
    public Optional<Map<String, Object>> executeFirst(ConnectionPool pool) throws SQLException {
        limit(1);
        List<Map<String, Object>> results = execute(pool);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Execute a SELECT and map the first result.
     */
    public <T> Optional<T> executeFirst(ConnectionPool pool, Function<Map<String, Object>, T> mapper) throws SQLException {
        return executeFirst(pool).map(mapper);
    }

    /**
     * Execute an INSERT/UPDATE/DELETE and return affected rows.
     */
    public int executeUpdate(ConnectionPool pool) throws SQLException {
        PreparedQuery pq = build();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(pq.sql())) {
            setParameters(stmt, pq.params());
            return stmt.executeUpdate();
        }
    }

    /**
     * Execute with RETURNING clause and return results.
     */
    public List<Map<String, Object>> executeReturning(ConnectionPool pool) throws SQLException {
        PreparedQuery pq = build();
        try (Connection conn = pool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(pq.sql())) {
            setParameters(stmt, pq.params());
            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        }
    }

    // ─── Async Execution ───────────────────────────────────────

    public CompletableFuture<List<Map<String, Object>>> executeAsync(ConnectionPool pool) {
        return CompletableFuture.supplyAsync(() -> {
            try { return execute(pool); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public <T> CompletableFuture<List<T>> executeAsync(ConnectionPool pool, Function<Map<String, Object>, T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            try { return execute(pool, mapper); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Optional<Map<String, Object>>> executeFirstAsync(ConnectionPool pool) {
        return CompletableFuture.supplyAsync(() -> {
            try { return executeFirst(pool); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public <T> CompletableFuture<Optional<T>> executeFirstAsync(ConnectionPool pool, Function<Map<String, Object>, T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            try { return executeFirst(pool, mapper); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    public CompletableFuture<Integer> executeUpdateAsync(ConnectionPool pool) {
        return CompletableFuture.supplyAsync(() -> {
            try { return executeUpdate(pool); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    // ─── Batch Execution ───────────────────────────────────────

    /**
     * Execute a batch insert for multiple rows.
     */
    public static CompletableFuture<int[]> batchInsert(ConnectionPool pool, String table,
                                                        List<String> columns, List<List<Object>> rows) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (" +
                    "?,".repeat(columns.size());
            sql = sql.substring(0, sql.length() - 1) + ")";
            try (Connection conn = pool.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                for (List<Object> row : rows) {
                    setParameters(stmt, row);
                    stmt.addBatch();
                }
                int[] results = stmt.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
                return results;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Execute a batch upsert.
     */
    public static CompletableFuture<int[]> batchUpsert(ConnectionPool pool, String table,
                                                        List<String> columns, List<String> conflictColumns,
                                                        List<List<Object>> rows) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES (");
            sql.append("?,".repeat(columns.size()));
            sql.setLength(sql.length() - 1);
            sql.append(") ON CONFLICT (").append(String.join(", ", conflictColumns)).append(") DO UPDATE SET ");
            columns.stream()
                    .filter(col -> !conflictColumns.contains(col))
                    .forEach(col -> sql.append(col).append(" = EXCLUDED.").append(col).append(", "));
            sql.setLength(sql.length() - 2);

            try (Connection conn = pool.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                conn.setAutoCommit(false);
                for (List<Object> row : rows) {
                    setParameters(stmt, row);
                    stmt.addBatch();
                }
                int[] results = stmt.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
                return results;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    // ─── Helpers ───────────────────────────────────────────────

    private static void setParameters(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param == null) {
                stmt.setNull(i + 1, Types.NULL);
            } else if (param instanceof String s) {
                stmt.setString(i + 1, s);
            } else if (param instanceof Integer n) {
                stmt.setInt(i + 1, n);
            } else if (param instanceof Long n) {
                stmt.setLong(i + 1, n);
            } else if (param instanceof Double n) {
                stmt.setDouble(i + 1, n);
            } else if (param instanceof Float n) {
                stmt.setFloat(i + 1, n);
            } else if (param instanceof Boolean b) {
                stmt.setBoolean(i + 1, b);
            } else if (param instanceof java.util.UUID uuid) {
                stmt.setObject(i + 1, uuid);
            } else if (param instanceof Timestamp ts) {
                stmt.setTimestamp(i + 1, ts);
            } else if (param instanceof byte[] bytes) {
                stmt.setBytes(i + 1, bytes);
            } else {
                stmt.setObject(i + 1, param);
            }
        }
    }

    private static List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> results = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            results.add(row);
        }
        return results;
    }

    // ─── Inner Classes ─────────────────────────────────────────

    public record PreparedQuery(String sql, List<Object> params) {}

    private record WhereClause(String column, String operator, Object value, String connector) {}
}
