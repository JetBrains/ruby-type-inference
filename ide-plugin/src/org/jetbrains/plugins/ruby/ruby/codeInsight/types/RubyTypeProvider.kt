package org.jetbrains.plugins.ruby.ruby.codeInsight.types

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.AbstractRubyTypeProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.IncomingType
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.ResolveUtil
import org.jetbrains.plugins.ruby.ruby.codeInsight.stateTracker.RubyClassHierarchyWithCaching
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.SymbolicExecutionContext
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.SymbolicExpressionProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.SymbolicTypeInferenceProvider
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.TypeInferenceComponent
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.instance.TypeInferenceInstance
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.symbolicExpression.SymbolicCall
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbolicExecution.symbolicExpression.SymbolicExpression
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.Type
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.Symbol
import org.jetbrains.plugins.ruby.ruby.codeInsight.symbols.structure.util.SymbolHierarchy
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.impl.REmptyType
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.RubyPsiUtil
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier
import org.jetbrains.ruby.codeInsight.types.signature.*
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.CallInfoTable
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.RSignatureProviderImpl
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Cache where we store last accessed [CallInfo]s. Thread safe
 */
private val registeredCallInfosCache: MutableMap<MethodInfo, List<CallInfo>>
        = ContainerUtil.createConcurrentSoftKeySoftValueMap<MethodInfo, List<CallInfo>>()

fun resetAllRubyTypeProviderAndIDEACaches(project: Project?) {
    registeredCallInfosCache.clear()
    // Clears IDEAs caches about inferred types
    ServiceManager.getService(project ?: return, TypeInferenceContext::class.java)?.clear()
}

fun getCachedOrComputedRegisteredCallInfo(methodInfo: MethodInfo): List<CallInfo> {
    return registeredCallInfosCache.getOrPut(methodInfo) {
        RSignatureProviderImpl.getRegisteredCallInfos(methodInfo)
    }
}

class RubyParameterTypeProvider : AbstractRubyTypeProvider() {
    override fun createTypeBySymbol(symbol: Symbol): RType? {
        return null
    }

    override fun createTypeByRExpression(expr: RExpression): RType? {
        val symbol = ResolveUtil.resolveToSymbolWithCaching(expr.reference, false)
        if (symbol?.type == Type.CONSTANT) {
            val module = ModuleUtilCore.findModuleForPsiElement(expr) ?: return null
            val classHierarchyWithCaching = RubyClassHierarchyWithCaching.getInstance(module) ?: return null
            val path = symbol?.fqnWithNesting?.fullPath ?: return null
            val constant = classHierarchyWithCaching.getTypeForConstant(path) ?: return null
            val originType = RTypeFactory.createTypeByFQN(expr.project, constant.type)
            val mixins = constant.extended.map { RTypeFactory.createTypeByFQN(expr.project, it) }
            if (mixins.isNotEmpty()) {
                return RTypeUtil.union(originType,  mixins.reduce { acc, it -> RTypeUtil.union(acc, it) })
            }
            return originType
        }
        if (expr is RIdentifier && expr.isParameter) {
            val method = RubyPsiUtil.getContainingRMethod(expr) ?: return null
            val rubyModuleName = RubyPsiUtil.getContainingRClassOrModule(method)?.fqn?.fullPath ?: "Object"

            val info = MethodInfo.Impl(ClassInfo(rubyModuleName), method.fqn.shortName, RVisibility.PUBLIC)

            val callInfos: List<CallInfo> = getCachedOrComputedRegisteredCallInfo(info)

            val returnType = callInfos.map { callInfo ->
                val typeName = expr.name?.let { callInfo.getTypeNameByArgumentName(it) } ?: return@map REmptyType.INSTANCE
                return@map RTypeFactory.createTypeClassName(typeName, expr)
            }.unionTypesSmart()
            return if (returnType == REmptyType.INSTANCE) {
                // If we don't have any information about type then return null
                // in order to allow to RubyMine try to determine type itself
                null
            } else {
                returnType
            }
        }
        return null
    }
}

/**
 * Provides types for Ruby method return values. Type providing based on information collection into [CallInfoTable]
 */
