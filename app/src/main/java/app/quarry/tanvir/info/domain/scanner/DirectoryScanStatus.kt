package app.quarry.tanvir.info.domain.scanner

/** Coverage of a directory, independent of the bytes successfully indexed below it. */
object DirectoryScanStatus {
    const val COMPLETE = "COMPLETE"
    const val PARTIAL = "PARTIAL"
    const val UNREADABLE = "UNREADABLE"
    const val EXCLUDED = "EXCLUDED"
}
