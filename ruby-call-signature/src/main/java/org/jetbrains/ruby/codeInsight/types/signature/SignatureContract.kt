package org.jetbrains.ruby.codeInsight.types.signature

import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ReferenceContractTransition
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TransitionHelper
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.TypedContractTransition
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

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

        fun getAllReturnTypes(rSignatureContract: SignatureContract): Set<String> {
            val curNode = rSignatureContract.startNode
            return forwardLook(curNode)
        }

        private fun getBackEdges(startNode: SignatureNode): Map<SignatureNode, Map<ContractTransition, SignatureNode>> {
            val result = HashMap<SignatureNode, MutableMap<ContractTransition, SignatureNode>>()
            val queue = LinkedList<SignatureNode>()
            val visited = HashSet<SignatureNode>()
            queue.push(startNode)
            visited.add(startNode)
            while (!queue.isEmpty()) {
                val node = queue.poll()
                for ((transition, succ) in node.transitions) {
                    if (!visited.contains(succ)) {
                        var nodeEntry = result[succ]
                        if (nodeEntry == null) {
                            nodeEntry = HashMap()
                            result.put(succ, nodeEntry)
                        }
                        nodeEntry.put(transition, node)
                        visited.add(succ)
                        queue.push(succ)
                    }
                }
            }
            return result
        }

        private fun forwardLook(startNode: SignatureNode): Set<String> {
            val backEdges = getBackEdges(startNode)
            val queue = LinkedList<SignatureNode>()
            val visited = HashSet<SignatureNode>()
            val level = HashMap<SignatureNode, Int>()
            val result = HashSet<String>()

            queue.push(startNode)
            visited.add(startNode)
            level[startNode] = 0

            while (!queue.isEmpty()) {
                val signatureNode = queue.poll()
                for ((key, value) in signatureNode.transitions) {
                    // is it exit node?
                    if (value.transitions.isEmpty()) {
                        when (key) {
                            is TypedContractTransition -> result.add(key.type)
                            is ReferenceContractTransition -> result.addAll(
                                    backwardLook(signatureNode,
                                            level[signatureNode]!! - getHighestSetBit(key.mask),
                                            level[signatureNode]!!, backEdges))
                            else -> throw IllegalStateException()
                        }
                    } else if (!visited.contains(value)) {
                        visited.add(value)
                        level[value] = level[signatureNode]!! + 1
                        queue.add(value)
                    }
                }
            }
            return result
        }

        private fun getHighestSetBit(mask: Int): Int {
            var m = mask
            var result = 0
            while (m > 0) {
                result++
                m = m shr 1
            }
            return result
        }

        private fun backwardLook(value: SignatureNode, numOfSteps: Int, originLevel: Int,
                                 backEdges: Map<SignatureNode, Map<ContractTransition, SignatureNode>>): Collection<String> {
            var nodesOnCurrentLevel = HashSet<SignatureNode>()
            nodesOnCurrentLevel.add(value)
            var currentStep = numOfSteps;
            while (currentStep >= 0) {
                if (currentStep > 0 ) {
                    val nodesOnPreviousLevel = HashSet<SignatureNode>()
                    for (node in nodesOnCurrentLevel) {
                        for ((_, pred) in backEdges[node]!!) {
                            nodesOnPreviousLevel.add(pred)
                        }
                    }
                    nodesOnCurrentLevel = nodesOnPreviousLevel
                } else {
                    val result = HashSet<String>()
                    for (node in nodesOnCurrentLevel) {
                        for ((transition, _) in backEdges[node]!!) {
                            if (transition is TypedContractTransition) {
                                result.add(transition.type)
                            } else if (transition is ReferenceContractTransition) {
                                val curLevel = originLevel - numOfSteps
                                val curNumOfSteps = curLevel - getHighestSetBit(transition.mask)
                                val res = backwardLook(node, curNumOfSteps, curLevel, backEdges)
                                result.addAll(res)
                            } else
                                throw IllegalArgumentException("Cannot reach")

                        }
                    }
                    return result
                }
                currentStep--;
            }
            throw IllegalArgumentException("Cannot reach")
        }
    }
}

interface SignatureNode {
    val transitions: Map<ContractTransition, SignatureNode>
}