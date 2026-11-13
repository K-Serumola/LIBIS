package bw.ac.bac.libis.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

public final class Database {

    private static DataSource dataSource;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        return lookup().getConnection();
    }

    private static synchronized DataSource lookup() throws SQLException {
        if (dataSource == null) {
            try {
                Context env = (Context) new InitialContext().lookup("java:comp/env");
                dataSource = (DataSource) env.lookup("jdbc/libis");
            } catch (NamingException e) {
                throw new SQLException("DataSource jdbc/libis not found", e);
            }
        }
        return dataSource;
    }
}