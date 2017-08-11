package org.jetbrains.ruby.codeInsight.types.signature

import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TransitionHelper

/**
 * The `SignatureContract` interface allows for checking input type sequence validity
 * as well as hinting the next possible transitions for the current sequence.
 *
 * The set of type contracts representing the method is represented with
 * an automaton of a specific kind:
 *   * There is exactly one start node (=state) as an entry point for each
 *     type sequence;
 *   * There is exactly one finish node (=state) representing a successful
 *     read of the type sequence;
 *   * All paths from start node the the finish node have the same length.
 *
 * There is no difference between input type and return type in the means
 * of the automaton used. Thus,
 *
 *  * A set of input and output types `(A, B) -> C` is read in the contract iff
 *     1. automaton "length" is 3, and
 *     2. reading types `(A, B, C)` by the automaton succeeds.
 *  * Return type for input types `(A, B)` is calculated by reading `(A, B)`
 *     in the automaton and getting _the only_ outbound transition from the resulting node.
 */
interface SignatureContract {
    val nodeCount: Int
    val startNode: SignatureNode
    val argsInfo: List<ParameterInfo>

    companion object {
        fun accept(rSignatureContract: SignatureContract, signature: RTuple): Boolean {
            var currNode = rSignatureContract.startNode

            val returnType = signature.returnTypeName

            val argsTypes = signature.argsTypes
            for (argIndex in argsTypes.indices) {
                val type = argsTypes[argIndex]

                val transition = TransitionHelper.calculateTransition(signature.argsTypes, argIndex, type)

                currNode = currNode.transitions[transition]
                        ?: return false
            }

            val transition = TransitionHelper.calculateTransition(signature.argsTypes, signature.argsTypes.size, returnType)

            return currNode.transitions.containsKey(transition)
        }
    }
}

interface SignatureNode {
    val transitions: Map<ContractTransition, SignatureNode>
}