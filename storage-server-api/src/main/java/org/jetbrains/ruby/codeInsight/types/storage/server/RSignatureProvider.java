package org.jetbrains.ruby.codeInsight.types.storage.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ruby.codeInsight.types.signature.*;

import java.util.Collection;

/**
 * <p>An interface that allows for transparent working with the signatures storage.</p>
 * <p>
 * <p>The general workflow is the following:</p>
 * <ul>
 * <li>1. Determine which gem statistics are to be used. If one wants to receive code insight for some project,
 * they must know which gems are available at runtime.
 * <li>2. Since a precalculated information for the particular gem may not be available, one searches for the
 * closest gem version with calculated stats via {@link #getClosestRegisteredGem(GemInfo)}
 * <li>3. In order to get the registered classes available upon requiring the given gem one may use
 * {@link #getRegisteredClasses(GemInfo)}
 * <li>4. Given a class of a receiver object one may get the registered methods available for sending
 * via {@link #getRegisteredMethods(ClassInfo)}
 * <li>5. Given a call, which is represented as a method of a particular class in a particular gem one may
 * get Signature contract via {@link #getSignature(MethodInfo)}. It allows for getting params
 * information, deducing return type from given input types, etc.
 * </ul>
 */
public interface RSignatureProvider {
    @NotNull
    Collection<GemInfo> getRegisteredGems() throws StorageException;

    @Nullable
    GemInfo getClosestRegisteredGem(@NotNull GemInfo usedGem) throws StorageException;

    @NotNull
    Collection<ClassInfo> getRegisteredClasses(@NotNull GemInfo gem) throws StorageException;

    @NotNull
    Collection<ClassInfo> getAllClassesWithFQN(@NotNull String fqn) throws StorageException;

    @NotNull
    Collection<MethodInfo> getRegisteredMethods(@NotNull ClassInfo containerClass)
            throws StorageException;

    /**
     * Get registered {@link CallInfo}s by given {@code methodInfo}
     */
    @NotNull
    Collection<CallInfo> getRegisteredCallInfos(@NotNull MethodInfo methodInfo) throws StorageException;

    @Nullable
    SignatureInfo getSignature(@NotNull MethodInfo method) throws StorageException;

    void deleteSignature(@NotNull MethodInfo method) throws StorageException;

    void putSignature(@NotNull SignatureInfo signatureInfo) throws StorageException;

}
