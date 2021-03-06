package com.eharmony.aloha.factory

import java.io.StringReader

import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.Test
import org.junit.Assert._

import org.apache.commons.io.input.ReaderInputStream

import spray.json.DefaultJsonProtocol.DoubleJsonFormat

import com.eharmony.aloha.score.conversions.rich.RichScore
import com.eharmony.aloha.score.conversions.ScoreConverter.Implicits.DoubleScoreConverter

import com.eharmony.aloha.io.sources.{ReadableSource, ReaderReadableSource, InputStreamReadableSource, StringReadableSource}
import com.eharmony.aloha.io.sources.ReadableSourceConverters.Implicits._
import com.eharmony.aloha.io.sources.ReadableSourceConverters.StringImplicits.stringToStringReadableConverter

import com.eharmony.aloha.util.ICList

/**
 * This is the scala counterpart of the com.eharmony.aloha.factory.JavaDefaultModelFactoryTest.  This is mainly
 * for illustrative purposes but also shows that the well-known scala list syntax and the implicit conversions work.
 * This allows the code to appear cleaner.  For example, see ''testMultipleFromDefaultFactory'' in both classes for the
 * difference.
 */
@RunWith(classOf[BlockJUnit4ClassRunner])
class ScalaDefaultModelFactoryTest {
    import ScalaDefaultModelFactoryTest._

    @Test def testErrorModelFromDefaultFactory() {
        val model = defaultFactory.fromString(ErrorModelJson).get
        assertEquals(ErrorModelName, model.modelId.getName())
        assertEquals(ErrorModelId, model.modelId.getId())

        val score = model.score(null)

        assertFalse(score.hasScore)
        assertTrue(score.hasError)
        assertEquals(1, score.getError.getMessagesCount)
        assertEquals(ErrorModelMsg, score.getError.getMessages(0))
        assertEquals(0, score.getError.getMissingFeatures.getNamesCount)
    }

    @Test def testConstantModelFromDefaultFactory() {
        val model = defaultFactory.fromString(ConstModelJson).get

        assertEquals(ConstModelName, model.modelId.getName())
        assertEquals(ConstModelId, model.modelId.getId())

        val score = model.score(null)
        val ds1 = score.relaxed.asDouble.get
        assertEquals(ConstModelVal, ds1, 0)

        val ds2 = model.apply(null).get
        assertEquals(ConstModelVal, ds2, 0)
    }

    @Test def testMultipleFromDefaultFactory() {
        val constModel = defaultFactory.fromString(ConstModelJson).get
        val errModel = defaultFactory.fromString(ErrorModelJson).get

        // We can just use the normal list-style syntax because of the ReadableTypeList interface.
        // To use the automatic type conversion, we need to import the implicits in ReadableTypeConverters.
        val rtl =
                getInputStream(ErrorModelJson) ::
                InputStreamReadableSource(getInputStream(ConstModelJson)) ::
                getReader(ErrorModelJson) ::
                ReaderReadableSource(getReader(ConstModelJson)) ::
                ErrorModelJson ::
                StringReadableSource(ConstModelJson) ::
                ICList.empty[ReadableSource]

        // Note we can use the ReadableTypeList because the implicit conversion exists in the companion object
        // of ReadableTypeList.
        val tries = defaultFactory.fromMultipleSources(rtl.list)

        // Test the models are as expected.
        tries.zipWithIndex foreach { case (t, i) => {
            val m = t.get
            val exp = if (0 == i % 2) errModel else constModel
            assertEquals("on test " + i + ":", exp, m)
        }}
    }
}

object ScalaDefaultModelFactoryTest {
    val ErrorModelName = "gsaj"
    val ErrorModelId = 5625l
    val ErrorModelMsg = "bad stuff happening"
    val ErrorModelJson = s"""{"modelType": "Error", "modelId": {"id": $ErrorModelId, "name": "$ErrorModelName"}, "errors": ["$ErrorModelMsg"]}"""

    val ConstModelName = "gaoas"
    val ConstModelId = 521l
    val ConstModelVal = 1234.0
    val ConstModelJson = s"""{"modelType": "Constant", "modelId": {"id": $ConstModelId, "name": "$ConstModelName"}, "value": $ConstModelVal}"""

    // Create the model factory.  Notice the implicit reflection information is automatically injected.
    val defaultFactory = ModelFactory.defaultFactory.toTypedFactory[Map[String, Long], Double]

    def getInputStream(json: String) = new ReaderInputStream(getReader(json))
    def getReader(json: String) = new StringReader(json)
}
