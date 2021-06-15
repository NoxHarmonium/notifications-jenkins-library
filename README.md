# common-jenkins-library

A library that has functions shared between all Jenkins pipeline builds

## Steps

### runBuild

Wraps build steps with Error handling and Slack notifications.

#### Parameters

- jiraBaseUrl: The base URL of the Jira instance that the Slack notifications will link to if a Jira ticket is mentioned in a branch name
- mainChannel: The Slack channel were most of the Slack notifications are sent (build start/end/error)
- priorityChannel: The Slack channel where build failures for "dist" branches are sent
- isDistBranch: If set to true, failures will be sent to the priority Slack channel
- currentBuild: The global currentBuild object which used by the runBuild task to update the build status

#### Example

```groovy

final isDistBranch = env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'develop'

runBuild(
    jiraBaseUrl: "https://jira.somedomain.com.au",
    mainChannel: "project-bots",
    priorityChannel: "project-dev",
    isDistBranch: isDistBranch,
    currentBuild: currentBuild
  ) {
    stage('Checkout code') {
      checkout scm
    }
  }
```
