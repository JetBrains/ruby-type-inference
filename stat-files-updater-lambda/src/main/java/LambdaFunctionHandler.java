import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import signature.RestrictedParameterInfo;
import signature.RestrictedRSignature;
import signature.RestrictedVisibility;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class LambdaFunctionHandler implements RequestHandler<S3Event, Object> {
    @NotNull
    private final String EXPORT_BUCKET_NAME = "ruby-type-stat-export";
    @NotNull
    private final AmazonS3 myClient = new AmazonS3Client();

    @Override
    public Object handleRequest(@NotNull final S3Event event, @NotNull final Context context) {
        final LambdaLogger logger = context.getLogger();
        try {
            final List<Pair<String, String>> gemNamesAndVersions = getGemNamesAndVersions();
            for (final Pair<String, String> gemNameAndVersion : gemNamesAndVersions) {
                final List<RestrictedRSignature> signaturesFromDB = getSignaturesFromDB(gemNameAndVersion);
                final String statFileName = String.format("%s-%s.json", gemNameAndVersion.getKey(),
                                                                        gemNameAndVersion.getValue());
                final List<RestrictedRSignature> signaturesFromStatFile =
                        myClient.doesObjectExist(EXPORT_BUCKET_NAME, statFileName)
                                ? getSignaturesFromStatFile(EXPORT_BUCKET_NAME, statFileName)
                                : new ArrayList<>();

                if (!(new HashSet<>(signaturesFromDB)).equals(new HashSet<>(signaturesFromStatFile))) {
                    putSignaturesToServer(signaturesFromDB, statFileName);
                }
            }
        } catch (IOException | SQLException | ClassNotFoundException e) {
            logger.log(e.toString());
            return "FAIL";
        }

        return "OK";
    }

    @NotNull
    private List<RestrictedRSignature> getSignaturesFromDB(@NotNull final Pair<String, String> gemNameAndVersion)
            throws SQLException, ClassNotFoundException {
        final String sql = String.format("SELECT * FROM rsignature WHERE gem_name = '%s' AND gem_version = '%s'",
                                         gemNameAndVersion.getKey(), gemNameAndVersion.getValue());
        return executeQuery(sql);
    }

    @NotNull
    private List<RestrictedRSignature> getSignaturesFromStatFile(@NotNull final String bucketName,
                                                                 @NotNull final String statFileName)
            throws IOException {
        final GetObjectRequest request = new GetObjectRequest(bucketName, statFileName);
        final S3Object s3object = myClient.getObject(request);
        final Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()))) {
            return gson.fromJson(reader, new TypeToken<List<RestrictedRSignature>>() {}.getType());
        }
    }

    @NotNull
    private List<Pair<String, String>> getGemNamesAndVersions() throws SQLException, ClassNotFoundException {
        final String sql = "SELECT DISTINCT gem_name, gem_version FROM rsignature " +
                           "WHERE gem_name <> '' AND gem_version <> ''";
        final List<Pair<String, String>> gemAndVersions = new ArrayList<>();
        final Connection connection = getConnectionToDB();
        try (final Statement statement = connection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                gemAndVersions.add(new Pair<>(resultSet.getString("gem_name"),
                                              resultSet.getString("gem_version")));
            }
        }

        return gemAndVersions;
    }

    private void putSignaturesToServer(@NotNull final List<RestrictedRSignature> signatures,
                                       @NotNull final String statFileName) {
        final Gson gson = new GsonBuilder().create();
        final String json = gson.toJson(signatures);
        final InputStream jsonStream = new ByteArrayInputStream(json.getBytes());
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");
        myClient.putObject(EXPORT_BUCKET_NAME, statFileName, jsonStream, metadata);
    }

    @NotNull
    private List<RestrictedRSignature> executeQuery(@NotNull final String sql) throws SQLException, ClassNotFoundException {
        final List<RestrictedRSignature> signatures = new ArrayList<>();
        final Connection connection = getConnectionToDB();
        try (final Statement statement = connection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                final RestrictedRSignature signature = new RestrictedRSignature(
                        resultSet.getString("method_name"),
                        resultSet.getString("receiver_name"),
                        RestrictedVisibility.valueOf(resultSet.getString("visibility")),
                        parseArgsInfo(resultSet.getString("args_info")),
                        Arrays.asList(resultSet.getString("args_type_name").split(";")),
                        resultSet.getString("gem_name"),
                        resultSet.getString("gem_version"),
                        resultSet.getString("return_type_name"),
                        false);
                signatures.add(signature);
            }
        }

        return signatures;
    }

    @NotNull
    private static List<RestrictedParameterInfo> parseArgsInfo(@NotNull final String argsInfoSerialized) {
        return Arrays.stream(argsInfoSerialized.split(";"))
                .map(argInfo -> Arrays.asList(argInfo.split(",")))
                .filter(list -> list.size() == 3)
                .map(argInfo -> new RestrictedParameterInfo(argInfo.get(1),
                        RestrictedParameterInfo.Type.valueOf(argInfo.get(0).toUpperCase()),
                        argInfo.get(2)))
                .collect(Collectors.toList());
    }

    @NotNull
    private static Connection getConnectionToDB() throws SQLException, ClassNotFoundException {
        final String host ="typestatdbinstance.ccmaoqa8spde.eu-central-1.rds.amazonaws.com";
        final String port = "5432";
        final String user = "typestatdbuser";
        final String password = "typestatdbuserpassword";
        final String name = "typestatdb";
        final String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, name);
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(url, user, password);
    }
}