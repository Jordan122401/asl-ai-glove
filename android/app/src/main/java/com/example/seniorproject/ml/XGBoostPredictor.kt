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
        var inputStream: java.io.InputStream? = null
        var reader: InputStreamReader? = null
        try {
            Log.d("XGBoostPredictor", "Loading XGBoost model from: $modelFileName")
            inputStream = context.assets.open(modelFileName)
            reader = InputStreamReader(inputStream, "UTF-8")
            val gson = Gson()
            
            // Try to parse as JsonObject first (standard XGBoost format)
            try {
                val jsonObject = gson.fromJson(reader, JsonObject::class.java)
                
                if (jsonObject.has("learner")) {
                    // Standard XGBoost format
                    val learner = jsonObject.getAsJsonObject("learner")
                    val learnerModelParam = learner.getAsJsonObject("learner_model_param")
                    numClass = learnerModelParam.get("num_class").asInt
                    
                    // Handle base_score - it can be a float or a string array
                    val baseScoreElement = learnerModelParam.get("base_score")
                    baseScore = try {
                        // Try as float first
                        baseScoreElement.asFloat
                    } catch (e: Exception) {
                        // If it's a string array like "[2E-1,2.2564103E-1,...]", use first value or default
                        try {
                            val baseScoreStr = baseScoreElement.asString
                            if (baseScoreStr.startsWith("[") && baseScoreStr.endsWith("]")) {
                                // Parse first value from array string
                                val firstValue = baseScoreStr.removePrefix("[").removeSuffix("]").split(",").firstOrNull()
                                firstValue?.toFloatOrNull() ?: 0.5f
                            } else {
                                baseScoreStr.toFloatOrNull() ?: 0.5f
                            }
                        } catch (e2: Exception) {
                            Log.w("XGBoostPredictor", "Could not parse base_score, using default 0.5", e2)
                            0.5f
                        }
                    }
                    
                    Log.d("XGBoostPredictor", "Model params: numClass=$numClass, baseScore=$baseScore")
                    
                    val gradientBooster = learner.getAsJsonObject("gradient_booster")
                    val model = gradientBooster.getAsJsonObject("model")
                    val treesArray = model.getAsJsonArray("trees")
                    
                    Log.d("XGBoostPredictor", "Found ${treesArray.size()} trees to parse")
                    
                    trees = treesArray.mapIndexed { index, treeElement ->
                        if (index % 100 == 0) {
                            Log.d("XGBoostPredictor", "Parsing tree $index/${treesArray.size()}")
                        }
                        val tree = parseTree(treeElement.asJsonObject)
                        tree
                    }
                    
                    Log.d("XGBoostPredictor", "Successfully parsed ${trees.size} trees")
                } else {
                    throw Exception("No learner found in JSON")
                }
            } catch (e: Exception) {
                Log.w("XGBoostPredictor", "Failed to parse as standard format, trying array format", e)
                // Reset reader and try as JsonArray (direct tree format)
                reader?.close()
                inputStream?.close()
                inputStream = context.assets.open(modelFileName)
                reader = InputStreamReader(inputStream, "UTF-8")
                val treesArray = gson.fromJson(reader, com.google.gson.JsonArray::class.java)
                
                // For array format, use default values
                numClass = 5
                baseScore = 0.5f
                
                trees = treesArray.map { treeElement ->
                    parseTree(treeElement.asJsonObject)
                }
            }
            
            if (trees.isEmpty()) {
                throw Exception("No trees loaded from model file")
            }
            
            Log.d("XGBoostPredictor", "Loaded ${trees.size} trees, $numClass classes")
        } catch (e: OutOfMemoryError) {
            Log.e("XGBoostPredictor", "Out of memory loading XGBoost model. File may be too large.", e)
            throw Exception("Model file too large for device memory. Consider using a smaller model or SimpleXGBoostPredictor.", e)
        } catch (e: Exception) {
            Log.e("XGBoostPredictor", "Failed to load XGBoost model from $modelFileName", e)
            Log.e("XGBoostPredictor", "Error type: ${e.javaClass.simpleName}, message: ${e.message}")
            e.printStackTrace()
            throw Exception("Failed to load XGBoost model: ${e.message}", e)
        } finally {
            try {
                reader?.close()
                inputStream?.close()
            } catch (e: Exception) {
                Log.w("XGBoostPredictor", "Error closing streams", e)
            }
        }
    }

    private fun parseTree(treeJson: JsonObject): TreeNode {
        try {
            // XGBoost JSON format - arrays are indexed by node position
            val leftChildren = treeJson.getAsJsonArray("left_children").map { it.asInt }
            val rightChildren = treeJson.getAsJsonArray("right_children").map { it.asInt }
            val parents = treeJson.getAsJsonArray("parents").map { it.asInt }
            val splitIndices = treeJson.getAsJsonArray("split_indices").map { it.asInt }
            val splitConditions = treeJson.getAsJsonArray("split_conditions").map { it.asFloat }
            val baseWeights = treeJson.getAsJsonArray("base_weights").map { it.asFloat }
            
            // Create node IDs based on array indices (0, 1, 2, ...)
            val nodeIds = (0 until leftChildren.size).toList()
            
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
        
        // Accumulate predictions from all trees
        trees.forEachIndexed { treeIdx, tree ->
            val treeOutput = predictTree(tree, features)
            val classIdx = treeIdx % numClass
            rawScores[classIdx] += treeOutput
        }
        
        // Apply softmax to convert to probabilities
        val result = softmax(rawScores)
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


