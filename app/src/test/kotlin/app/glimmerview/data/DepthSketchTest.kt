package app.glimmerview.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthSketchTest {

    /** Scène de test : fond lointain (0,2) à gauche, sujet proche (0,7) à droite, bord net au milieu. */
    private fun twoPlanes(width: Int = 40, height: Int = 30): DepthSketch {
        val values = FloatArray(width * height) { if (it % width < width / 2) 0.2f else 0.7f }
        return DepthSketch(width, height, values.copyOf(), values.copyOf())
    }

    @Test
    fun selectionStopsAtDepthEdges() {
        val sketch = twoPlanes()
        assertTrue(sketch.select(30f, 15f))
        assertNotNull(sketch.selection)
        val weights = sketch.selection!!
        // Bien à l'intérieur du sujet : sélectionné ; bien dans le fond : pas du tout.
        assertEquals(1f, weights[15 * 40 + 35], 1e-6f)
        assertEquals(0f, weights[15 * 40 + 5], 1e-6f)
    }

    @Test
    fun nearerOnSelectionOnlyMovesTheSubject() {
        val sketch = twoPlanes()
        sketch.select(30f, 15f)
        sketch.applyToSelection(SketchTool.NEARER)
        assertEquals(0.7f + DepthSketch.SELECTION_STEP, sketch.depth[15 * 40 + 35], 1e-4f)
        assertEquals(0.2f, sketch.depth[15 * 40 + 5], 1e-6f)
        assertTrue(sketch.edited)
    }

    @Test
    fun undoAndRedoRestoreStates() {
        val sketch = twoPlanes()
        sketch.select(30f, 15f)
        sketch.applyToSelection(SketchTool.FARTHER)
        val after = sketch.depth[15 * 40 + 35]
        assertTrue(sketch.undo())
        assertEquals(0.7f, sketch.depth[15 * 40 + 35], 1e-4f)
        assertTrue(sketch.redo())
        assertEquals(after, sketch.depth[15 * 40 + 35], 1e-4f)
        assertFalse(sketch.canRedo)
    }

    @Test
    fun brushIsStrongestAtItsCenterAndStopsAtItsRadius() {
        val sketch = twoPlanes()
        sketch.checkpoint()
        sketch.dab(5f, 15f, 4f, SketchTool.NEARER, 0f)
        val center = sketch.depth[15 * 40 + 5] - 0.2f
        val side = sketch.depth[15 * 40 + 7] - 0.2f
        val outside = sketch.depth[15 * 40 + 12] - 0.2f
        assertTrue(center > side)
        assertTrue(side > 0f)
        assertEquals(0f, outside, 1e-6f)
    }

    @Test
    fun eraseReturnsToTheOriginal() {
        val sketch = twoPlanes()
        sketch.select(30f, 15f)
        sketch.applyToSelection(SketchTool.NEARER)
        sketch.applyToSelection(SketchTool.ERASE)
        assertEquals(0.7f, sketch.depth[15 * 40 + 35], 1e-4f)
    }

    @Test
    fun tighterSelectionStopsAtGentlerSlopes() {
        // Pente douce : 0,009 par pixel, sous le seuil normal mais au-dessus du seuil resserré.
        val width = 40
        val values = FloatArray(width * 10) { (it % width) * 0.009f }
        val sketch = DepthSketch(width, 10, values.copyOf(), values.copyOf())
        assertTrue(sketch.select(5f, 5f))
        val wide = sketch.selection!!.count { it > 0.5f }
        assertTrue(sketch.isSelected(5f, 5f))
        sketch.select(5f, 5f, tightness = 1)
        val tight = sketch.selection?.count { it > 0.5f } ?: 0
        assertTrue("la zone resserrée doit être plus petite ($tight < $wide)", tight < wide)
    }

    @Test
    fun tinyRegionsAreNotSelected() {
        val width = 20
        val values = FloatArray(width * width) { 0.2f }
        values[10 * width + 10] = 0.9f // un pixel isolé, très proche
        val sketch = DepthSketch(width, width, values.copyOf(), values.copyOf())
        assertFalse(sketch.select(10f, 10f))
        assertNull(sketch.selection)
    }
}
