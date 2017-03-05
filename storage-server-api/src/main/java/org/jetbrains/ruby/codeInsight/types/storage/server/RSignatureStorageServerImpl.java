package org.jetbrains.ruby.codeInsight.types.storage.server;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RSignatureStorageServerImpl extends RSignatureStorageServer {
    @NotNull
    private static final String EXPORT_BUCKET_NAME = "ruby-type-stat-export";
    @NotNull
    private static final String IMPORT_BUCKET_NAME = "ruby-type-stat-import";

    @NotNull
    private final AmazonS3 myClient = new AmazonS3Client();
    @Nullable
    private final Connection myConnection;

    public RSignatureStorageServerImpl() throws SQLException, ClassNotFoundException {
        myConnection = null;
    }

    public RSignatureStorageServerImpl(@NotNull final String host, @NotNull final String port,
                                       @NotNull final String login, @NotNull final String password,
                                       @NotNull final String dbName) throws SQLException, ClassNotFoundException {
        myConnection = getConnectionToStorage(host, port, login, password, dbName);
    }

    @Override
    @NotNull
    public List<Pair<String, String>> getGemNamesAndVersionsFromStorage() throws SQLException {
        if (myConnection == null) {
            throw new SQLException();
        }

        final String sql = "SELECT DISTINCT gem_name, gem_version FROM rsignature " +
                "WHERE gem_name <> '' AND gem_version <> ''";
        final List<Pair<String, String>> gemNamesAndVersions = new ArrayList<>();
        try (final Statement statement = myConnection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                gemNamesAndVersions.add(new Pair<>(resultSet.getString("gem_name"),
                        resultSet.getString("gem_version")));
            }
        }

        return gemNamesAndVersions;
    }

    @Override
    @NotNull
    public List<RSignature> getSignaturesFromStorage(@NotNull final String gemName, @NotNull final String gemVersion)
            throws SQLException, ClassNotFoundException {
        final String sql = String.format("SELECT * FROM rsignature WHERE gem_name = '%s' AND gem_version = '%s'",
                gemName, gemVersion);
        return executeQuery(sql);
    }

    @Override
    public void insertSignaturesToStorage(@NotNull final List<RSignature> signatures) throws SQLException {
        if (myConnection == null) {
            throw new SQLException();
        }

        final List<String> sqls = signatures.stream()
                .map(RSignatureStorageServerImpl::signatureToSqlString)
                .collect(Collectors.toList());
        try (final Statement statement = myConnection.createStatement()) {
            for (final String sql : sqls) {
                statement.execute(sql);
            }
        }
    }

    @Override
    public List<RSignature> getSignaturesFromStatFile(@NotNull final String statFileName,
                                                      final boolean fromExportBucket)
            throws IOException {
        if (myClient.doesObjectExist(fromExportBucket ? EXPORT_BUCKET_NAME : IMPORT_BUCKET_NAME, statFileName)) {
            final GetObjectRequest request = new GetObjectRequest(fromExportBucket ? EXPORT_BUCKET_NAME : IMPORT_BUCKET_NAME,
                    statFileName);
            final S3Object s3object = myClient.getObject(request);
            final Gson gson = new Gson();
            try (final BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()))) {
                return gson.fromJson(reader, new TypeToken<List<RSignature>>() {
                }.getType());
            }
        }

        return new ArrayList<>();
    }

    @Override
    public void insertSignaturesToStatFile(@NotNull final List<RSignature> signatures,
                                           @NotNull final String statFileName,
                                           final boolean toExportBucket) {
        final Gson gson = new GsonBuilder().create();
        final String json = gson.toJson(signatures);
        final InputStream jsonStream = new ByteArrayInputStream(json.getBytes());
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        myClient.putObject(toExportBucket ? EXPORT_BUCKET_NAME : IMPORT_BUCKET_NAME,
                statFileName, jsonStream, metadata);
    }

    @Override
    @NotNull
    public List<StatFileInfo> getStatFileInfos(final boolean fromExportBucket) {
        final List<StatFileInfo> statFileInfos = new ArrayList<>();
        final ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(fromExportBucket ? EXPORT_BUCKET_NAME : IMPORT_BUCKET_NAME);
        ListObjectsV2Result result;
        do {
            result = myClient.listObjectsV2(request);
            result.getObjectSummaries().forEach(s ->
                    statFileInfos.add(new StatFileInfo(s.getKey(), s.getLastModified().getTime())));
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());

        return statFileInfos;
    }

    @Override
    public void deleteStatFile(@NotNull final String statFileName, final boolean fromExportBucket) {
        myClient.deleteObject(fromExportBucket ? EXPORT_BUCKET_NAME : IMPORT_BUCKET_NAME, statFileName);
    }

    @NotNull
    private List<RSignature> executeQuery(@NotNull final String sql) throws SQLException, ClassNotFoundException {
        if (myConnection == null) {
            throw new SQLException();
        }

        final List<RSignature> signatures = new ArrayList<>();
        try (final Statement statement = myConnection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                final GemInfo gemInfo = GemInfoKt.GemInfo(resultSet.getString("gem_name"),
                        resultSet.getString("gem_version"));
                final RSignature signature = new RSignature(
                        MethodInfoKt.MethodInfo(ClassInfoKt.ClassInfo(gemInfo, resultSet.getString("receiver_name")),
                                resultSet.getString("method_name"),
                                RVisibility.valueOf(resultSet.getString("visibility"))),
                        parseArgsInfo(resultSet.getString("args_info")),
                        Arrays.asList(resultSet.getString("args_type_name").split(";")),
                        gemInfo,
                        resultSet.getString("return_type_name"),
                        false);
                signatures.add(signature);
            }
        }

        return signatures;
    }

    @NotNull
    private static List<ParameterInfo> parseArgsInfo(@NotNull final String argsInfoSerialized) {
        return Arrays.stream(argsInfoSerialized.split(";"))
                .map(argInfo -> Arrays.asList(argInfo.split(",")))
                .filter(list -> list.size() == 3)
                .map(argInfo -> new ParameterInfo(argInfo.get(1),
                        ParameterInfo.Type.valueOf(argInfo.get(0).toUpperCase()),
                        argInfo.get(2)))
                .collect(Collectors.toList());
    }

    @NotNull
    private static String signatureToSqlString(@NotNull final RSignature signature) {
        final String argsInfoSerialized = signature.getArgsInfo().stream()
                .map(argInfo -> String.join(",", argInfo.getType().toString().toLowerCase(), argInfo.getName(),
                        argInfo.getDefaultValueTypeName()))
                .collect(Collectors.joining(";"));

        final MethodInfo methodInfo = signature.getMethodInfo();
        return String.format("INSERT INTO rsignature values('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', FALSE) " +
                        "ON CONFLICT DO NOTHING;",
                methodInfo.getName(), methodInfo.getClassInfo().getClassFQN(),
                methodInfo.getVisibility(), String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                signature.getReturnTypeName(), signature.getGemInfo().getName(), signature.getGemInfo().getVersion());

    }

    @NotNull
    private static Connection getConnectionToStorage(@NotNull final String host, @NotNull final String port,
                                                     @NotNull final String login, @NotNull final String password,
                                                     @NotNull final String dbName)
            throws SQLException, ClassNotFoundException {
        final String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(url, login, password);
    }
}
