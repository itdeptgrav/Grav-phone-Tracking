package com.personal.callrecorder.automation

import android.view.accessibility.AccessibilityNodeInfo

object AccessibilityNodeFinder {

    fun findNodeByViewId(node: AccessibilityNodeInfo, viewIdResourceName: String): AccessibilityNodeInfo? {
        if (node.viewIdResourceName == viewIdResourceName) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByViewId(child, viewIdResourceName)
                if (found != null) return found
            }
        }
        return null
    }

    fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByText(child, text)
                if (found != null) return found
            }
        }
        return null
    }

    fun findNodeByContentDescription(node: AccessibilityNodeInfo, contentDescription: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == contentDescription) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findNodeByContentDescription(child, contentDescription)
                if (found != null) return found
            }
        }
        return null
    }

    fun logNodeTree(node: AccessibilityNodeInfo, depth: Int, logger: (String) -> Unit) {
        val indent = " ".repeat(depth * 2)
        val className = node.className.toString()
        val text = node.text?.toString() ?: ""
        val contentDescription = node.contentDescription?.toString() ?: ""
        val viewIdResourceName = node.viewIdResourceName ?: ""
        val clickable = node.isClickable
        val enabled = node.isEnabled
        val visibleToUser = node.isVisibleToUser

        logger("$indent- className: $className, text: $text, contentDescription: $contentDescription, viewIdResourceName: $viewIdResourceName, clickable: $clickable, enabled: $enabled, visibleToUser: $visibleToUser")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                logNodeTree(child, depth + 1, logger)
            }
        }
    }

    fun findNearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node
        while (current.parent != null && !current.isClickable) {
            current = current.parent
        }
        return if (current.isClickable) current else null
    }
}
