package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;

public class SqliteRSignatureCacheManager extends RSignatureCacheManager {
    private static final Logger LOG = Logger.getInstance(SqliteRSignatureCacheManager.class.getName());
    private static final String DB_PATH = "/home/user/sqlite/MyDB.db";
    private static final RSignatureCacheManager INSTANCE = new SqliteRSignatureCacheManager(DB_PATH);

    private final Connection myConnection;

    public static RSignatureCacheManager getInstance() {
//        TODO?: return ServiceManager.getService(project, SqliteRSignatureCacheManager.class);
        return INSTANCE;
    }

    @Nullable
    private static Connection connect(@NotNull final String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            return DriverManager.getConnection(String.format("jdbc:sqlite:%s", path));
        } catch (final ClassNotFoundException | SQLException e) {
            LOG.info(e);
        }

        return null;
    }

    private SqliteRSignatureCacheManager(@NotNull final String db_path) {
        myConnection = connect(db_path);
    }

    @Override
    @Nullable
    public String findReturnTypeNameBySignature(@NotNull final RSignature signature) {
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT return_type_name FROM signatures WHERE " +
                                             "method_name = '%s' AND receiver_name = '%s' AND args_type_name = '%s';",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()));
            final ResultSet returnTypeNames = statement.executeQuery(sql);
            if (returnTypeNames.next()) {
                return returnTypeNames.getString(1);
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return null;
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName) {
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("INSERT OR REPLACE INTO signatures values('%s', '%s', '%s', '%s');",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()));
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            LOG.info(e);
        }
    }

    @Override
    public void clearCache() {
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = "DELETE FROM signatures;";
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            LOG.info(e);
        }
    }
}
