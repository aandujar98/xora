package com.arcadia.shell.launcher.music

import java.io.File

/**
 * Local album-art helpers for folder-scanned mp3/wav libraries.
 *
 * Game OSTs are often a directory of dumps plus a `cover.jpg`, or MP3s with an embedded picture
 * and WAVs with neither. The Music browser tries these before any network scrape.
 */
object MusicFolderArt {
    fun findCover(dir: File): String? {
        if (!dir.isDirectory) return null
        val files = dir.listFiles() ?: return null
        return files.firstOrNull { file ->
            file.isFile && file.length() > 64L && file.name.lowercase() in COVER_NAMES
        }?.absolutePath
    }

    fun extensionForImage(bytes: ByteArray): String {
        if (bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        if (bytes.size >= 3 && bytes[0] == 0x47.toByte() &&
            bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        ) {
            return "gif"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte()
        ) {
            return "webp"
        }
        return "jpg"
    }

    private val COVER_NAMES = setOf(
        "cover.jpg",
        "cover.jpeg",
        "cover.png",
        "cover.webp",
        "folder.jpg",
        "folder.jpeg",
        "folder.png",
        "album.jpg",
        "album.jpeg",
        "album.png",
        "front.jpg",
        "front.jpeg",
        "front.png",
        "artwork.jpg",
        "artwork.jpeg",
        "artwork.png",
        "albumart.jpg",
        "albumart.jpeg",
        "albumart.png",
    )
}
