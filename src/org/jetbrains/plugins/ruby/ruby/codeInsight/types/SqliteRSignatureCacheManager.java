package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SqliteRSignatureCacheManager extends RSignatureCacheManager {
    private static final Logger LOG = Logger.getInstance(SqliteRSignatureCacheManager.class.getName());
    private static final String DB_PATH = "/home/user/sqlite/MyDB.db";
    private static final RSignatureCacheManager INSTANCE = new SqliteRSignatureCacheManager(DB_PATH);

    private final Connection myConnection;

    public static RSignatureCacheManager getInstance() {
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
                return returnTypeNames.getString("return_type_name");
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return null;
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName) {
        try (final Statement statement = myConnection.createStatement()) {
            final String argsInfoSerialized = signature.getArgsInfo().stream()
                    .map(argInfo -> argInfo.getName() + "," + getRubyArgTypeRepresentation(argInfo.getType()))
                    .collect(Collectors.joining(";"));
            final String sql = String.format("INSERT OR REPLACE INTO signatures values('%s', '%s', '%s', '%s', '%s');",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                                             returnTypeName);
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

    @Override
    @Nullable
    public List<ArgumentInfo> getMethodArgsInfo(@NotNull final String methodName, @Nullable String receiverName) {
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT args_info FROM signatures " +
                                             "WHERE method_name = '%s' AND receiver_name = '%s';",
                                             methodName, receiverName);
            final ResultSet signatures = statement.executeQuery(sql);
            if (signatures.next()) {
                return Arrays.stream(signatures.getString("args_info").split(";"))
                        .map(argInfo -> argInfo.split(","))
                        .filter(argInfo -> argInfo.length == 2)
                        .map(argInfo -> new ArgumentInfo(StringRef.fromString(argInfo[1]),
                                                         getArgTypeByRubyRepresentation(argInfo[0])))
                        .collect(Collectors.toList());
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return null;
    }

    @Override
    @NotNull
    protected List<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName) {
        final List<RSignature> receiverMethodSignatures = new ArrayList<>();

        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT method_name, args_type_name, args_info FROM signatures " +
                                             "WHERE receiver_name = '%s';", receiverName);
            final ResultSet signatures = statement.executeQuery(sql);
            while (signatures.next()) {
                String methodName = signatures.getString("method_name");
                List<String> argsTypeName = Arrays.asList(signatures.getString("args_type_name").split(";"));
                List<ArgumentInfo> argsInfo = Arrays.stream(signatures.getString("args_info").split(";"))
                        .map(argInfo -> argInfo.split(","))
                        .filter(argInfo -> argInfo.length == 2)
                        .map(argInfo -> new ArgumentInfo(StringRef.fromString(argInfo[1]),
                                                         getArgTypeByRubyRepresentation(argInfo[0])))
                        .collect(Collectors.toList());
                receiverMethodSignatures.add(new RSignature(methodName, receiverName, argsTypeName, argsInfo));
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return receiverMethodSignatures;
    }

    @NotNull
    private static String getRubyArgTypeRepresentation(@NotNull final ArgumentInfo.Type type) {
        switch (type) {
            case SIMPLE:
                return "req";
            case ARRAY:
                return "rest";
            case HASH:
                return "keyrest";
            case BLOCK:
                return "block";
            default:
                return "opt";
        }
    }

    @NotNull
    private static ArgumentInfo.Type getArgTypeByRubyRepresentation(@NotNull final String argTypeRepresentation) {
        switch (argTypeRepresentation) {
            case "req":
                return ArgumentInfo.Type.SIMPLE;
            case "rest":
                return ArgumentInfo.Type.ARRAY;
            case "keyrest":
                return ArgumentInfo.Type.HASH;
            case "block":
                return ArgumentInfo.Type.BLOCK;
            default:
                return ArgumentInfo.Type.PREDEFINED;
        }
    }
}
