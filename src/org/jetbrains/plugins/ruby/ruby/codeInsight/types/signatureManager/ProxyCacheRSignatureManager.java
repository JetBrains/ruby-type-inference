package org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.graph.RSignatureDAG;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.ParameterInfo;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.RSignature;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signature.RSignatureBuilder;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProxyCacheRSignatureManager extends RSignatureManager {
    @NotNull
    private static final Logger LOG = Logger.getInstance(ProxyCacheRSignatureManager.class.getName());
    @NotNull
    private static final Map<RSignatureManager, ProxyCacheRSignatureManager> ourInstances = new HashMap<>();

    @NotNull
    private final RSignatureManager myDecoratedManager;
    @NotNull
    private final LoadingCache<RSignature, RSignatureDAG> mySignatureCache;
    @NotNull
    private final Set<RSignature> myPersistedDAGs = new HashSet<>();

    @NotNull
    public static ProxyCacheRSignatureManager getInstance(@NotNull final  Project project,
                                                          @NotNull final RSignatureManager signatureManager) {
        if (!ourInstances.containsKey(signatureManager)) {
            ourInstances.put(signatureManager, new ProxyCacheRSignatureManager(project, signatureManager));
        }

        return ourInstances.get(signatureManager);
    }

    private ProxyCacheRSignatureManager(@NotNull final Project project,
                                        @NotNull final RSignatureManager decoratedManager) {
        myDecoratedManager = decoratedManager;
        mySignatureCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .removalListener(
                        (RemovalListener<RSignature, RSignatureDAG>) notification -> {
                            final RSignature key = notification.getKey();
                            final RSignatureDAG dag = notification.getValue();
                            if (key != null && dag != null && isModifiedDAG(key)) {
                                myDecoratedManager.deleteSimilarSignatures(key);
                                dag.depthFirstSearch(myDecoratedManager::recordSignature);
                            }
                        }
                )
                .build(
                        new CacheLoader<RSignature, RSignatureDAG>() {
                            @Override
                            public RSignatureDAG load(RSignature signature) throws Exception {
                                final RSignature key = getKey(signature);
                                final List<RSignature> signatures = myDecoratedManager.getSimilarSignatures(key);
                                final RSignatureDAG dag = new RSignatureDAG(project, key.getArgsInfo().size());
                                dag.addAll(signatures);
                                myPersistedDAGs.remove(key);
                                return dag;
                            }
                        }
                );
    }

    @NotNull
    @Override
    public List<String> findReturnTypeNamesBySignature(@NotNull final Project project,
                                                       @Nullable final Module module,
                                                       @NotNull final RSignature signature) {
        try {
            return mySignatureCache.get(getKey(signature)).findClosest(signature).stream()
                    .map(RSignature::getReturnTypeName)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (ExecutionException e) {
            LOG.info(e);
        }

        return ContainerUtilRt.emptyList();
    }

    @Override
    public void recordSignature(@NotNull final RSignature signature) {
        try {
            final RSignature key = getKey(signature);
            mySignatureCache.get(key).add(signature);
            myPersistedDAGs.add(key);
        } catch (ExecutionException e) {
            LOG.info(e);
        }
    }

    @Override
    public void deleteSignature(@NotNull final RSignature signature) {
        // TODO: RSignatureDAG::remove
//        try {
//            final RSignature key = getKey(signature);
//            mySignatureCache.get(key).remove(signature);
//            myPersistedDAGs.add(key);
//        } catch (ExecutionException e) {
//            LOG.info(e);
//        }
    }

    @Override
    public void deleteSimilarSignatures(@NotNull final RSignature signature) {
        try {
            final RSignature key = getKey(signature);
            mySignatureCache.get(key).removeAll();
            myPersistedDAGs.add(key);
        } catch (ExecutionException e) {
            LOG.info(e);
        }
    }

    @Override
    public List<RSignature> getSimilarSignatures(@NotNull final RSignature signature) {
        try {
            return mySignatureCache.get(signature).toList();
        } catch (ExecutionException e) {
            LOG.info(e);
        }

        return ContainerUtilRt.emptyList();
    }

    @Override
    public void compact(@NotNull final Project project) {
        // TODO: ProxyCacheRSignatureManager::compact
    }

    @Override
    public void clear() {
        myPersistedDAGs.clear();
        mySignatureCache.cleanUp();
//        myDecoratedManager.clear();
    }

    @NotNull
    @Override
    public List<ParameterInfo> getMethodArgsInfo(@NotNull final String methodName,
                                                 @NotNull final String receiverName) {
        return myDecoratedManager.getMethodArgsInfo(methodName, receiverName);
    }

    public void invalidateAll() {
        mySignatureCache.invalidateAll();
    }

    public boolean isModifiedDAG(@NotNull final RSignature signature) {
        return myPersistedDAGs.contains(getKey(signature));
    }

    @NotNull
    @Override
    protected Set<RSignature> getReceiverMethodSignatures(@NotNull final String receiverName) {
        return myDecoratedManager.getReceiverMethodSignatures(receiverName);
    }

    @NotNull
    private RSignature getKey(@NotNull final RSignature signature) {
        return new RSignatureBuilder(signature.getMethodName())
                .setReceiverName(signature.getReceiverName())
                .setArgsInfo(signature.getArgsInfo())
                .setGemName(signature.getGemName())
                .setGemVersion(signature.getGemVersion())
                .build();
    }
}
