package com.example.seniorproject.ml

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.InputStreamReader
import kotlin.math.exp

/**
 * Lightweight XGBoost predictor that reads and evaluates trees from JSON format.
 * Supports multi-class classification with softmax objective.
 */
class XGBoostPredictor(
    private val context: Context,
    private val modelFileName: String = "xgb_model.json"
) {
    private var trees: List<TreeNode> = emptyList()
    private var numClass: Int = 0
    private var baseScore: Float = 0.5f

    data class TreeNode(
        val nodeId: Int,
        val split: String? = null,           // feature name or null for leaf
        val splitCondition: Float? = null,   // threshold
        val yes: Int? = null,                // left child node id
        val no: Int? = null,                 // right child node id
        val leaf: Float? = null,             // leaf value
        val children: List<TreeNode>? = null
    )

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val inputStream = context.assets.open(modelFileName)
            val reader = InputStreamReader(inputStream)
            val gson = Gson()
            
            // Try to parse as JsonObject first (standard XGBoost format)
            try {
                val jsonObject = gson.fromJson(reader, JsonObject::class.java)
                
                if (jsonObject.has("learner")) {
                    // Standard XGBoost format
                    val learner = jsonObject.getAsJsonObject("learner")
                    val learnerModelParam = learner.getAsJsonObject("learner_model_param")
                    numClass = learnerModelParam.get("num_class").asInt
                    baseScore = learnerModelParam.get("base_score").asFloat
                    
                    val gradientBooster = learner.getAsJsonObject("gradient_booster")
                    val model = gradientBooster.getAsJsonObject("model")
                    val treesArray = model.getAsJsonArray("trees")
                    
                    trees = treesArray.mapIndexed { index, treeElement ->
                        Log.d("XGBoostPredictor", "Parsing tree $index")
                        val tree = parseTree(treeElement.asJsonObject)
                        Log.d("XGBoostPredictor", "Tree $index parsed successfully: ${if (tree.leaf != null) "leaf=${tree.leaf}" else "split=${tree.split}"}")
                        tree
                    }
                } else {
                    throw Exception("No learner found in JSON")
                }
            } catch (e: Exception) {
                // Reset reader and try as JsonArray (direct tree format)
                reader.close()
                val newReader = InputStreamReader(context.assets.open(modelFileName))
                val treesArray = gson.fromJson(newReader, com.google.gson.JsonArray::class.java)
                
                // For array format, use default values
                numClass = 5
                baseScore = 0.5f
                
                trees = treesArray.map { treeElement ->
                    parseTree(treeElement.asJsonObject)
                }
                newReader.close()
            }
            
            Log.d("XGBoostPredictor", "Loaded ${trees.size} trees, $numClass classes")
            reader.close()
        } catch (e: Exception) {
            Log.e("XGBoostPredictor", "Failed to load XGBoost model", e)
            throw e
        }
    }

    private fun parseTree(treeJson: JsonObject): TreeNode {
        try {
            Log.d("XGBoostPredictor", "Parsing tree with keys: ${treeJson.keySet()}")
            
            // XGBoost JSON format - arrays are indexed by node position
            val leftChildren = treeJson.getAsJsonArray("left_children").map { it.asInt }
            val rightChildren = treeJson.getAsJsonArray("right_children").map { it.asInt }
            val parents = treeJson.getAsJsonArray("parents").map { it.asInt }
            val splitIndices = treeJson.getAsJsonArray("split_indices").map { it.asInt }
            val splitConditions = treeJson.getAsJsonArray("split_conditions").map { it.asFloat }
            val baseWeights = treeJson.getAsJsonArray("base_weights").map { it.asFloat }
            
            // Create node IDs based on array indices (0, 1, 2, ...)
            val nodeIds = (0 until leftChildren.size).toList()
            
            Log.d("XGBoostPredictor", "Tree arrays: nodes=${nodeIds.size}, left=${leftChildren.size}, right=${rightChildren.size}, splits=${splitIndices.size}")
            
            // Build tree structure (root is always node 0)
            return buildTreeNode(0, nodeIds, leftChildren, rightChildren, splitIndices, splitConditions, baseWeights)
        } catch (e: Exception) {
            Log.e("XGBoostPredictor", "Failed to parse tree, creating dummy tree", e)
            Log.e("XGBoostPredictor", "Tree JSON keys: ${treeJson.keySet()}")
            // Create a simple dummy tree that returns 0.0
            return TreeNode(
                nodeId = 0,
                leaf = 0.0f
            )
        }
    }

    private fun buildTreeNode(
        nodeIdx: Int,
        nodeIds: List<Int>,
        leftChildren: List<Int>,
        rightChildren: List<Int>,
        splitIndices: List<Int>,
        splitConditions: List<Float>,
        baseWeights: List<Float>
    ): TreeNode {
        val left = leftChildren[nodeIdx]
        val right = rightChildren[nodeIdx]
        
        // Leaf node (negative values indicate leaf)
        if (left < 0) {
            return TreeNode(
                nodeId = nodeIdx,
                leaf = baseWeights[nodeIdx]
            )
        }
        
        // Internal node
        return TreeNode(
            nodeId = nodeIdx,
            split = "f${splitIndices[nodeIdx]}", // feature index
            splitCondition = splitConditions[nodeIdx],
            yes = left,
            no = right,
            children = listOf(
                buildTreeNode(left, nodeIds, leftChildren, rightChildren, splitIndices, splitConditions, baseWeights),
                buildTreeNode(right, nodeIds, leftChildren, rightChildren, splitIndices, splitConditions, baseWeights)
            )
        )
    }

    /**
     * Predict class probabilities for a single sample
     * @param features Feature vector matching training data format
     * @return Array of class probabilities (after softmax)
     */
    fun predict(features: FloatArray): FloatArray {
        // Initialize raw scores (logits) with base score
        val rawScores = FloatArray(numClass) { baseScore }
        
        Log.d("XGBoostPredictor", "Predicting with ${trees.size} trees, baseScore=$baseScore")
        
        // Accumulate predictions from all trees
        trees.forEachIndexed { treeIdx, tree ->
            val treeOutput = predictTree(tree, features)
            val classIdx = treeIdx % numClass
            rawScores[classIdx] += treeOutput
            if (treeIdx < 5) { // Log first 5 trees
                Log.d("XGBoostPredictor", "Tree $treeIdx -> class $classIdx, output=$treeOutput, rawScore=${rawScores[classIdx]}")
            }
        }
        
        Log.d("XGBoostPredictor", "Raw scores before softmax: ${rawScores.joinToString { "%.3f".format(it) }}")
        
        // Apply softmax to convert to probabilities
        val result = softmax(rawScores)
        Log.d("XGBoostPredictor", "Final probabilities: ${result.joinToString { "%.3f".format(it) }}")
        return result
    }

    private fun predictTree(node: TreeNode, features: FloatArray): Float {
        // Leaf node - return value
        if (node.leaf != null) {
            return node.leaf
        }
        
        // Internal node - traverse based on split
        val featureIdx = node.split!!.removePrefix("f").toInt()
        val featureValue = features[featureIdx]
        
        return if (featureValue < node.splitCondition!!) {
            // Go left (yes branch)
            predictTree(node.children!![0], features)
        } else {
            // Go right (no branch)
            predictTree(node.children!![1], features)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        // Subtract max for numerical stability
        val maxLogit = logits.maxOrNull() ?: 0f
        val exps = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sumExps = exps.sum()
        return exps.map { it / sumExps }.toFloatArray()
    }

    /**
     * Get the predicted class index
     */
    fun predictClass(features: FloatArray): Int {
        val probs = predict(features)
        return probs.indices.maxByOrNull { probs[it] } ?: 0
    }
}


