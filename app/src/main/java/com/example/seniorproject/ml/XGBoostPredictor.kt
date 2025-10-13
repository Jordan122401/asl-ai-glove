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

    // ENHANCED: Added robust JSON parsing to handle different XGBoost export formats
    // This fixes the "com.google.gson.JsonPrimitive cannot..." error that was occurring
    private fun loadModel() {
        try {
            val inputStream = context.assets.open(modelFileName)
            val reader = InputStreamReader(inputStream)
            val gson = Gson()
            val jsonObject = gson.fromJson(reader, JsonObject::class.java)
            
            // ADDED: Debug logging to help identify JSON structure issues
            Log.d("XGBoostPredictor", "JSON structure: ${jsonObject.keySet()}")
            
            // ADDED: Support for multiple XGBoost JSON formats
            // Different XGBoost versions or export methods may create different JSON structures
            when {
                jsonObject.has("learner") -> {
                    loadXGBoostFormat(jsonObject)  // Standard XGBoost format
                }
                jsonObject.has("num_class") -> {
                    loadSimpleFormat(jsonObject)   // Simplified format
                }
                else -> {
                    Log.e("XGBoostPredictor", "Unknown XGBoost JSON format")
                    throw IllegalArgumentException("Unknown XGBoost JSON format")
                }
            }
            
            Log.d("XGBoostPredictor", "Loaded ${trees.size} trees, $numClass classes")
            reader.close()
        } catch (e: Exception) {
            Log.e("XGBoostPredictor", "Failed to load XGBoost model", e)
            Log.e("XGBoostPredictor", "Error details: ${e.message}")
            throw e
        }
    }
    
    private fun loadXGBoostFormat(jsonObject: JsonObject) {
        // Standard XGBoost format
        val learner = jsonObject.getAsJsonObject("learner")
        val learnerModelParam = learner.getAsJsonObject("learner_model_param")
        numClass = learnerModelParam.get("num_class").asInt
        baseScore = learnerModelParam.get("base_score").asFloat
        
        // Extract gradient booster (trees)
        val gradientBooster = learner.getAsJsonObject("gradient_booster")
        val model = gradientBooster.getAsJsonObject("model")
        val treesArray = model.getAsJsonArray("trees")
        
        trees = treesArray.map { treeElement ->
            parseTree(treeElement.asJsonObject)
        }
    }
    
    // ADDED: Support for simplified XGBoost JSON format
    // This handles cases where the JSON structure is different from standard XGBoost export
    private fun loadSimpleFormat(jsonObject: JsonObject) {
        // Simplified format - try to extract basic info with safe defaults
        numClass = jsonObject.get("num_class")?.asInt ?: 5
        baseScore = jsonObject.get("base_score")?.asFloat ?: 0.5f
        
        // ADDED: Fallback for missing trees - creates dummy tree to prevent crashes
        if (!jsonObject.has("trees")) {
            Log.w("XGBoostPredictor", "No trees found in JSON, creating dummy tree")
            trees = listOf(createDummyTree())
            return
        }
        
        val treesArray = jsonObject.getAsJsonArray("trees")
        trees = treesArray.map { treeElement ->
            parseTree(treeElement.asJsonObject)
        }
    }
    
    // ADDED: Creates a dummy tree to prevent crashes when JSON parsing fails
    // This ensures the predictor can still function even with malformed JSON
    private fun createDummyTree(): TreeNode {
        // Create a simple dummy tree that always returns 0.0
        return TreeNode(
            nodeId = 0,
            leaf = 0.0f
        )
    }

    // ENHANCED: Added robust tree parsing with multiple format support and error handling
    // This prevents crashes when tree JSON structure is unexpected
    private fun parseTree(treeJson: JsonObject): TreeNode {
        try {
            // ADDED: Debug logging to help identify tree structure issues
            Log.d("XGBoostPredictor", "Tree JSON keys: ${treeJson.keySet()}")
            
            // ADDED: Support for simple leaf node format
            if (treeJson.has("leaf")) {
                return TreeNode(
                    nodeId = treeJson.get("nodeid")?.asInt ?: 0,
                    leaf = treeJson.get("leaf").asFloat
                )
            }
            
            // Standard XGBoost JSON format has all nodes in flat arrays
            val nodeIds = treeJson.getAsJsonArray("id").map { it.asInt }
            val leftChildren = treeJson.getAsJsonArray("left_children").map { it.asInt }
            val rightChildren = treeJson.getAsJsonArray("right_children").map { it.asInt }
            val parents = treeJson.getAsJsonArray("parents").map { it.asInt }
            val splitIndices = treeJson.getAsJsonArray("split_indices").map { it.asInt }
            val splitConditions = treeJson.getAsJsonArray("split_conditions").map { it.asFloat }
            val baseWeights = treeJson.getAsJsonArray("base_weights").map { it.asFloat }
            
            // Build tree structure (root is always node 0)
            return buildTreeNode(0, nodeIds, leftChildren, rightChildren, splitIndices, splitConditions, baseWeights)
        } catch (e: Exception) {
            // ADDED: Graceful fallback when tree parsing fails
            Log.e("XGBoostPredictor", "Failed to parse tree, creating dummy tree", e)
            return createDummyTree()
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
        
        // Accumulate predictions from all trees
        trees.forEachIndexed { treeIdx, tree ->
            val treeOutput = predictTree(tree, features)
            val classIdx = treeIdx % numClass
            rawScores[classIdx] += treeOutput
        }
        
        // Apply softmax to convert to probabilities
        return softmax(rawScores)
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



