package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.io.StringRef;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo;
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.Visibility;

import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

class SqliteRSignatureCacheManager extends RSignatureCacheManager {
    @NotNull
    private static final Logger LOG = Logger.getInstance(SqliteRSignatureCacheManager.class.getName());

    @Nullable
    private static RSignatureCacheManager ourInstance;

    @NotNull
    private final Connection myConnection;

    @Nullable
    static RSignatureCacheManager getInstance() {
        if (ourInstance == null) {
            try {
                final URL dbURL = SqliteRSignatureCacheManager.class.getClassLoader().getResource("CallStat.db");
                if (dbURL != null) {
                    ourInstance = new SqliteRSignatureCacheManager(dbURL.getPath());
                }
            } catch (ClassNotFoundException | SQLException e) {
                LOG.info(e);
                return null;
            }
        }

        return ourInstance;
    }

    private SqliteRSignatureCacheManager(@NotNull final String dbPath) throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        myConnection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbPath));
    }

    @NotNull
    @Override
    public List<String> findReturnTypeNamesBySignature(@Nullable final Module module, @NotNull final RSignature signature) {
        final List<String> resultReturnTypeNames = new ArrayList<>();
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT return_type_name, gem_name, gem_version FROM signatures WHERE " +
                                             "method_name = '%s' AND receiver_name = '%s' AND args_type_name = '%s';",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()));
            final ResultSet rs = statement.executeQuery(sql);
            if (rs.next()) {
                final String moduleGemVersion = getGemVersionByName(module, rs.getString("gem_name"));
                final List<String> gemVersions = new ArrayList<>();
                final List<String> returnTypeNames = new ArrayList<>();
                do {
                    gemVersions.add(rs.getString("gem_version"));
                    returnTypeNames.add(rs.getString("return_type_name"));
                } while (rs.next());

                final String closestGemVersion = getClosestGemVersion(moduleGemVersion, gemVersions);
                for (int i = 0; i < gemVersions.size(); i++) {
                    if (gemVersions.get(i).equals(closestGemVersion)) {
                        resultReturnTypeNames.add(returnTypeNames.get(i));
                    }
                }
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return resultReturnTypeNames;
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName,
                                @NotNull final String gemName, @NotNull final String gemVersion) {
        try (final Statement statement = myConnection.createStatement()) {
            final String argsInfoSerialized = signature.getArgsInfo().stream()
                    .map(argInfo -> argInfo.getName() + "," + getRubyArgTypeRepresentation(argInfo.getType()))
                    .collect(Collectors.joining(";"));
            final String sql = String.format("INSERT OR REPLACE INTO signatures " +
                                             "values('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                                             returnTypeName, gemName, gemVersion, signature.getVisibility());
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

    @NotNull
    @Override
    public List<ArgumentInfo> getMethodArgsInfo(@NotNull final String methodName, @Nullable String receiverName) {
         try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT args_info FROM signatures " +
                                             "WHERE method_name = '%s' AND receiver_name = '%s';",
                                             methodName, receiverName);
            final ResultSet signatures = statement.executeQuery(sql);
            if (signatures.next()) {
                return parseArgsInfo(signatures.getString("args_info"));
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOG.info(e);
        }

        return ContainerUtilRt.emptyList();
    }

    @NotNull
    @Override
    protected Set<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName) {
        final Set<RSignature> receiverMethodSignatures = new HashSet<>();

        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT method_name, visibility, args_type_name, args_info FROM signatures " +
                                             "WHERE receiver_name = '%s';", receiverName);
            final ResultSet signatures = statement.executeQuery(sql);
            while (signatures.next()) {
                final String methodName = signatures.getString("method_name");
                final Visibility visibility = Visibility.valueOf(signatures.getString("visibility"));
                final List<ArgumentInfo> argsInfo = parseArgsInfo(signatures.getString("args_info"));
                final List<String> argsTypeName = StringUtil.splitHonorQuotes(signatures.getString("args_type_name"), ';');
                final RSignature signature = new RSignature(methodName, receiverName, visibility, argsInfo, argsTypeName);
                receiverMethodSignatures.add(signature);
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOG.info(e);
        }

        return receiverMethodSignatures;
    }

    @NotNull
    private static String getGemVersionByName(@Nullable final Module module, @NotNull final String gemName) {
        if (module != null && !gemName.isEmpty()) {
            final GemInfo gemInfo = GemSearchUtil.findGemEx(module, gemName);
            if (gemInfo != null) {
                return StringUtil.notNullize(gemInfo.getRealVersion());
            }
        }

        return "";
    }

    @NotNull
    private static List<ArgumentInfo> parseArgsInfo(@NotNull final String argsInfoSerialized) {
        try {
            return StringUtil.splitHonorQuotes(argsInfoSerialized, ';').stream()
                    .map(argInfo -> StringUtil.splitHonorQuotes(argInfo, ','))
                    .map(argInfo -> new ArgumentInfo(StringRef.fromString(argInfo.get(1)),
                                                     getArgTypeByRubyRepresentation(argInfo.get(0))))
                    .collect(Collectors.toList());
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @NotNull
    private String getClosestGemVersion(@NotNull final String gemVersion, @NotNull final List<String> gemVersions) {
        final NavigableSet<String> sortedSet = new TreeSet<>(VersionComparatorUtil.COMPARATOR);
        sortedSet.addAll(gemVersions);

        final String upperBound = sortedSet.ceiling(gemVersion);
        final String lowerBound = sortedSet.floor(gemVersion);
        if (upperBound == null) {
            return lowerBound;
        } else if (lowerBound == null) {
            return upperBound;
        } else if (upperBound.equals(lowerBound)) {
            return upperBound;
        } else if (firstStringCloser(gemVersion, upperBound, lowerBound)) {
            return upperBound;
        } else {
            return lowerBound;
        }
    }

    private boolean firstStringCloser(@NotNull final String gemVersion,
                                      @NotNull final String firstVersion, @NotNull final String secondVersion) {
        final int lcpLengthFirst = longestCommonPrefixLength(gemVersion, firstVersion);
        final int lcpLengthSecond = longestCommonPrefixLength(gemVersion, secondVersion);
        return (lcpLengthFirst > lcpLengthSecond || lcpLengthFirst > 0 && lcpLengthFirst == lcpLengthSecond &&
                Math.abs(gemVersion.charAt(lcpLengthFirst) - firstVersion.charAt(lcpLengthFirst)) <
                Math.abs(gemVersion.charAt(lcpLengthFirst) - secondVersion.charAt(lcpLengthSecond)));
    }

    private static int longestCommonPrefixLength(@NotNull final String str1, @NotNull final String str2) {
        final int minLength = Math.min(str1.length(), str2.length());
        for (int i = 0; i < minLength; i++) {
            if (str1.charAt(i) != str2.charAt(i)) {
                return i;
            }
        }

        return minLength;
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
            case PREDEFINED:
                return "opt";
            default:
                throw new IllegalArgumentException();
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
            case "opt":
            case "key":
            case "keyreq":
                return ArgumentInfo.Type.PREDEFINED;
            default:
                throw new IllegalArgumentException();
        }
    }
}
