package io.github.alexswilliams.photosync

import java.nio.file.*
import java.time.*
import kotlin.io.path.*
import kotlin.streams.*

fun removeOldFilesFromArchive(archivePath: String, clock: InstantSource) {
    val cutOffInstant = LocalDate.ofInstant(clock.instant(), LONDON).minusMonths(6).atStartOfDay().atZone(LONDON).toInstant()
    val filesToDelete: List<Path> = Files.walk(Path(archivePath)).asSequence()
        .filter { it.isRegularFile() }
        .filter { it.getLastModifiedTime().toInstant().isBefore(cutOffInstant) }
        .toList()
    filesToDelete.forEach {
        val dateModified = LocalDate.ofInstant(it.getLastModifiedTime().toInstant(), LONDON)
        val age = Period.between(dateModified, LocalDate.ofInstant(clock.instant(), LONDON))
        println("Deleting $it as it is has age: $age")
        it.deleteExisting()
    }

    while (true) {
        val foldersToDelete: List<Path> = Files.walk(Path(archivePath)).asSequence()
            .filter { it.isDirectory() }
            .filterNot { it.toAbsolutePath().equals(Path(archivePath).absolute()) }
            .filter { Files.list(it.toAbsolutePath()).use { files -> files.findFirst().isEmpty } }
            .toList()
        foldersToDelete.forEach {
            println("Deleting $it as it is empty")
            it.deleteExisting()
        }
        if (foldersToDelete.isEmpty()) return
    }
}