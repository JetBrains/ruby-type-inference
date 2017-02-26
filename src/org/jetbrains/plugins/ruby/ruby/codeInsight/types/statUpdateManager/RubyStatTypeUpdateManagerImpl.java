package org.jetbrains.plugins.ruby.ruby.codeInsight.types.statUpdateManager;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.GemManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.RSignatureManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.SqliteRSignatureManager;
import ruby.codeInsight.types.signature.RSignature;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class RubyStatTypeUpdateManagerImpl extends RubyStatTypeUpdateManager {
    @NotNull
    private static final Logger LOG = Logger.getInstance(RubyStatTypeUpdateManager.class.getName());

    @Nullable
    private static Properties ourProperties;
    @Nullable
    private static RubyStatTypeUpdateManager ourInstance;

    @NotNull
    private final AmazonS3 myS3Client = new AmazonS3Client();

    @Nullable
    public static RubyStatTypeUpdateManager getInstance() {
        if (ourInstance == null) {
            try {
                ourProperties = getProperties();
                ourInstance = new RubyStatTypeUpdateManagerImpl();
            } catch (IOException e) {
                LOG.error(e);
            }
        }

        return ourInstance;
    }

    @Override
    public void updateLocalStat(@NotNull final Project project, @NotNull final Module module) {
        final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
        if (signatureManager == null) {
            return;
        }

        // 1. Get a list of stat files from server
        final List<StatFileInfo> statFiles = getStatFileInfosFromServer();
        if (statFiles == null) {
            return;
        }

        // 2. Get a list of current gems
        final Set<GemInfo> gems = GemManager.getAllGems(module);

        // 3. Select appropriate stat files for my gems
        final List<StatFileInfo> neededStatFiles = gems.stream()
                .map(gem -> getClosestStatFile(gem, statFiles))
                .collect(Collectors.toList());

        // 4. Filter out unwanted files
        neededStatFiles.removeIf(statFile -> {
            final long localLastModified = signatureManager.getStatFileLastModified(
                    statFile.getGemName(), statFile.getGemVersion());
            return statFile.getLastModified() <= localLastModified;
        });

        // 5. Download stat files and add new stat to the DB,
        //    then update last modified time for stat files in the DB table
        for (final StatFileInfo statFile : neededStatFiles) {
            try {
                final List<RSignature> signatures = downloadRSignaturesFromServer(statFile.getFullGemName());
                signatures.forEach(signatureManager::recordSignature);
                signatureManager.setStatFileLastModified(
                        statFile.getGemName(), statFile.getGemVersion(), statFile.getLastModified());
            } catch (IOException e) {
                LOG.error(e);
            }
        }
    }

    @Override
    public void uploadCollectedStat() {
        final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
        if (signatureManager == null) {
            return;
        }

        final List<RSignature> signatures = signatureManager.getLocalSignatures();
        final Gson gson = new GsonBuilder().create();
        final String json = gson.toJson(signatures);
        final InputStream jsonStream = new ByteArrayInputStream(json.getBytes());
        final ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/json");

        final AmazonS3 client = new AmazonS3Client();
        String key = UUID.randomUUID().toString() + ".json";
        client.putObject(ourProperties.getProperty("bucketImport"), key, jsonStream, metadata);
    }

    @NotNull
    private static Properties getProperties() throws IOException {
        final String PROPERTIES_FILE = "aws.properties";

        final Properties properties = new Properties();
        final URL propertiesURL = RubyStatTypeUpdateManager.class.getClassLoader().getResource("properties");
        if (propertiesURL == null) {
            throw new FileNotFoundException("File " + PROPERTIES_FILE + " not found");
        }

        final String propertiesPath = propertiesURL.getPath();
        properties.load(new FileInputStream(propertiesPath + "/" + PROPERTIES_FILE));
        return properties;
    }

    @Nullable
    private StatFileInfo getClosestStatFile(@NotNull final GemInfo gemInfo,
                                            @NotNull List<StatFileInfo> statFileInfos) {
        statFileInfos = statFileInfos.stream()
                .filter(statFileInfo -> statFileInfo.getGemName().equals(gemInfo.getName()))
                .collect(Collectors.toList());
        final List<String> gemVersions = statFileInfos.stream()
                .map(StatFileInfo::getGemVersion)
                .collect(Collectors.toList());
        if (gemVersions.isEmpty()) {
            return null;
        }

        final String closestGemVersion = SqliteRSignatureManager.getClosestGemVersion(gemInfo.getVersion(), gemVersions);
        return statFileInfos.stream()
                .filter(statFileInfo -> statFileInfo.getGemVersion().equals(closestGemVersion))
                .findAny()
                .get();
    }

    @Nullable
    private List<StatFileInfo> getStatFileInfosFromServer() {
        final List<StatFileInfo> statFileInfos = new ArrayList<>();

        final AmazonS3 client = new AmazonS3Client();
        final ListObjectsV2Request request = new ListObjectsV2Request()
                .withBucketName(ourProperties.getProperty("bucketExport"));
        ListObjectsV2Result result;
        do {
            result = client.listObjectsV2(request);
            result.getObjectSummaries().forEach(s ->
                    statFileInfos.add(new StatFileInfo(s.getKey(), s.getLastModified().getTime())));
            request.setContinuationToken(result.getNextContinuationToken());
        } while (result.isTruncated());

        return statFileInfos;
    }

    @NotNull
    private List<RSignature> downloadRSignaturesFromServer(@NotNull final String statFileName) throws IOException {
        final GetObjectRequest request = new GetObjectRequest(ourProperties.getProperty("bucketExport"), statFileName);
        final S3Object s3object = myS3Client.getObject(request);
        final Gson gson = new Gson();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()))) {
            return gson.fromJson(reader, new TypeToken<List<RSignature>>() {}.getType());
        }
    }

    private RubyStatTypeUpdateManagerImpl() {
    }
}
