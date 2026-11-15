package app.quarry.tanvir.info.domain.file

/** Fixed product boundary: this build inspects user storage but never changes its files. */
object AnalysisOnlyPolicy {
    const val enabled = true
    const val message = "This analysis-only build cannot change, open, or share your files."

    fun <T> denied(): Result<T> = Result.failure(UnsupportedOperationException(message))
    fun deniedBatch(paths: List<String>): Map<String, Boolean> = paths.associateWith { false }
}
