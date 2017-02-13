import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ruby.runtime.signature.RestrictedRSignature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

public class LambdaFunctionHandler implements RequestHandler<S3Event, Object> {
    @NotNull
    private final AmazonS3 myClient = new AmazonS3Client();

    @Override
    public Object handleRequest(@NotNull final S3Event event, @NotNull final Context context) {
        final LambdaLogger logger = context.getLogger();
        logger.log("Input: " + event);

        final S3EventNotificationRecord record = event.getRecords().get(0);
        final String bucketName = record.getS3().getBucket().getName();
        final String statFileName = record.getS3().getObject().getKey();
        try {
            final List<RestrictedRSignature> signatures = getRestrictedSignaturesFromStatFile(bucketName, statFileName);
            insertSignaturesToStorage(signatures);
            myClient.deleteObject(bucketName, statFileName);
        } catch (IOException | SQLException | ClassNotFoundException e) {
            logger.log(e.toString());
            return "FAIL";
        }

        return "OK";
    }

    @NotNull
    private List<RestrictedRSignature> getRestrictedSignaturesFromStatFile(@NotNull final String bucketName,
                                                                           @NotNull final String statFileName)
            throws IOException {
        final GetObjectRequest request = new GetObjectRequest(bucketName, statFileName);
        final S3Object s3object = myClient.getObject(request);
        final Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()))) {
            return gson.fromJson(reader, new TypeToken<List<RestrictedRSignature>>() {}.getType());
        }
    }

    private static void insertSignaturesToStorage(@NotNull final List<RestrictedRSignature> signatures)
            throws SQLException, ClassNotFoundException {
        final List<String> sqls = signatures.stream()
                .map(LambdaFunctionHandler::signatureToSqlString)
                .collect(Collectors.toList());
        final Connection connection = getConnectionToDB();
        try (final Statement statement = connection.createStatement()) {
            for (final String sql : sqls) {
                statement.execute(sql);
            }
        }
    }

    @NotNull
    private static String signatureToSqlString(@NotNull final RestrictedRSignature signature) {
        final String argsInfoSerialized = signature.getArgsInfo().stream()
                .map(argInfo -> String.join(",", argInfo.getType().toString().toLowerCase(), argInfo.getName(),
                        argInfo.getDefaultValueTypeName()))
                .collect(Collectors.joining(";"));
        return String.format("INSERT INTO rsignature values('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s', FALSE) " +
                             "ON CONFLICT DO NOTHING;",  signature.getMethodName(), signature.getReceiverName(),
                signature.getVisibility(), String.join(";", signature.getArgsTypeName()), argsInfoSerialized,
                signature.getReturnTypeName(), signature.getGemName(), signature.getGemVersion());

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