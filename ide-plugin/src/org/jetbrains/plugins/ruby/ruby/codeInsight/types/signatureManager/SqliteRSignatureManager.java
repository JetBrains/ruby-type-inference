package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.PluginResourceUtil;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.ClassModuleSymbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.CoreTypes;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.graph.RSignatureDAG;
import org.jetbrains.ruby.codeInsight.types.signature.*;

import java.io.FileNotFoundException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SqliteRSignatureManager extends RSignatureManager {
    @NotNull
    private static final Logger LOG = Logger.getInstance(SqliteRSignatureManager.class.getName());

    @Nullable
    private static SqliteRSignatureManager ourInstance;

    @NotNull
    private final Connection myConnection;

    @NotNull
    public static SqliteRSignatureManager getInstance()
            throws SQLException, ClassNotFoundException, FileNotFoundException {
        if (ourInstance == null) {
            final String dbPath = PluginResourceUtil.getPluginResourcesPath() + "CallStat.db";
            ourInstance = new SqliteRSignatureManager(dbPath);
        }

        return ourInstance;
    }

    private SqliteRSignatureManager(@NotNull final String dbPath) throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        myConnection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", dbPath));
    }

    @NotNull
    @Override
    public List<String> findReturnTypeNamesBySignature(@NotNull final Project project, @Nullable final Module module,
                                                       @NotNull final RSignature signature) {
        final MethodInfo methodInfo = signature.getMethodInfo();
        final String sql = String.format("SELECT * FROM rsignature WHERE method_name = '%s' AND receiver_name = '%s';",
                methodInfo.getName(), methodInfo.getClassInfo().getClassFQN());
        final List<RSignature> signatures = executeQuery(sql);
        final List<Pair<RSignature, Integer>> signaturesAndDistances = signatures.stream()
                .map(sign -> Pair.create(sign, RSignatureDAG.getArgsTypeNamesDistance(project, signature.getArgsTypeName(),
                                                                                      sign.getArgsTypeName())))
                .collect(Collectors.toList());

        signaturesAndDistances.removeIf(signAndFist -> signAndFist.getSecond() == null);
        if (signaturesAndDistances.isEmpty()) {
            return ContainerUtilRt.emptyList();
        }

        if (checkIfOnlyOneUniqueReturnTypeName(signaturesAndDistances)) {
            return new ArrayList<String>() {{
                add(signaturesAndDistances.get(0).getFirst().getReturnTypeName());
            }};
        }

        final String gemName = signaturesAndDistances.get(0).getFirst().getGemInfo().getName();
        final String moduleGemVersion = getGemVersionByName(module, gemName);
        filterSignaturesByModuleGemVersion(moduleGemVersion, signaturesAndDistances);

        if (checkIfOnlyOneUniqueReturnTypeName(signaturesAndDistances)) {
            return new ArrayList<String>() {{
                add(signaturesAndDistances.get(0).getFirst().getReturnTypeName());
            }};
        }

        final int minDistance = signaturesAndDistances.stream()
                .mapToInt(pair -> pair.getSecond())
                .min()
                .getAsInt();
        signaturesAndDistances.removeIf(signAndDist -> signAndDist.getSecond() > minDistance);
        return signaturesAndDistances.stream()
                .map(pair -> pair.getFirst().getReturnTypeName())
                .collect(Collectors.toList());
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature) {
        final String argsInfoSerialized = signature.getArgsInfo().stream()
                .map(argInfo -> String.join(",", argInfo.getType().toString().toLowerCase(), argInfo.getName(),
                                                 argInfo.getDefaultValueTypeName()))
                .collect(Collectors.joining(";"));
        final MethodInfo methodInfo = signature.getMethodInfo();
        final String sql = String.format("INSERT OR REPLACE INTO rsignature " +
                                         "values('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                methodInfo.getName(), methodInfo.getClassInfo().getClassFQN(),
                                         String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                signature.getReturnTypeName(), signature.getGemInfo().getName(), signature.getGemInfo().getVersion(),
                methodInfo.getVisibility());
        executeUpdate(sql);
    }

    @Override
    public void deleteSignature(@NotNull final RSignature signature) {
        final String sql = String.format("DELETE FROM rsignature WHERE args_type_name = '%s' " +
                                         "AND method_name = '%s' AND receiver_name = '%s' " +
                                         "AND gem_name = '%s' AND gem_version = '%s';",
                                         String.join(";", signature.getArgsTypeName()),
                signature.getMethodInfo().getName(), signature.getMethodInfo().getClassInfo().getClassFQN(),
                signature.getGemInfo().getName(), signature.getGemInfo().getVersion());
        executeUpdate(sql);
    }

    public void deleteSimilarSignatures(@NotNull final RSignature signature) {
        final String sql = String.format("DELETE FROM rsignature WHERE method_name = '%s' AND receiver_name = '%s' " +
                        "AND gem_name = '%s' AND gem_version = '%s';", signature.getMethodInfo().getName(),
                signature.getMethodInfo().getClassInfo().getClassFQN(), signature.getGemInfo().getName(), signature.getGemInfo().getVersion());
        executeUpdate(sql);
    }

    @NotNull
    public List<RSignature> getSimilarSignatures(@NotNull final RSignature signature) {
        final String sql = String.format("SELECT * FROM rsignature WHERE method_name = '%s' AND receiver_name = '%s' " +
                        "AND gem_name = '%s' AND gem_version = '%s';", signature.getMethodInfo().getName(),
                signature.getMethodInfo().getClassInfo().getClassFQN(), signature.getGemInfo().getName(), signature.getGemInfo().getVersion());
        return executeQuery(sql);
    }

    @NotNull
    public List<RSignature> getLocalSignatures() {
        final String sql = "SELECT * FROM rsignature WHERE is_local = true AND gem_name <> '' AND gem_version <> '';";
        return executeQuery(sql);
    }

    @Override
    public void compact(@NotNull final Project project) {
        mergeRecordsWithSameSignatureButDifferentReturnTypeNames(project);
        mergeRecordsWithDifferentSignaturesButSameReturnTypeName(project);
        // TODO: infer code contracts
    }

    @Override
    public void clear() {
        final String sql = "DELETE FROM rsignature;";
        executeUpdate(sql);
    }

    @Override
    public long getStatFileLastModified(@NotNull final String gemName, @NotNull final String gemVersion) {
        final String sql = String.format("SELECT last_modified FROM stat_file " +
                                         "WHERE gem_name = '%s' AND gem_version = '%s';",  gemName, gemVersion);
        try (final Statement statement = myConnection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            if (resultSet.next()) {
                return resultSet.getLong("last_modified");
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return 0;
    }

    public void setStatFileLastModified(@NotNull final String gemName, @NotNull final String gemVersion,
                                        final long lastModified) {
        final String sql = String.format("INSERT OR REPLACE INTO stat_file values('%s', '%s', %d);",
                                         gemName, gemVersion, lastModified);
        executeUpdate(sql);
    }

    @NotNull
    @Override
    public List<ParameterInfo> getMethodArgsInfo(@NotNull final String methodName, @Nullable final String receiverName) {
        final String sql = String.format("SELECT args_info FROM rsignature WHERE method_name = '%s' AND receiver_name = '%s';",
                                         methodName, receiverName);
        try (final Statement statement = myConnection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            if (resultSet.next()) {
                return parseArgsInfo(resultSet.getString("args_info"));
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOG.info(e);
        }

        return ContainerUtilRt.emptyList();
    }

    @NotNull
    @Override
    protected Set<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName) {
        final String sql = String.format("SELECT * FROM rsignature WHERE receiver_name = '%s';", receiverName);
        final List<RSignature> signatures = executeQuery(sql);
        return new HashSet<>(signatures);
    }

    @NotNull
    private static String getGemVersionByName(@Nullable final Module module, @NotNull final String gemName) {
        if (module != null && !gemName.isEmpty()) {
            final org.jetbrains.plugins.ruby.gem.GemInfo gemInfo = GemSearchUtil.findGemEx(module, gemName);
            if (gemInfo != null) {
                return StringUtil.notNullize(gemInfo.getRealVersion());
            }
        }

        return "";
    }

    @NotNull
    private static List<ParameterInfo> parseArgsInfo(@NotNull final String argsInfoSerialized) {
        try {
            return StringUtil.splitHonorQuotes(argsInfoSerialized, ';').stream()
                    .map(argInfo -> StringUtil.splitHonorQuotes(argInfo, ','))
                    .map(argInfo -> new ParameterInfo(argInfo.get(1),
                                                      ParameterInfo.Type.valueOf(argInfo.get(0).toUpperCase()),
                                                      argInfo.get(2)))
                    .collect(Collectors.toList());
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static boolean checkIfOnlyOneUniqueReturnTypeName(@NotNull final List<Pair<RSignature, Integer>>
                                                              signaturesAndReturnTypeNames) {
        final long countOfDistinctReturnTypeNames = signaturesAndReturnTypeNames.stream()
                .map(pair -> pair.getFirst().getReturnTypeName())
                .distinct()
                .count();
        return countOfDistinctReturnTypeNames == 1;
    }

    private static void filterSignaturesByModuleGemVersion(@NotNull final String moduleGemVersion,
                                                           @NotNull final List<Pair<RSignature, Integer>> signaturesAndDistances) {
        final List<String> gemVersions = signaturesAndDistances.stream()
                .map(pair -> pair.getFirst())
                .map(signature -> signature.getGemInfo().getVersion())
                .collect(Collectors.toList());
        final String closestGemVersion = getClosestGemVersion(moduleGemVersion, gemVersions);
        signaturesAndDistances.removeIf(signAndDist -> {
            final String gemVersion = signAndDist.getFirst().getGemInfo().getVersion();
            return !gemVersion.equals(closestGemVersion);
        });
    }

    private void mergeRecordsWithSameSignatureButDifferentReturnTypeNames(@NotNull final Project project) {
        final String sql = "SELECT DISTINCT * FROM rsignature " +
                           "GROUP BY method_name, receiver_name, args_type_name, gem_name, gem_version " +
                           "HAVING COUNT(return_type_name) > 1;";
        final List<RSignature> signatures = executeQuery(sql);
        for (final RSignature signature : signatures) {
            final Set<String> returnTypeNames = getReturnTypeNamesBySignature(signature);
            final Set<ClassModuleSymbol> returnTypeSymbols = returnTypeNames.stream()
                    .map(returnTypeName -> SymbolUtil.findClassOrModule(project, returnTypeName))
                    .map(returnTypeSymbol -> (ClassModuleSymbol) returnTypeSymbol)
                    .collect(Collectors.toSet());
            String leastCommonSuperclassFQN = null;
            if (!returnTypeSymbols.contains(null)) {
                final ClassModuleSymbol leastCommonSuperclass = RSignatureDAG.getLeastCommonSuperclass(returnTypeSymbols);
                if (leastCommonSuperclass != null) {
                    leastCommonSuperclassFQN = leastCommonSuperclass.getFQN().getFullPath();
                }
            }

            signature.setReturnTypeName(StringUtil.notNullize(leastCommonSuperclassFQN, CoreTypes.Object));
            deleteSignature(signature);
            recordSignature(signature);
        }
    }

    private void mergeRecordsWithDifferentSignaturesButSameReturnTypeName(@NotNull final Project project) {
        String sql = "SELECT DISTINCT * FROM rsignature GROUP BY method_name, receiver_name, gem_name, gem_version " +
                     "HAVING COUNT(args_type_name) > 1;";
        final List<RSignature> groups = executeQuery(sql);
        for (final RSignature signature : groups) {
            final List<RSignature> signatures = getSimilarSignatures(signature);
            final RSignatureDAG dag = new RSignatureDAG(project, signature.getArgsTypeName().size());
            dag.addAll(signatures);
            deleteSimilarSignatures(signature);
            dag.depthFirstSearch(this::recordSignature);
        }
    }

    @NotNull
    private Set<String> getReturnTypeNamesBySignature(@NotNull final RSignature signature) {
        final String sql = String.format("SELECT * FROM rsignature " +
                                         "WHERE method_name = '%s' AND receiver_name = '%s' " +
                                         "AND args_type_name = '%s' AND gem_name = '%s' AND gem_version = '%s';",
                signature.getMethodInfo().getName(), signature.getMethodInfo().getClassInfo().getClassFQN(),
                                         String.join(";", signature.getArgsTypeName()),
                signature.getGemInfo().getName(), signature.getGemInfo().getVersion());
        final List<RSignature> signatures = executeQuery(sql);
        return signatures.stream()
                .map(RSignature::getReturnTypeName)
                .collect(Collectors.toSet());
    }

    private void executeUpdate(@NotNull final String sql) {
        try (final Statement statement = myConnection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            LOG.info(e);
        }
    }

    @NotNull
    private List<RSignature> executeQuery(@NotNull final String sql) {
        final List<RSignature> signatures = new ArrayList<>();
        try (final Statement statement = myConnection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                final RSignature signature = new RSignatureBuilder(resultSet.getString("method_name"))
                        .setReceiverName(resultSet.getString("receiver_name"))
                        .setVisibility(RVisibility.valueOf(resultSet.getString("visibility")))
                        .setArgsInfo(parseArgsInfo(resultSet.getString("args_info")))
                        .setArgsTypeName(StringUtil.splitHonorQuotes(resultSet.getString("args_type_name"), ';'))
                        .setGemInfo(GemInfoKt.GemInfo(resultSet.getString("gem_name"),
                                resultSet.getString("gem_version")))
                        .setReturnTypeName(resultSet.getString("return_type_name"))
                        .build();
                signatures.add(signature);
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOG.info(e);
            return ContainerUtilRt.emptyList();
        }

        return signatures;
    }
}
