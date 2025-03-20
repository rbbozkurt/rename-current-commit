package com.rbbozkurt.renamecommit

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepositoryManager
import java.io.File

class RenameCommitAction : AnAction() {

    private val logger = Logger.getInstance(RenameCommitAction::class.java)

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()

        if (repository == null) {
            Messages.showErrorDialog(project, "No Git repository found.", "Error")
            return
        }

        // Ensure there are unpushed commits
        if (!hasUnpushedCommits(project, repository.root.path)) {
            Messages.showErrorDialog(
                project,
                "No unpushed commits found. You can only rename unpushed commits.",
                "Error"
            )
            return
        }

        // Fetch the last commit message
        val lastCommitMessage = getLastCommitMessage(project, repository.root.path)

        // Ask the user for a new commit message
        val newCommitMessage = Messages.showInputDialog(
            project,
            "Enter new commit message:",
            "Rename Current Commit",
            Messages.getQuestionIcon(),
            lastCommitMessage,
            object : InputValidator {
                override fun checkInput(inputString: String?): Boolean {
                    return !inputString.isNullOrBlank()
                }

                override fun canClose(inputString: String?): Boolean {
                    return checkInput(inputString)
                }
            }
        ) ?: return

        // Perform the commit rename
        renameLastCommit(project, repository.root.path, newCommitMessage)
    }

    private fun renameLastCommit(project: Project, repositoryPath: String, newMessage: String) {
        val git = Git.getInstance()
        val gitRoot = File(repositoryPath)

        val handler = GitLineHandler(project, gitRoot, GitCommand.COMMIT)
        handler.setSilent(false)
        handler.addParameters("--amend", "-m", newMessage)

        val result = git.runCommand(handler)
        if (result.success()) {
            Messages.showInfoMessage(project, "Commit message successfully changed.", "Success")
        } else {
            Messages.showErrorDialog(
                project,
                "Failed to rename commit: ${result.errorOutputAsJoinedString}",
                "Error"
            )
            logger.error("Git amend failed: ${result.errorOutputAsJoinedString}")
        }
    }

    private fun hasUnpushedCommits(project: Project, repositoryPath: String): Boolean {
        val git = Git.getInstance()
        val gitRoot = File(repositoryPath)

        val handler = GitLineHandler(project, gitRoot, GitCommand.LOG)
        handler.addParameters("--branches", "--not", "--remotes", "--oneline")

        val result = git.runCommand(handler)
        return result.success() && result.output.isNotEmpty()
    }

    private fun getLastCommitMessage(project: Project, repositoryPath: String): String {
        val git = Git.getInstance()
        val gitRoot = File(repositoryPath)

        val handler = GitLineHandler(project, gitRoot, GitCommand.LOG)
        handler.addParameters("-1", "--pretty=%B") // Fetch only the commit message

        val result = git.runCommand(handler)
        return if (result.success() && result.output.isNotEmpty()) {
            result.output.joinToString("\n") // Return commit message
        } else {
            "No commit message found"
        }
    }
}
