/*
 * Toolform-compatible Jenkins 2 Pipeline to notify Slack about build status
 */

import net.sf.json.JSONArray
import net.sf.json.JSONObject

@NonCPS def extractJiraDetailsFromBranchName(branchName, jiraBaseUrl) {
    def jiraTicketNumberMatcher = branchName =~ /([A-Z]+-\d+)/
    if (jiraTicketNumberMatcher) {
        def ticketNumber = jiraTicketNumberMatcher[0][1]
        def url = "${jiraBaseUrl}/browse/${ticketNumber}"
        return [true, ticketNumber, url]
    } else {
        return [false, null, null]
    }
}

@NonCPS def extractPRDetailsFromBranchName(branchName, jiraBaseUrl) {
    def prNumberMatcher = branchName =~ /PR-(\d+)/
    if (prNumberMatcher) {
        def prNumber = prNumberMatcher[0][1]
        def url = env.CHANGE_URL
        return [true, prNumber, url]
    } else {
        return [false, null, null]
    }
}

def notifySlack(jiraBaseUrl, mainChannel, priorityChannel, isDistBranch, String buildStatus = 'STARTED') {
    // Build status of null means success.
    buildStatus = buildStatus ?: 'SUCCESS'
    final boolean alertDevChannel = buildStatus != 'SUCCESS' && buildStatus != 'STARTED' && isDistBranch
    final greyColor = '#D4DADF'

    def color

    if (buildStatus == 'STARTED') {
        color = greyColor
    } else if (buildStatus == 'SUCCESS') {
        color = 'good'
    } else if (buildStatus == 'UNSTABLE') {
        color = 'warning'
    } else {
        color = 'danger'
    }

    def (hasAssociatedJiraTicket, jiraTicketNumber, jiraTicketUrl) = extractJiraDetailsFromBranchName(env.BRANCH_NAME, jiraBaseUrl)
    def (hasAssociatedPr, prNumber, prUrl) = extractPRDetailsFromBranchName(env.BRANCH_NAME, jiraBaseUrl)
    def unescapedJobName = java.net.URLDecoder.decode(env.JOB_NAME, 'UTF-8')

    def defaultFields = [
        ['title': 'Job Name', 'value': "<${env.JOB_URL}|${unescapedJobName}>".toString(), 'short': true],
        ['title': 'Build Number', 'value': "<${env.BUILD_URL}|${env.BUILD_NUMBER}>".toString(), 'short': true],
        ['title': 'Branch', 'value': env.BRANCH_NAME, 'short': true]
    ]
    def jiraFields = hasAssociatedJiraTicket ?
        ['title': 'Jira Ticket', 'value': "<${jiraTicketUrl}|${jiraTicketNumber}>".toString(), 'short': true] : []
    def prFields = hasAssociatedPr ?
        ['title': 'Pull Request', 'value': "<${prUrl}|PR-${prNumber}>".toString(), 'short': true] : []

    def attachment = [
        'title': 'Jenkins Build Status',
        'text': "Build status is: ${buildStatus}".toString(),
        'fallback': "Build status is: ${buildStatus}".toString(),
        'color': color,
        'fields': defaultFields + jiraFields + prFields,
        'footer': 'Jenkins'
    ]

    JSONObject attachmentJson = JSONObject.fromObject(attachment)
    slackSend(channel: alertDevChannel ? priorityChannel : mainChannel, attachments: "[${attachmentJson.toString()}]")
}


def call(Map config, Closure body) {
    final jiraBaseUrl = config.jiraBaseUrl // https://somejira.com
    assert jiraBaseUrl?.trim() : "string value 'jiraBaseUrl' must be passed in config map"
    final mainChannel = config.mainChannel // project-bots
    assert mainChannel?.trim() : "string value 'mainChannel' must be passed in config map"
    final priorityChannel = config.priorityChannel ?: mainChannel // project-dev
    final isDistBranch = config.isDistBranch
    assert isDistBranch instanceof Boolean : "boolean value 'isDistBranch' must be passed in config map"
    final currentBuild = config.currentBuild
    assert currentBuild : "'currentBuild' global variable from pipeline must be passed in config map"

    try {
        stage('Notify Slack') {
            notifySlack(jiraBaseUrl, mainChannel, priorityChannel, isDistBranch)
        }

        body()

        stage('Mark build as success (if not already set)') {
            if (currentBuild.result == null){
                currentBuild.result = "SUCCESS"
            }
        }
    } catch (InterruptedException e) {
        // Build interupted
        currentBuild.result = "ABORTED"
        throw e
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e
    } finally {
        // Success or failure, always send notifications
        notifySlack(jiraBaseUrl, mainChannel, priorityChannel, isDistBranch, currentBuild.result)
    }


}
