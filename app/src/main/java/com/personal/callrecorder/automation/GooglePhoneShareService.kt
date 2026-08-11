package com.personal.callrecorder.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GooglePhoneShareService : AccessibilityService() {

    @Inject lateinit var automationController: AutomationController

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != AutomationController.DIALER_PKG && event.packageName != AutomationController.RESOLVER_PKG) {
            return
        }

        if (automationController.timedOut()) {
            automationController.fail("Automation timed out")
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowState(event)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> handleWindowContentChanged(event)
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleViewClicked(event)
            else -> {}
        }
    }

    override fun onInterrupt() {
        // Handle service interruption if needed
    }

    private fun handleWindowState(event: AccessibilityEvent) {
        val root = event.source ?: return

        when (automationController.step) {
            AutomationController.Step.LAUNCHING -> {
                if (event.packageName?.toString() == AutomationController.DIALER_PKG) {
                    automationController.markInDialer()
                }
                logNodeTree(root, 0)
            }
            AutomationController.Step.IN_DIALER -> {
                if (event.packageName?.toString() == AutomationController.RESOLVER_PKG) {
                    automationController.markInResolver()
                }
                logNodeTree(root, 0)
            }
            AutomationController.Step.IN_RESOLVER -> {
                // Placeholder for future navigation logic
                logNodeTree(root, 0)
            }
            else -> {}
        }
    }

    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val root = event.source ?: return

        when (automationController.step) {
            AutomationController.Step.IN_DIALER -> {
                // Placeholder for future navigation logic
                logNodeTree(root, 0)
            }
            AutomationController.Step.IN_RESOLVER -> {
                // Placeholder for future navigation logic
                logNodeTree(root, 0)
            }
            else -> {}
        }
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        val node = event.source ?: return

        when (automationController.step) {
            AutomationController.Step.IN_DIALER -> {
                // Placeholder for future navigation logic
                logNodeTree(node, 0)
            }
            AutomationController.Step.IN_RESOLVER -> {
                // Placeholder for future navigation logic
                logNodeTree(node, 0)
            }
            else -> {}
        }
    }

    private fun logNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        AccessibilityNodeFinder.logNodeTree(node, depth, { msg -> automationController.log(msg) })
    }

    private fun findNodeByViewId(node: AccessibilityNodeInfo, viewIdResourceName: String): AccessibilityNodeInfo? {
        return AccessibilityNodeFinder.findNodeByViewId(node, viewIdResourceName)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        return AccessibilityNodeFinder.findNodeByText(node, text)
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, contentDescription: String): AccessibilityNodeInfo? {
        return AccessibilityNodeFinder.findNodeByContentDescription(node, contentDescription)
    }
}
