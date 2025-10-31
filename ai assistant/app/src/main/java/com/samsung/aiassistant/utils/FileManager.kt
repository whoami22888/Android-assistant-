package com.samsung.aiassistant.utils

import android.content.Context
import android.os.Environment
import com.samsung.aiassistant.data.database.AppDatabase
import com.samsung.aiassistant.data.model.FileOperation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class FileManager(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    
    suspend fun createFile(path: String, content: String = ""): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                
                // Create parent directories if they don't exist
                file.parentFile?.mkdirs()
                
                // Create the file
                val created = file.createNewFile()
                
                if (created && content.isNotEmpty()) {
                    file.writeText(content)
                }
                
                logOperation("CREATE", path, true, "File created successfully")
                
                FileOperationResult(
                    success = true,
                    message = "File created: ${file.absolutePath}",
                    path = file.absolutePath
                )
            } catch (e: Exception) {
                logOperation("CREATE", path, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to create file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun readFile(path: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                
                if (!file.exists()) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "File does not exist: $path"
                    )
                }
                
                val content = file.readText()
                
                logOperation("READ", path, true, "File read successfully")
                
                FileOperationResult(
                    success = true,
                    message = "File read successfully",
                    path = file.absolutePath,
                    content = content
                )
            } catch (e: Exception) {
                logOperation("READ", path, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to read file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun writeFile(path: String, content: String, append: Boolean = false): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                
                // Create parent directories if they don't exist
                file.parentFile?.mkdirs()
                
                if (append) {
                    file.appendText(content)
                } else {
                    file.writeText(content)
                }
                
                logOperation("WRITE", path, true, "Content written successfully")
                
                FileOperationResult(
                    success = true,
                    message = "Content written to file: ${file.absolutePath}",
                    path = file.absolutePath
                )
            } catch (e: Exception) {
                logOperation("WRITE", path, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to write file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun deleteFile(path: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                
                if (!file.exists()) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "File does not exist: $path"
                    )
                }
                
                val deleted = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                
                logOperation("DELETE", path, deleted, if (deleted) "File deleted" else "Failed to delete")
                
                FileOperationResult(
                    success = deleted,
                    message = if (deleted) "File deleted: $path" else "Failed to delete file: $path",
                    path = path
                )
            } catch (e: Exception) {
                logOperation("DELETE", path, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to delete file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun copyFile(sourcePath: String, destinationPath: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val source = File(sourcePath)
                val destination = File(destinationPath)
                
                if (!source.exists()) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "Source file does not exist: $sourcePath"
                    )
                }
                
                // Create parent directories if they don't exist
                destination.parentFile?.mkdirs()
                
                source.copyTo(destination, overwrite = true)
                
                logOperation("COPY", "$sourcePath -> $destinationPath", true, "File copied")
                
                FileOperationResult(
                    success = true,
                    message = "File copied to: ${destination.absolutePath}",
                    path = destination.absolutePath
                )
            } catch (e: Exception) {
                logOperation("COPY", "$sourcePath -> $destinationPath", false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to copy file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun moveFile(sourcePath: String, destinationPath: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val source = File(sourcePath)
                val destination = File(destinationPath)
                
                if (!source.exists()) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "Source file does not exist: $sourcePath"
                    )
                }
                
                // Create parent directories if they don't exist
                destination.parentFile?.mkdirs()
                
                val moved = source.renameTo(destination)
                
                logOperation("MOVE", "$sourcePath -> $destinationPath", moved, if (moved) "File moved" else "Failed to move")
                
                FileOperationResult(
                    success = moved,
                    message = if (moved) "File moved to: ${destination.absolutePath}" else "Failed to move file",
                    path = if (moved) destination.absolutePath else sourcePath
                )
            } catch (e: Exception) {
                logOperation("MOVE", "$sourcePath -> $destinationPath", false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to move file: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun listFiles(directoryPath: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(directoryPath)
                
                if (!directory.exists() || !directory.isDirectory) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "Directory does not exist: $directoryPath"
                    )
                }
                
                val files = directory.listFiles()?.map { file ->
                    FileInfo(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = file.isDirectory,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                } ?: emptyList()
                
                logOperation("LIST", directoryPath, true, "Listed ${files.size} items")
                
                FileOperationResult(
                    success = true,
                    message = "Found ${files.size} items",
                    path = directoryPath,
                    files = files
                )
            } catch (e: Exception) {
                logOperation("LIST", directoryPath, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to list files: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun createDirectory(path: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val directory = File(path)
                val created = directory.mkdirs()
                
                logOperation("CREATE_DIR", path, created, if (created) "Directory created" else "Directory already exists")
                
                FileOperationResult(
                    success = true,
                    message = if (created) "Directory created: $path" else "Directory already exists: $path",
                    path = directory.absolutePath
                )
            } catch (e: Exception) {
                logOperation("CREATE_DIR", path, false, e.message)
                FileOperationResult(
                    success = false,
                    message = "Failed to create directory: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    suspend fun getFileInfo(path: String): FileOperationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                
                if (!file.exists()) {
                    return@withContext FileOperationResult(
                        success = false,
                        message = "File does not exist: $path"
                    )
                }
                
                val info = FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    canRead = file.canRead(),
                    canWrite = file.canWrite()
                )
                
                FileOperationResult(
                    success = true,
                    message = "File info retrieved",
                    path = file.absolutePath,
                    files = listOf(info)
                )
            } catch (e: Exception) {
                FileOperationResult(
                    success = false,
                    message = "Failed to get file info: ${e.message}",
                    error = e
                )
            }
        }
    }
    
    fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    
    fun getAppStoragePath(): String {
        return context.filesDir.absolutePath
    }
    
    private suspend fun logOperation(operation: String, path: String, success: Boolean, details: String?) {
        try {
            database.fileOperationDao().insert(
                FileOperation(
                    operation = operation,
                    filePath = path,
                    success = success,
                    details = details
                )
            )
        } catch (e: Exception) {
            // Silently fail logging
        }
    }
}

data class FileOperationResult(
    val success: Boolean,
    val message: String,
    val path: String? = null,
    val content: String? = null,
    val files: List<FileInfo>? = null,
    val error: Throwable? = null
)

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val canRead: Boolean = true,
    val canWrite: Boolean = true
)
