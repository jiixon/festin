package com.festin.app.adapter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
public class DatabaseTestAdapter {

    @Autowired
    private DataSource dataSource;

    public boolean canConnect() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    public String getDatabaseName() {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get database name", e);
        }
    }
}