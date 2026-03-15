import java.nio.file.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.transform.CompileStatic

/**
 * Search and Replace Tool that processes files
 */
@CompileStatic
class SearchReplaceTool {

    /**
     * Entry point for the Search and Replace Tool.
     * * @param args Array containing:
     * 0: directoryPath (String),
     * 1: searchPattern (Regex String),
     * 2: replacementText (String),
     * 3: logFilePath (Optional String)
     */
    static void main(String[] args) {
        if (args.length < 3) {
            println """
            Usage:
            groovy ./src/SearchReplaceTool.groovy <directoryPath> <searchPattern> <replacementText> [logFilePath]

            Example:
            groovy ./src/SearchReplaceTool.groovy ./data "hello" "hey" ./log.txt
            """
            return
        }

        String dirPath = args[0]
        String searchPattern = args[1]
        String replacement = args[2]
        String logFile = args.length >= 4 ? args[3] : null

        List<String> modifiedFiles = []
        Path sourceDir = Paths.get(dirPath)

        if (!Files.exists(sourceDir)) {
            log(logFile,"Error: Directory $dirPath does not exist.")
            return
        }

        // Create a unique backup directory name based on timestamp
        Path backupDir = Paths.get(sourceDir.toString() + "-backup-" + System.currentTimeMillis())

        log(logFile, "--- SESSION START: ${LocalDateTime.now()} ---")
        log(logFile, "Pattern: $searchPattern")

        try {
            // Using a Stream-based walk to handle large directory trees efficiently
            Files.walk(sourceDir).withCloseable { stream ->
                stream.filter { Files.isRegularFile(it) }
                        .forEach { Path path ->
                            processFile(path, sourceDir, backupDir, searchPattern, replacement, logFile, modifiedFiles)
                        }
            }
        } catch (Exception e) {
            log(logFile, "FATAL ERROR: ${e.message}")
            e.printStackTrace()
        }

        if (modifiedFiles.size() > 0) {
            log(logFile, "Files Modified: ${modifiedFiles.size()}")
            modifiedFiles.each { String filePath ->
                log(logFile, " - $filePath")
            }
            log(logFile, "Backups created at: ${backupDir}")
        }else{
            log(logFile, "No files were modified.")
        }
        log(logFile, "Process Completed")
        log(logFile, "--- SESSION END: ${LocalDateTime.now()} ---")
    }

    /**
     * Processes an individual file using a temporary file stream.
     * * @param path The current file path being processed
     * @param sourceDir The root source directory
     * @param backupDir The directory where original files are stored
     * @param patternStr The regex pattern to search for
     * @param replacement The string to replace matches with
     * @param logFile Path to the log file (nullable)
     * @param modifiedFiles List to track the absolute paths of changed files
     */
    static void processFile(Path path, Path sourceDir, Path backupDir, String patternStr, String replacement, String logFile, List<String> modifiedFiles) {
        File originalFile = path.toFile()
        // Create a temp file in the same directory
        Path tempFilePath = path.resolveSibling("${path.fileName}.tmp")
        boolean fileModified = false
        Pattern pattern = Pattern.compile(patternStr)

        try {
            tempFilePath.withWriter { writer ->
                int lineNum = 1
                originalFile.eachLine { String line ->
                    Matcher matcher = pattern.matcher(line)
                    boolean lineHasMatch = false

                    while (matcher.find()) {
                        lineHasMatch = true
                        log(logFile, "MATCH: [${originalFile.name}] Line $lineNum: '${matcher.group()}' at pos ${matcher.start()}-${matcher.end()}")
                    }

                    if (lineHasMatch) {
                        writer.write(matcher.replaceAll(replacement))
                        fileModified = true
                    } else {
                        writer.write(line)
                    }
                    writer.write(System.lineSeparator())
                    lineNum++
                }
            }

            if (fileModified) {
                // Back up the original file
                backupFile(sourceDir, backupDir, path)
                // Replace the original with the modified temp file
                Files.move(tempFilePath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                modifiedFiles << originalFile.absolutePath
                log(logFile, "SAVED: ${originalFile.absolutePath}")
            } else {
                // Clean up the temp file if no changes were made
                Files.deleteIfExists(tempFilePath)
            }
        } catch (Exception e) {
            log(logFile, "ERROR processing ${originalFile.name}: ${e.message}")
            Files.deleteIfExists(tempFilePath)
        }
    }

    /**
     * Creates a backup of the original file, mirroring the source directory structure.
     * * @param sourceDir Root source directory
     * @param backupDir Root backup directory
     * @param filePath Path of the file to be backed up
     */
    static void backupFile(Path sourceDir, Path backupDir, Path filePath) {
        Path relativePath = sourceDir.relativize(filePath)
        Path backupPath = backupDir.resolve(relativePath)
        Files.createDirectories(backupPath.parent)
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Logs messages to the standard console and appends to a log file if provided.
     * * @param logFile Path to the destination log file (can be null)
     * @param message The string message to record
     */
    static void log(String logFile, String message) {
        String now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        String logEntry = "[$now] $message"

        println logEntry
        if (logFile) {
            new File(logFile).withWriterAppend { it.writeLine(logEntry) }
        }
    }
}