package com.melita.orders.platform.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Runs a job on one instance at a time using a MySQL named lock ({@code GET_LOCK}).
 * <p>
 * The lock belongs to the database <em>connection</em>, not to the thread, so the same connection is
 * borrowed for the whole run and the lock is released on it. No transaction is held on that connection.
 * If the instance dies, MySQL closes the connection and frees the lock, so another instance takes over on
 * its next tick. The lock only avoids duplicate work: correctness never depends on it.
 */
public class NamedLock {

    private static final Logger log = LoggerFactory.getLogger(NamedLock.class);

    private final DataSource dataSource;

    public NamedLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** @return {@code true} if the lock was obtained and the job ran; {@code false} if another instance holds it. */
    public boolean runIfAvailable(String name, Runnable job) {
        try (Connection connection = dataSource.getConnection()) {
            if (!acquire(connection, name)) {
                return false;
            }
            try {
                job.run();
                return true;
            } finally {
                release(connection, name);
            }
        } catch (SQLException e) {
            log.warn("Could not use named lock {}: {}", name, e.getMessage());
            return false;
        }
    }

    private static boolean acquire(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select get_lock(?, 0)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private static void release(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select release_lock(?)")) {
            statement.setString(1, name);
            statement.executeQuery().close();
        }
    }
}
