package cn.com.omnimind.bot.agent.tool.handlers

import cn.com.omnimind.accessibility.action.OmniAction
import cn.com.omnimind.accessibility.service.AssistsService
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.agent.AgentCallback
import cn.com.omnimind.bot.agent.AgentExecutionEnvironment
import cn.com.omnimind.bot.agent.AgentToolExecutionHandle
import cn.com.omnimind.bot.agent.AgentToolRegistry
import cn.com.omnimind.bot.agent.ToolExecutionResult
import cn.com.omnimind.bot.util.AssistsUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * PhoneControlToolHandler - 手机操控工具处理器
 * 
 * 基于 MyClaw 的手机操控能力，移植到 myagent 的 ToolHandler 架构中。
 * 使用 OmniAction 来执行实际的 AccessibilityService 操作。
 */
class PhoneControlToolHandler(
    private val helper: SharedHelper
) : ToolHandler {

    companion object {
        private const val TAG = "PhoneControlToolHandler"
    }

    override val toolNames: Set<String> = setOf(
        "phone_tap",
        "phone_swipe",
        "phone_input_text",
        "phone_get_screen_info",
        "phone_find_node_info",
        "phone_open_app",
        "phone_system_key",
        "phone_scroll_to_find",
        "phone_take_screenshot"
    )

    override suspend fun execute(
        toolCall: cn.com.omnimind.baselib.llm.AssistantToolCall,
        args: JsonObject,
        runtimeDescriptor: AgentToolRegistry.RuntimeToolDescriptor,
        env: AgentExecutionEnvironment,
        callback: AgentCallback,
        toolHandle: AgentToolExecutionHandle
    ): ToolExecutionResult {
        helper.ensureRunActive()
        currentCoroutineContext().ensureActive()
        
        val toolName = toolCall.function.name
        OmniLog.d(TAG, "Executing phone tool: $toolName")
        
        return try {
            when (toolName) {
                "phone_tap" -> executePhoneTap(args)
                "phone_swipe" -> executePhoneSwipe(args)
                "phone_input_text" -> executePhoneInputText(args)
                "phone_get_screen_info" -> executePhoneGetScreenInfo()
                "phone_find_node_info" -> executePhoneFindNodeInfo(args)
                "phone_open_app" -> executePhoneOpenApp(args)
                "phone_system_key" -> executePhoneSystemKey(args)
                "phone_scroll_to_find" -> executePhoneScrollToFind(args)
                "phone_take_screenshot" -> executePhoneTakeScreenshot()
                else -> errorResult(toolName, "Unknown phone tool: $toolName")
            }
        } catch (e: Exception) {
            OmniLog.e(TAG, "Phone tool execution failed: ${e.message}", e)
            errorResult(toolName, "Phone tool execution failed: ${e.message}")
        }
    }

    // ======================== 工具实现 ========================
    
    private fun executePhoneTap(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_tap", error)
        
        val x = args["x"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_tap", "Missing required parameter: x")
        val y = args["y"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_tap", "Missing required parameter: y")
        
        return try {
            val omniAction = getOmniAction()
            omniAction.clickCoordinate(x.toFloat(), y.toFloat()).get()
            successResult("phone_tap", "Tapped at ($x, $y). Continue with the next action if needed.")
        } catch (e: Exception) {
            errorResult("phone_tap", "Failed to tap at ($x, $y): ${e.message}")
        }
    }
    
    private fun executePhoneSwipe(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_swipe", error)
        
        val startX = args["start_x"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_swipe", "Missing required parameter: start_x")
        val startY = args["start_y"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_swipe", "Missing required parameter: start_y")
        val endX = args["end_x"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_swipe", "Missing required parameter: end_x")
        val endY = args["end_y"]?.jsonPrimitive?.intOrNull
            ?: return errorResult("phone_swipe", "Missing required parameter: end_y")
        val durationMs = args["duration_ms"]?.jsonPrimitive?.intOrNull ?: 500
        
        return try {
            val service = AssistsService.instance!!
            val path = android.graphics.Path()
            path.moveTo(startX.toFloat(), startY.toFloat())
            path.lineTo(endX.toFloat(), endY.toFloat())
            
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                    path, 0, durationMs.toLong()
                ))
                .build()
            
            val future = java.util.concurrent.CompletableFuture<Boolean>()
            service.dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    future.complete(true)
                }
                override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                    future.complete(false)
                }
            }, null)
            
            val success = future.get()
            if (success) {
                successResult("phone_swipe", "Swiped from ($startX, $startY) to ($endX, $endY). Continue with the next action if needed.")
            } else {
                errorResult("phone_swipe", "Swipe gesture was cancelled")
            }
        } catch (e: Exception) {
            errorResult("phone_swipe", "Failed to swipe: ${e.message}")
        }
    }
    
    private fun executePhoneInputText(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_input_text", error)
        
        val text = args["text"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("phone_input_text", "Missing required parameter: text")
        val clearFirst = args["clear_first"]?.jsonPrimitive?.booleanOrNull ?: true
        
        return try {
            val service = AssistsService.instance!!
            val rootNode = service.rootInActiveWindow
                ?: return errorResult("phone_input_text", "Cannot get active window")
            
            val focusedNode = findFocusedEditableNode(rootNode)
                ?: return errorResult("phone_input_text", "No focused editable text field found. Please tap the text field first.")
            
            if (clearFirst) {
                val clearArgs = android.os.Bundle().apply {
                    putCharSequence(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        ""
                    )
                }
                focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
            }
            
            val omniAction = getOmniAction()
            omniAction.inputText(focusedNode, text)
            successResult("phone_input_text", "Text input successful. Continue with the next action if needed.")
        } catch (e: Exception) {
            errorResult("phone_input_text", "Failed to input text: ${e.message}")
        }
    }
    
    private fun findFocusedEditableNode(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) return result
        }
        return null
    }
    
    private fun executePhoneGetScreenInfo(): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_get_screen_info", error)
        
        return try {
            val service = AssistsService.instance!!
            val rootNode = service.rootInActiveWindow
                ?: return errorResult("phone_get_screen_info", "Cannot get active window")
            
            val tree = StringBuilder()
            dumpNodeTree(rootNode, tree, 0)
            successResult("phone_get_screen_info", tree.toString())
        } catch (e: Exception) {
            errorResult("phone_get_screen_info", "Failed to get screen info: ${e.message}")
        }
    }
    
    private fun dumpNodeTree(
        node: android.view.accessibility.AccessibilityNodeInfo,
        sb: StringBuilder,
        depth: Int
    ) {
        val indent = "  ".repeat(depth)
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        sb.appendLine("${indent}[${node.className}] \"${node.text ?: ""}\" bounds=$bounds clickable=${node.isClickable}")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpNodeTree(child, sb, depth + 1)
        }
    }
    
    private fun executePhoneFindNodeInfo(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_find_node_info", error)
        
        val text = args["text"]?.jsonPrimitive?.contentOrNull
        val nodeId = args["node_id"]?.jsonPrimitive?.contentOrNull
        
        if (text == null && nodeId == null) {
            return errorResult("phone_find_node_info", "Must provide either text or node_id")
        }
        
        return try {
            val service = AssistsService.instance!!
            val rootNode = service.rootInActiveWindow
                ?: return errorResult("phone_find_node_info", "Cannot get active window")
            
            val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
            findNodes(rootNode, text, nodeId, nodes)
            
            if (nodes.isEmpty()) {
                successResult("phone_find_node_info", "No matching nodes found")
            } else {
                val result = StringBuilder("Found ${nodes.size} matching node(s):\n")
                nodes.forEachIndexed { index, node ->
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    result.appendLine("${index + 1}. [${node.className}] \"${node.text ?: ""}\" bounds=$bounds clickable=${node.isClickable}")
                }
                successResult("phone_find_node_info", result.toString())
            }
        } catch (e: Exception) {
            errorResult("phone_find_node_info", "Failed to find node: ${e.message}")
        }
    }
    
    private fun findNodes(
        node: android.view.accessibility.AccessibilityNodeInfo,
        text: String?,
        nodeId: String?,
        result: MutableList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        val matchesText = text == null || (node.text?.toString()?.contains(text) == true)
        val matchesId = nodeId == null || (node.viewIdResourceName?.contains(nodeId) == true)
        if (matchesText && matchesId) result.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodes(child, text, nodeId, result)
        }
    }
    
    private fun executePhoneOpenApp(args: JsonObject): ToolExecutionResult {
        val packageName = args["package_name"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("phone_open_app", "Missing required parameter: package_name")
        
        return try {
            val context = cn.com.omnimind.bot.App.instance
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return errorResult("phone_open_app", "App not found: $packageName")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            successResult("phone_open_app", "Opened app: $packageName. Continue with the next action if needed.")
        } catch (e: Exception) {
            errorResult("phone_open_app", "Failed to open app: ${e.message}")
        }
    }
    
    private fun executePhoneSystemKey(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_system_key", error)
        
        val key = args["key"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("phone_system_key", "Missing required parameter: key")
        
        return try {
            val service = AssistsService.instance!!
            val globalAction = when (key) {
                "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                "recent_apps" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "collapse_notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
                "lock_screen" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                    } else return errorResult("phone_system_key", "lock_screen requires Android 9+")
                }
                else -> return errorResult("phone_system_key", "Unknown key: $key. Supported: back, home, recent_apps, notifications, collapse_notifications, lock_screen")
            }
            val success = service.performGlobalAction(globalAction)
            if (success) {
                successResult("phone_system_key", "Pressed $key. Continue with the next action if needed.")
            } else {
                errorResult("phone_system_key", "Failed to press $key")
            }
        } catch (e: Exception) {
            errorResult("phone_system_key", "Failed to press key: ${e.message}")
        }
    }
    
    private fun executePhoneScrollToFind(args: JsonObject): ToolExecutionResult {
        val error = checkAccessibilityService()
        if (error != null) return errorResult("phone_scroll_to_find", error)
        
        val searchText = args["text"]?.jsonPrimitive?.contentOrNull
            ?: return errorResult("phone_scroll_to_find", "Missing required parameter: text")
        val maxScrolls = args["max_scrolls"]?.jsonPrimitive?.intOrNull ?: 5
        
        return try {
            val service = AssistsService.instance!!
            val omniAction = getOmniAction()
            
            repeat(maxScrolls) { attempt ->
                val rootNode = service.rootInActiveWindow ?: return@repeat
                val nodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                findNodes(rootNode, searchText, null, nodes)
                
                if (nodes.isNotEmpty()) {
                    val node = nodes.first()
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    return successResult("phone_scroll_to_find", "Found \"$searchText\" after $attempt scroll(s) at bounds=$bounds")
                }
                
                val scrollableNode = findScrollableNode(rootNode)
                if (scrollableNode != null) {
                    omniAction.scrollNode(scrollableNode, cn.com.omnimind.accessibility.action.AccessibilityNodeScrollDirection.FORWARD)
                    Thread.sleep(500)
                }
            }
            
            errorResult("phone_scroll_to_find", "Could not find \"$searchText\" after $maxScrolls scroll(s)")
        } catch (e: Exception) {
            errorResult("phone_scroll_to_find", "Failed to scroll and find: ${e.message}")
        }
    }
    
    private fun findScrollableNode(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }
    
    private fun executePhoneTakeScreenshot(): ToolExecutionResult {
        return successResult("phone_take_screenshot", "Screenshot requires MediaProjection permission. Use vlm_task for screenshot analysis.")
    }

    // ======================== 辅助方法 ========================
    
    private fun successResult(toolName: String, summary: String): ToolExecutionResult.ContextResult {
        return ToolExecutionResult.ContextResult(
            toolName = toolName,
            summaryText = summary,
            previewJson = "{}",
            rawResultJson = "{}",
            success = true
        )
    }
    
    private fun errorResult(toolName: String, message: String): ToolExecutionResult.Error {
        return ToolExecutionResult.Error(toolName = toolName, message = message)
    }
    
    private fun getOmniAction(): OmniAction {
        val service = AssistsService.instance
            ?: throw IllegalStateException("Accessibility service is not running")
        return OmniAction(service)
    }
    
    private fun checkAccessibilityService(): String? {
        return if (AssistsService.instance == null) {
            "Accessibility service is not running. Please enable it in Settings."
        } else null
    }
}
