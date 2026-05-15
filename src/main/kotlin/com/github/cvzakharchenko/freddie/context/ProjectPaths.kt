package com.github.cvzakharchenko.freddie.context

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore

fun projectRelativePath(
    project: Project,
    file: VirtualFile,
): String {
    val baseDir = project.baseDir
    val relative = baseDir?.let { VfsUtilCore.getRelativePath(file, it, '/') }
    return (relative ?: file.path).replace('\\', '/')
}
