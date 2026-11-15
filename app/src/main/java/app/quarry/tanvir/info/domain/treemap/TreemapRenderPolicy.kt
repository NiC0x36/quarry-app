package app.quarry.tanvir.info.domain.treemap

/** Rendering decisions only: no rectangle or byte count is changed by this policy. */
object TreemapRenderPolicy {
    const val MAX_GRADIENT_TILES = 256
    const val MAX_ANIMATED_TILES = 256
    const val MAX_LABEL_TILES = 128
    const val MAX_BORDER_TILES = 1024
    const val COLORS_PER_KIND = 32
    const val FLAT_PALETTE_SIZE = COLORS_PER_KIND * 2

    fun shouldAnimate(nodeCount: Int): Boolean = nodeCount in 1..MAX_ANIMATED_TILES

    fun labelBudget(visibleCount: Int): Int = visibleCount.coerceIn(0, MAX_LABEL_TILES)

    fun shouldDrawBorders(visibleCount: Int): Boolean = visibleCount in 1..MAX_BORDER_TILES

    /** A cache for one small map, never a cache that grows across folders. */
    fun gradientNodeIndices(nodes: List<TreemapNode>): IntArray {
        if (nodes.size > MAX_GRADIENT_TILES) return IntArray(0)
        return visibleNodeIndices(nodes, null)
    }

    fun flatColorIndex(path: String, isDirectory: Boolean): Int =
        (path.hashCode() and Int.MAX_VALUE) % COLORS_PER_KIND +
            if (isDirectory) COLORS_PER_KIND else 0

    /** Inverse of the centered scale and screen-space translation used by graphicsLayer. */
    fun contentViewport(
        canvasWidth: Float,
        canvasHeight: Float,
        contentScale: Float,
        offsetX: Float,
        offsetY: Float
    ): TreemapRect {
        if (!canvasWidth.isFinite() || !canvasHeight.isFinite() ||
            canvasWidth <= 0f || canvasHeight <= 0f
        ) return TreemapRect(0f, 0f, 0f, 0f)

        val scale = contentScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val translationX = offsetX.takeIf { it.isFinite() } ?: 0f
        val translationY = offsetY.takeIf { it.isFinite() } ?: 0f
        val centerX = canvasWidth / 2f
        val centerY = canvasHeight / 2f
        val viewport = TreemapRect(
            left = centerX + (-centerX - translationX) / scale,
            top = centerY + (-centerY - translationY) / scale,
            right = centerX + (canvasWidth - centerX - translationX) / scale,
            bottom = centerY + (canvasHeight - centerY - translationY) / scale
        )
        return if (isFinite(viewport)) viewport else TreemapRect(0f, 0f, canvasWidth, canvasHeight)
    }

    /** Only visible indices are retained; a zoomed map does not allocate a full-size copy. */
    fun visibleNodeIndices(nodes: List<TreemapNode>, viewport: TreemapRect?): IntArray {
        if (viewport != null && (!isFinite(viewport) || viewport.width <= 0f || viewport.height <= 0f)) {
            return IntArray(0)
        }
        var count = 0
        nodes.forEach { if (isVisible(it, viewport)) count++ }
        val indices = IntArray(count)
        var next = 0
        nodes.forEachIndexed { index, node ->
            if (isVisible(node, viewport)) indices[next++] = index
        }
        return indices
    }

    /**
     * Two constant-memory passes preserve the canvas's touch rules: prefer the smallest
     * direct hit, allow a much smaller neighbor within 75% of the touch radius, and use
     * a 150% radius only when there is no direct hit. Equal candidates keep input order.
     */
    fun hitTest(nodes: List<TreemapNode>, x: Float, y: Float, touchRadiusPx: Float): TreemapNode? {
        if (!x.isFinite() || !y.isFinite()) return null
        var directHit: TreemapNode? = null
        var directArea = Double.POSITIVE_INFINITY
        for (node in nodes) {
            if (!isPickable(node)) continue
            val rect = node.rect
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                val area = rect.width.toDouble() * rect.height
                if (area < directArea) {
                    directHit = node
                    directArea = area
                }
            }
        }

        val radius = touchRadiusPx.takeIf { it.isFinite() && it > 0f }?.toDouble() ?: 0.0
        val candidateRadius = radius * if (directHit != null) 0.75 else 1.5
        val maxDistanceSquared = candidateRadius * candidateRadius
        var nearest: TreemapNode? = null
        var nearestDistanceSquared = Double.POSITIVE_INFINITY
        for (node in nodes) {
            if (!isPickable(node)) continue
            val rect = node.rect
            if (directHit != null && rect.width.toDouble() * rect.height >= directArea * 0.3) continue
            val distanceSquared = distanceSquaredToRect(x.toDouble(), y.toDouble(), rect)
            if (distanceSquared < nearestDistanceSquared) {
                nearest = node
                nearestDistanceSquared = distanceSquared
            }
        }
        return if (nearestDistanceSquared <= maxDistanceSquared) nearest else directHit
    }

    private fun distanceSquaredToRect(x: Double, y: Double, rect: TreemapRect): Double {
        val dx = when {
            x < rect.left -> rect.left - x
            x > rect.right -> x - rect.right
            else -> 0.0
        }
        val dy = when {
            y < rect.top -> rect.top - y
            y > rect.bottom -> y - rect.bottom
            else -> 0.0
        }
        return dx * dx + dy * dy
    }

    private fun isVisible(node: TreemapNode, viewport: TreemapRect?): Boolean {
        val rect = node.rect
        if (!isPickable(node)) return false
        return viewport == null ||
            rect.right > viewport.left && rect.left < viewport.right &&
            rect.bottom > viewport.top && rect.top < viewport.bottom
    }

    private fun isPickable(node: TreemapNode): Boolean =
        node.size > 0L && isFinite(node.rect) && node.rect.width > 0f && node.rect.height > 0f

    private fun isFinite(rect: TreemapRect): Boolean =
        rect.left.isFinite() && rect.top.isFinite() && rect.right.isFinite() && rect.bottom.isFinite() &&
            rect.width.isFinite() && rect.height.isFinite()
}