class ReturnTypeSymbolicTypeInferenceProvider : SymbolicTypeInferenceProvider {
    companion object {
        private val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1)
    }

    override fun evaluateSymbolicCall(symbolicCall: SymbolicCall,
                                      context: SymbolicExecutionContext,
                                      callContext: TypeInferenceInstance.CallContext,
                                      provider: SymbolicExpressionProvider,
                                      component: TypeInferenceComponent): SymbolicExpression? {
        ProgressManager.checkCanceled()
        val job: () -> SymbolicExpression? = {
            evaluateSymbolicCallPoolJob(symbolicCall, context, callContext, component)
        }

        val future: Future<SymbolicExpression?> = pool.submit(job)
        return try {
            // This method is run under read action and we cannot afford to spend a lot of time determining time.
            // Otherwise we got glitches see: https://youtrack.jetbrains.com/issue/RUBY-25433
            future.get(100, TimeUnit.MILLISECONDS)
        } catch (ex: TimeoutException) {
            null
        }
    }

    private fun evaluateSymbolicCallPoolJob(symbolicCall: SymbolicCall,
                                            context: SymbolicExecutionContext,
                                            callContext: TypeInferenceInstance.CallContext,
                                            component: TypeInferenceComponent): SymbolicExpression? {
        var unnamedArgsTypes: List<String?> = emptyList()
        var namedArgsTypes: List<ArgumentNameAndType?> = emptyList()
        var receiverTypesConsideringAncestors: List<String> = emptyList()
        ReadAction.run<Exception> {
            ProgressManager.checkCanceled()
            val exactReceiverType: RType = SymbolicTypeInferenceProvider.getReceiverType(symbolicCall, component, callContext)

            // reversed because getAncestorsCaching gives us list of ancestors ordered from parent to end children
            // This list already include exactReceiverType
            receiverTypesConsideringAncestors = RTypeUtil.getBirthTypeSymbol(exactReceiverType)
                    ?.let { SymbolHierarchy.getAncestorsCaching(it, callContext.invocationPoint) }
                    ?.map { it.symbol.fqnWithNesting.toString() }?.reversed() ?: emptyList()

            val typeInferenceComponent = context.getComponent(TypeInferenceComponent::class.java)

            unnamedArgsTypes = symbolicCall.arguments.asSequence().filter { it.type != IncomingType.ASSOC }
                    .map { typeInferenceComponent.getTypeForSymbolicExpression(it.expression).name }.toList()

            namedArgsTypes = symbolicCall.arguments.asSequence().filter { it.type == IncomingType.ASSOC }
                    .map {
                        val type = typeInferenceComponent.getTypeForSymbolicExpression(it.expression).name ?: return@map null
                        return@map ArgumentNameAndType(it.keyName, type)
                    }.toList()
        }

        for (receiverTypeName in receiverTypesConsideringAncestors) {
            val methodInfo = MethodInfo.Impl(ClassInfo.Impl(null, receiverTypeName), symbolicCall.name)

            val registeredCallInfos = getCachedOrComputedRegisteredCallInfo(methodInfo)

            val registeredReturnTypes: List<String> = registeredCallInfos
                    .asSequence()
                    .filter { argumentsMatch(it, unnamedArgsTypes, namedArgsTypes) }
                    .map { it.returnType }
                    .toList()
                    .takeIf { !it.isEmpty() }
                    ?: registeredCallInfos.map { it.returnType }

            val returnType = registeredReturnTypes
                    .mapNotNull {
                        RTypeFactory.createTypeClassName(it, callContext.invocationPoint as? RPsiElement
                                ?: return@mapNotNull null)
                    }
                    .unionTypesSmart()

            if (returnType != REmptyType.INSTANCE) {
                component.updateSymbolicExpressionType(symbolicCall, returnType)
                return symbolicCall
            }
        }
        // If we don't have any information about type then return null
        // in order to allow to RubyMine try to determine type itself
        return null
    }

    private fun argumentsMatch(oneOfExpected: CallInfo, actualUnnamedArgs: List<String?>, actualNamedArgs: List<ArgumentNameAndType?>): Boolean {
        if (oneOfExpected.unnamedArguments.size != actualUnnamedArgs.size || oneOfExpected.namedArguments.size != actualNamedArgs.size) {
            return false
        }

        if (oneOfExpected.unnamedArguments.zip(actualUnnamedArgs).any {
                    it.first.type != ArgumentNameAndType.IMPLICITLY_PASSED_ARGUMENT_TYPE &&
                            it.second != null &&
                            it.first.type != it.second
                }) {
            return false
        }

        if (oneOfExpected.namedArguments.zip(actualNamedArgs).any {
                    it.second != null &&
                            it.first.type != ArgumentNameAndType.IMPLICITLY_PASSED_ARGUMENT_TYPE &&
                            it.second!!.type != ArgumentNameAndType.IMPLICITLY_PASSED_ARGUMENT_TYPE &&
                            it.first.type != it.second!!.type
                }) {
            return false
        }

        return true
    }
}

/**
 * The same as [unionTypes] but also get rid of [REmptyType] and duplicates
 */
private fun List<RType>.unionTypesSmart(): RType = filter { it != REmptyType.INSTANCE }.distinct().unionTypes()

private fun List<RType>.unionTypes(): RType {
    if (size == 0) {
        return REmptyType.INSTANCE
    }

    if (size == 1) {
        return first()
    }
    val m = size / 2
    return RTypeUtil.union(subList(0, m).unionTypes(), subList(m, size).unionTypes())
}
