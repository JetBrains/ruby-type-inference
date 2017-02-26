package ruby.codeInsight.types.storage.server;

import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ruby.codeInsight.types.signature.RSignature;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public abstract class RSignatureStorageServer {
    @NotNull
    public abstract List<Pair<String, String>> getGemNamesAndVersionsFromStorage() throws SQLException;

    @NotNull
    public abstract List<RSignature> getSignaturesFromStorage(@NotNull final String gemName,
                                                              @NotNull final String gemVersion)
            throws SQLException, ClassNotFoundException;

    public abstract void insertSignaturesToStorage(@NotNull final List<RSignature> signatures) throws SQLException;

    public abstract List<RSignature> getSignaturesFromStatFile(@NotNull final String statFileName,
                                                               final boolean fromExportBucket) throws IOException;

    public abstract void insertSignaturesToStatFile(@NotNull final List<RSignature> signatures,
                                                    @NotNull final String statFileName,
                                                    final boolean toExportBucket);

    @NotNull
    public abstract List<StatFileInfo> getStatFileInfos(final boolean fromExportBucket);

    public abstract void deleteStatFile(@NotNull final String statFileName, final boolean fromExportBucket);
}
