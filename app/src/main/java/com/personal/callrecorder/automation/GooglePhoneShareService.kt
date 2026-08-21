package com.personal.callrecorder.automation

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GooglePhoneShareService : AccessibilityService() {

    @Inject lateinit var automationController: AutomationController

    private var lastShowMoreAttemptAt = 0L
    private var recordClickedForCall = false
    private var latestRecordedCallClicked = false
    private var shareAudioClicked = false

private val serviceScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)


    override fun onServiceConnected() {
    super.onServiceConnected()

    serviceScope.launch {
        automationController.launchRequests.collect {
            automationController.log(
                "AccessibilityService: launching Google Phone"
            )

            val intent =
                packageManager.getLaunchIntentForPackage(
                    AutomationController.DIALER_PKG
                )?.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )

            if (intent == null) {
                automationController.fail("Google Phone not installed")
                return@collect
            }

            runCatching {
                startActivity(intent)
            }.onFailure {
                automationController.fail(
                    "AccessibilityService launch failed: ${it.message}"
                )
            }
        }
    }
}

override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
}

override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != AutomationController.DIALER_PKG && event.packageName != AutomationController.RESOLVER_PKG) {
            return
        }

        if (event.packageName?.toString() == AutomationController.DIALER_PKG) {
        maybeStartNativeRecording()

        if (automationController.step == AutomationController.Step.LAUNCHING) {
            latestRecordedCallClicked = false
            shareAudioClicked = false
            automationController.markInDialer()
            automationController.log("Post-call: entered Google Phone")
        }
    }

    if (event.packageName?.toString() == AutomationController.RESOLVER_PKG &&
        automationController.running
    ) {
        automationController.markInResolver()
        automationController.log("Post-call: entered share sheet")
        maybeChooseCallRecorder()
    }

    // Diagnostic: inspect Google Phone UI even while automation is IDLE.
    if (event.packageName?.toString() == AutomationController.DIALER_PKG &&
        (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
         event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
         event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    ) {
        rootInActiveWindow?.let { logNodeTree(it, 0) }
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
                    latestRecordedCallClicked = false
                    shareAudioClicked = false
                    automationController.markInDialer()
                    maybeClickLatestRecordedCall()
                    maybeClickShareAudio()
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
                maybeClickLatestRecordedCall()
                maybeClickShareAudio()
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

    private fun maybeClickLatestRecordedCall() {
    if (latestRecordedCallClicked) return

    val root = rootInActiveWindow ?: return

    val cards = root.findAccessibilityNodeInfosByViewId(
        "com.google.android.dialer:id/call_log_entry_card"
    )

    // IMPORTANT: only ever consider the TOP call-log card.
    // If the newest call recording is not ready yet, wait.
    // Never fall back to an older recording.
    val latestCard = cards.firstOrNull() ?: return

    val description =
        latestCard.contentDescription?.toString().orEmpty()

    if (!latestCard.isVisibleToUser) return

    if (!description.contains("In Call recording", ignoreCase = true)) {
        automationController.log(
            "Post-call: newest call not finalized yet — waiting"
        )
        return
    }

    if (latestCard.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        latestRecordedCallClicked = true
        automationController.log(
            "Post-call: clicked absolute newest recording"
        )
    }
}


private fun maybeClickShareAudio() {
    if (shareAudioClicked) return
    if (!latestRecordedCallClicked) return

    val root = rootInActiveWindow ?: return

    val cards = root.findAccessibilityNodeInfosByViewId(
        "com.google.android.dialer:id/call_log_entry_card"
    )

    val latestCard = cards.firstOrNull() ?: return

    val share =
        AccessibilityNodeFinder.findNodeByViewId(
            latestCard,
            "com.google.android.dialer:id/playback_share"
        )
            ?: AccessibilityNodeFinder.findNodeByContentDescription(
                latestCard,
                "Share audio file"
            )
            ?: return

    val clickable =
        AccessibilityNodeFinder.findNearestClickableAncestor(share)
            ?: share.takeIf { it.isClickable }
            ?: return

    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        shareAudioClicked = true
        automationController.log(
            "Post-call: clicked Share on absolute newest recording"
        )
    }
}

private fun maybeChooseCallRecorder() {
    val root = rootInActiveWindow ?: return

    val node = findNodeByText(root, "Call Recorder") ?: return

    val clickable =
        AccessibilityNodeFinder.findNearestClickableAncestor(node)
            ?: node.takeIf { it.isClickable }
            ?: return

    if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
        automationController.log("Post-call: selected Call Recorder")
        automationController.succeed()
    }
}

private fun maybeStartNativeRecording() {
    val root = rootInActiveWindow ?: return

    val endCallNode = findNodeByContentDescription(root, "End call")

    if (endCallNode == null) {
        lastShowMoreAttemptAt = 0L
        recordClickedForCall = false
        return
    }

    if (recordClickedForCall) return

    val recordNode =
        findNodeByContentDescription(root, "Record")
            ?: findNodeByText(root, "Record")

    if (recordNode != null) {
        val clickable =
            AccessibilityNodeFinder.findNearestClickableAncestor(recordNode)

        if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            recordClickedForCall = true
            automationController.log("Auto-record: clicked Record")
        }
        return
    }

    if (findNodeByContentDescription(root, "Close 'More' menu") != null) {
        return
    }

    val now = System.currentTimeMillis()
    if (now - lastShowMoreAttemptAt < 1_000L) return

    val showMoreNode = findNodeByContentDescription(root, "Show more")
    val clickable = showMoreNode?.let {
        AccessibilityNodeFinder.findNearestClickableAncestor(it)
    }

    if (clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
        lastShowMoreAttemptAt = now
        automationController.log("Auto-record: clicked Show more")
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
