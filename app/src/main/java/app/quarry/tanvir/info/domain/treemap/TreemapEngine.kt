package app.quarry.tanvir.info.domain.treemap

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlin.math.max
import kotlin.math.min

object TreemapEngine {

    /** Builds the immediate children of [rootPath], retaining zero-byte entries as metadata. */
    fun buildTree(
        files: List<FileEntity>,
        rootPath: String = ""
    ): TreemapNode {
        val root = normalizePath(rootPath)
        val filtered = files
            .filter { isDescendant(it.path, root) }
            .distinctBy { it.path }

        // Group by path segment even when some intermediate directory records are missing.
        // In particular, /Download must never include its sibling /Downloads.
        val grouped = filtered.groupBy { entity ->
            val path = normalizePath(entity.path)
            val relative = if (root.isEmpty() || root == "/") {
                path.trimStart('/')
            } else {
                path.removePrefix("$root/")
            }
            val firstSegment = relative.substringBefore('/')
            if (root.isEmpty() || root == "/") "/$firstSegment" else "$root/$firstSegment"
        }

        val children = grouped.map { (childPath, descendants) ->
            val directEntity = descendants.firstOrNull { normalizePath(it.path) == childPath }
            if (directEntity != null) {
                createNodeFromEntity(directEntity, descendants)
            } else {
                TreemapNode(
                    path = childPath,
                    name = childPath.substringAfterLast('/'),
                    size = sumSizes(descendants.filterNot { it.isDirectory }.map { it.size }),
                    isDirectory = true,
                    category = StorageCategory.OTHER
                )
            }
        }.sortedByDescending { it.size }

        return TreemapNode(
            path = root.ifEmpty { "/" },
            name = if (root.isEmpty() || root == "/") "Storage" else root.substringAfterLast('/'),
            size = sumSizes(children.map { it.size }),
            isDirectory = true,
            category = StorageCategory.OTHER,
            children = children
        )
    }

    private fun createNodeFromEntity(entity: FileEntity, files: List<FileEntity>): TreemapNode {
        val size = if (entity.isDirectory && entity.size <= 0L) {
            sumSizes(files.filter { !it.isDirectory && isDescendant(it.path, entity.path) }.map { it.size })
        } else {
            entity.size.coerceAtLeast(0L)
        }
        return TreemapNode(
            path = entity.path,
            name = entity.name,
            size = size,
            isDirectory = entity.isDirectory,
            category = if (entity.isDirectory) StorageCategory.OTHER else StorageCategory.fromExtension(entity.extension)
        )
    }

    private fun normalizePath(path: String): String =
        path.trimEnd('/').ifEmpty { if (path.startsWith('/')) "/" else "" }

    private fun isDescendant(path: String, rootPath: String): Boolean {
        val root = normalizePath(rootPath)
        val candidate = normalizePath(path)
        return when (root) {
            "" -> candidate.isNotEmpty() && candidate != "/"
            "/" -> candidate.startsWith('/') && candidate.length > 1
            else -> candidate.startsWith("$root/")
        }
    }

    private fun sumSizes(sizes: List<Long>): Long = sizes.fold(0L) { sum, size ->
        sum + size.coerceIn(0L, Long.MAX_VALUE - sum)
    }

    /**
     * Allocates rectangle areas strictly in proportion to bytes. Zero-byte entries have no
     * area; they remain available in the list. Touch targets and zoom belong to the canvas
     * and must not change the measured areas.
     */
    fun layoutSquarified(items: List<TreemapNode>, bounds: TreemapRect): List<TreemapNode> {
        if (!bounds.left.isFinite() || !bounds.top.isFinite() ||
            !bounds.right.isFinite() || !bounds.bottom.isFinite()
        ) return emptyList()

        val rect = LayoutRect(
            bounds.left.toDouble(), bounds.top.toDouble(),
            bounds.right.toDouble(), bounds.bottom.toDouble()
        )
        if (rect.width <= 0.0 || rect.height <= 0.0) return emptyList()

        val sorted = items.filter { it.size > 0L }.sortedByDescending { it.size }
        if (sorted.isEmpty()) return emptyList()

        // Sum as Double so even synthetic inputs exceeding Long.MAX_VALUE cannot overflow.
        val totalBytes = sorted.sumOf { it.size.toDouble() }
        val totalArea = rect.width * rect.height
        val areas = sorted.map { it.size.toDouble() / totalBytes * totalArea }
        val result = mutableListOf<TreemapNode>()
        var remaining = rect
        var first = 0

        while (first < sorted.size) {
            if (remaining.width <= 0.0 || remaining.height <= 0.0) {
                // Subpixel areas may round to zero at an extreme size ratio. Never invent
                // a visible minimum or divide by zero; the list still exposes these items.
                val collapsed = TreemapRect(
                    remaining.left.toFloat(), remaining.top.toFloat(),
                    remaining.left.toFloat(), remaining.top.toFloat()
                )
                sorted.drop(first).forEach { result += it.copy(rect = collapsed) }
                break
            }

            val shortSide = min(remaining.width, remaining.height)
            var end = first + 1
            var worst = worstAspectRatio(areas, first, end, shortSide)
            while (end < sorted.size) {
                val candidate = worstAspectRatio(areas, first, end + 1, shortSide)
                if (candidate > worst) break
                worst = candidate
                end++
            }
            remaining = layoutRow(sorted, areas, first, end, remaining, result)
            first = end
        }
        return result
    }

    private data class LayoutRect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double
    ) {
        val width: Double get() = right - left
        val height: Double get() = bottom - top
    }

    private fun worstAspectRatio(areas: List<Double>, first: Int, end: Int, length: Double): Double {
        var total = 0.0
        var smallest = Double.POSITIVE_INFINITY
        var largest = 0.0
        for (i in first until end) {
            total += areas[i]
            smallest = min(smallest, areas[i])
            largest = max(largest, areas[i])
        }
        if (total <= 0.0 || smallest <= 0.0 || length <= 0.0) return Double.MAX_VALUE
        val lengthSquared = length * length
        val totalSquared = total * total
        return max(lengthSquared * largest / totalSquared, totalSquared / (lengthSquared * smallest))
    }

    private fun layoutRow(
        items: List<TreemapNode>,
        areas: List<Double>,
        first: Int,
        end: Int,
        rect: LayoutRect,
        result: MutableList<TreemapNode>
    ): LayoutRect {
        var rowArea = 0.0
        for (i in first until end) rowArea += areas[i]
        val horizontal = rect.width < rect.height
        val finalRow = end == items.size

        if (horizontal) {
            val bottom = if (finalRow) rect.bottom else (rect.top + rowArea / rect.width).coerceIn(rect.top, rect.bottom)
            var left = rect.left
            for (i in first until end) {
                val right = if (i == end - 1) rect.right else (left + rect.width * areas[i] / rowArea).coerceIn(left, rect.right)
                result += items[i].copy(rect = TreemapRect(left.toFloat(), rect.top.toFloat(), right.toFloat(), bottom.toFloat()))
                left = right
            }
            return rect.copy(top = bottom)
        }

        val right = if (finalRow) rect.right else (rect.left + rowArea / rect.height).coerceIn(rect.left, rect.right)
        var top = rect.top
        for (i in first until end) {
            val bottom = if (i == end - 1) rect.bottom else (top + rect.height * areas[i] / rowArea).coerceIn(top, rect.bottom)
            result += items[i].copy(rect = TreemapRect(rect.left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat()))
            top = bottom
        }
        return rect.copy(left = right)
    }
}
