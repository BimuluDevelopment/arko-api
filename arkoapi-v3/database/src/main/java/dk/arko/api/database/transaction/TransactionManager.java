package dk.arko.api.database.transaction;

import dk.arko.api.database.pool.ConnectionPool;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Transaction manager for safe multi-statement database operations.
 * Supports nested transactions via savepoints, isolation levels, and async execution.
 *
 * Usage:
 *   TransactionManager tx = new TransactionManager(pool);
 *   tx.executeAsync(conn -> {
 *       // Multiple operations in one transaction
 *       stmt1.execute();
 *       stmt2.execute();
 *       // Auto-committed on success, auto-rolled-back on exception
 *   });
 */
public class TransactionManager {

    private final ConnectionPool pool;

    public TransactionManager(ConnectionPool pool) {
        this.pool = pool;
    }

    /**
     * Execute operations in a transaction. Auto-commits on success, rolls back on failure.
     */
    public void execute(Consumer<Connection> operations) throws SQLException {
        try (Connection conn = pool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                operations.accept(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e instanceof SQLException ? (SQLException) e :
                        new SQLException("Transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Execute operations with a return value.
     */
    public <T> T executeWithResult(Function<Connection, T> operations) throws SQLException {
        try (Connection conn = pool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = operations.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw e instanceof SQLException ? (SQLException) e :
                        new SQLException("Transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Execute async transaction.
     */
    public CompletableFuture<Void> executeAsync(Consumer<Connection> operations) {
        return CompletableFuture.runAsync(() -> {
            try { execute(operations); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    /**
     * Execute async transaction with result.
     */
    public <T> CompletableFuture<T> executeWithResultAsync(Function<Connection, T> operations) {
        return CompletableFuture.supplyAsync(() -> {
            try { return executeWithResult(operations); }
            catch (SQLException e) { throw new RuntimeException(e); }
        });
    }

    /**
     * Execute with a specific isolation level.
     */
    public void execute(int isolationLevel, Consumer<Connection> operations) throws SQLException {
        try (Connection conn = pool.getConnection()) {
            int original = conn.getTransactionIsolation();
            conn.setTransactionIsolation(isolationLevel);
            conn.setAutoCommit(false);
            try {
                operations.accept(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e instanceof SQLException ? (SQLException) e :
                        new SQLException("Transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
                conn.setTransactionIsolation(original);
            }
        }
    }

    /**
     * Execute with serializable isolation (strongest consistency).
     */
    public void executeSerializable(Consumer<Connection> operations) throws SQLException {
        execute(Connection.TRANSACTION_SERIALIZABLE, operations);
    }

    /**
     * Create a savepoint within a transaction for nested rollback.
     */
    public static Savepoint savepoint(Connection conn, String name) throws SQLException {
        return conn.setSavepoint(name);
    }

    /**
     * Rollback to a savepoint.
     */
    public static void rollbackTo(Connection conn, Savepoint savepoint) throws SQLException {
        conn.rollback(savepoint);
    }

    /**
     * Fluent transaction builder.
     */
    public TransactionBuilder builder() {
        return new TransactionBuilder(this);
    }

    public static class TransactionBuilder {
        private final TransactionManager manager;
        private int isolationLevel = -1;
        private boolean readOnly = false;

        TransactionBuilder(TransactionManager manager) {
            this.manager = manager;
        }

        public TransactionBuilder isolationLevel(int level) {
            this.isolationLevel = level;
            return this;
        }

        public TransactionBuilder serializable() {
            this.isolationLevel = Connection.TRANSACTION_SERIALIZABLE;
            return this;
        }

        public TransactionBuilder repeatableRead() {
            this.isolationLevel = Connection.TRANSACTION_REPEATABLE_READ;
            return this;
        }

        public TransactionBuilder readCommitted() {
            this.isolationLevel = Connection.TRANSACTION_READ_COMMITTED;
            return this;
        }

        public TransactionBuilder readOnly() {
            this.readOnly = true;
            return this;
        }

        public void execute(Consumer<Connection> operations) throws SQLException {
            if (isolationLevel >= 0) {
                manager.execute(isolationLevel, conn -> {
                    try {
                        if (readOnly) conn.setReadOnly(true);
                        operations.accept(conn);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
            } else {
                manager.execute(operations);
            }
        }

        public CompletableFuture<Void> executeAsync(Consumer<Connection> operations) {
            return CompletableFuture.runAsync(() -> {
                try { execute(operations); }
                catch (SQLException e) { throw new RuntimeException(e); }
            });
        }
    }
}
