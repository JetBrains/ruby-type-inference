package org.jetbrains.plugins.ruby.ruby.codeInsight.types;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.util.GemSearchUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.SymbolUtil;
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.v2.ClassModuleSymbol;
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
    public List<String> findReturnTypeNamesBySignature(@NotNull final Project project, @Nullable final Module module,
                                                       @NotNull final RSignature signature) {
        final List<Pair<RSignature, String>> signaturesAndReturnTypeNames =
                getSignaturesAndReturnTypeNames(signature.getMethodName(), signature.getReceiverName());
        final List<Trinity<RSignature, String, Integer>> signaturesAndReturnTypeNamesAndDistances =
                signaturesAndReturnTypeNames.stream()
                        .map(pair -> Trinity.create(pair.getFirst(), pair.getSecond(),
                                                    calcArgsTypeNamesDistance(project, signature.getArgsTypeName(),
                                                                              pair.getFirst().getArgsTypeName())))
                        .collect(Collectors.toList());

        signaturesAndReturnTypeNamesAndDistances.removeIf(signAndRetTypeAndDist ->
                signAndRetTypeAndDist.getThird() == null);
        if (signaturesAndReturnTypeNamesAndDistances.isEmpty()) {
            return new ArrayList<>();
        }

        if (checkIfOnlyOneUniqueReturnTypeName(signaturesAndReturnTypeNamesAndDistances)) {
            return new ArrayList<String>() {{
                add(signaturesAndReturnTypeNamesAndDistances.get(0).getSecond());
            }};
        }

        final String gemName = signaturesAndReturnTypeNamesAndDistances.get(0).getFirst().getGemName();
        final String moduleGemVersion = getGemVersionByName(module, gemName);
        filterSignaturesAndReturnTypesByModuleGemVersion(moduleGemVersion, signaturesAndReturnTypeNamesAndDistances);

        if (checkIfOnlyOneUniqueReturnTypeName(signaturesAndReturnTypeNamesAndDistances)) {
            return new ArrayList<String>() {{
                add(signaturesAndReturnTypeNamesAndDistances.get(0).getSecond());
            }};
        }

        final int minDistance = signaturesAndReturnTypeNamesAndDistances.stream()
                .mapToInt(Trinity::getThird)
                .min()
                .getAsInt();
        signaturesAndReturnTypeNamesAndDistances.removeIf(signAndRetTypeAndDist ->
                signAndRetTypeAndDist.getThird() > minDistance);

        return signaturesAndReturnTypeNamesAndDistances.stream()
                .map(Trinity::getSecond)
                .collect(Collectors.toList());
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature, @NotNull final String returnTypeName) {
        try (final Statement statement = myConnection.createStatement()) {
            final String argsInfoSerialized = signature.getArgsInfo().stream()
                    .map(argInfo -> argInfo.getName() + "," + argInfo.getType() + "," + argInfo.getDefaultValueTypeName())
                    .collect(Collectors.joining(";"));
            final String sql = String.format("INSERT OR REPLACE INTO signatures " +
                                             "values('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s');",
                                             signature.getMethodName(), signature.getReceiverName(),
                                             String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                                             returnTypeName, signature.getGemName(), signature.getGemVersion(),
                                             signature.getVisibility());
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
    public List<ParameterInfo> getMethodArgsInfo(@NotNull final String methodName, @Nullable final String receiverName) {
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
            final String sql = String.format("SELECT * FROM signatures WHERE receiver_name = '%s';", receiverName);
            final ResultSet signatures = statement.executeQuery(sql);
            while (signatures.next()) {
                final RSignature signature = new RSignatureBuilder()
                        .setMethodName(signatures.getString("method_name"))
                        .setReceiverName(receiverName)
                        .setVisibility(Visibility.valueOf(signatures.getString("visibility")))
                        .setArgsInfo(parseArgsInfo(signatures.getString("args_info")))
                        .setArgsTypeName(StringUtil.splitHonorQuotes(signatures.getString("args_type_name"), ';'))
                        .build();
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

    @NotNull
    private static String getClosestGemVersion(@NotNull final String gemVersion, @NotNull final List<String> gemVersions) {
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

    private static boolean firstStringCloser(@NotNull final String gemVersion,
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

    @Nullable
    private static Integer calcArgsTypeNamesDistance(@NotNull final Project project,
                                                     @NotNull final List<String> from, @NotNull final List<String> to) {
        if (from.size() != to.size()) {
            return null;
        }

        Integer distanceBetweenAllArgs = 0;
        for (int i = 0; i < from.size(); i++) {
            final Symbol fromArgSymbol = SymbolUtil.findClassOrModule(project, from.get(i));
            final Symbol toArgSymbol = SymbolUtil.findClassOrModule(project, to.get(i));
            final Integer distanceBetweenTwoArgs = calcArgTypeSymbolsDistance((ClassModuleSymbol) fromArgSymbol,
                    (ClassModuleSymbol) toArgSymbol);
            if (distanceBetweenTwoArgs == null) {
                return null;
            } else {
                distanceBetweenAllArgs += distanceBetweenTwoArgs;
            }
        }

        return distanceBetweenAllArgs;
    }

    @Nullable
    private static Integer calcArgTypeSymbolsDistance(@Nullable final ClassModuleSymbol from,
                                                      @Nullable final ClassModuleSymbol to) {
        if (from == null || to == null) {
            return null;
        }

        int distance = 0;
        for (ClassModuleSymbol current = from; current != null; current = (ClassModuleSymbol) current.getSuperClassSymbol(null)) {
            if (current.equals(to)) {
                return distance;
            }

            distance += 1;
        }

        return null;
    }

    private static boolean checkIfOnlyOneUniqueReturnTypeName(List<Trinity<RSignature, String, Integer>> signaturesAndReturnTypeNamesAndDistances) {
        final long countOfDistinctReturnTypeNames = signaturesAndReturnTypeNamesAndDistances.stream()
                .map(Trinity::getSecond)
                .distinct()
                .count();
        return countOfDistinctReturnTypeNames == 1;
    }

    private static void filterSignaturesAndReturnTypesByModuleGemVersion(@NotNull final String moduleGemVersion,
                                                                         @NotNull final List<Trinity<RSignature, String, Integer>> signaturesAndReturnTypeNamesAndDistances) {
        final List<String> gemVersions = signaturesAndReturnTypeNamesAndDistances.stream()
                .map(Trinity::getFirst)
                .map(RSignature::getGemVersion)
                .collect(Collectors.toList());
        final String closestGemVersion = getClosestGemVersion(moduleGemVersion, gemVersions);
        signaturesAndReturnTypeNamesAndDistances.removeIf(signAndRetTypeAndDist -> {
            final String gemVersion = signAndRetTypeAndDist.getFirst().getGemVersion();
            return !gemVersion.equals(closestGemVersion);
        });
    }

    @NotNull
    private List<Pair<RSignature, String>> getSignaturesAndReturnTypeNames(@NotNull final String methodName,
                                                                           @NotNull final String receiverName) {
        final List<Pair<RSignature, String>> signaturesAndReturnTypeNames = new ArrayList<>();
        try (final Statement statement = myConnection.createStatement()) {
            final String sql = String.format("SELECT * FROM signatures WHERE method_name = '%s' AND receiver_name = '%s';",
                    methodName, receiverName);
            final ResultSet rs = statement.executeQuery(sql);
            if (rs.next()) {
                do {
                    final RSignature newSignature = new RSignatureBuilder()
                            .setMethodName(rs.getString("method_name"))
                            .setReceiverName(rs.getString("receiver_name"))
                            .setVisibility(Visibility.valueOf(rs.getString("visibility")))
                            .setArgsInfo(parseArgsInfo(rs.getString("args_info")))
                            .setArgsTypeName(StringUtil.splitHonorQuotes(rs.getString("args_type_name"), ';'))
                            .setGemName(rs.getString("gem_name"))
                            .setGemVersion(rs.getString("gem_version"))
                            .build();
                    final String newReturnTypeName  = rs.getString("return_type_name");
                    signaturesAndReturnTypeNames.add(Pair.create(newSignature, newReturnTypeName));
                } while (rs.next());
            }
        } catch (SQLException e) {
            LOG.info(e);
        }

        return signaturesAndReturnTypeNames;
    }
}
