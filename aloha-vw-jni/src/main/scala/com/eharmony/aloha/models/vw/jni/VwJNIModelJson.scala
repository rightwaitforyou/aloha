package com.eharmony.aloha.models.vw.jni

import com.eharmony.aloha.factory.ScalaJsonFormats.listMapFormat
import com.eharmony.aloha.id.ModelIdentity
import com.eharmony.aloha.id.ModelIdentityJson.modelIdentityJsonFormat
import com.eharmony.aloha.io.fs.{FsInstance, FsType}
import com.eharmony.aloha.models.reg.ConstantDeltaSpline
import com.eharmony.aloha.models.reg.json.{Spec, SpecJson}
import com.eharmony.aloha.util.SimpleTypeSeq
import spray.json.DefaultJsonProtocol._
import spray.json._

import scala.collection.immutable.ListMap



/**
 * Components of the JSON protocol for VwJniModel
 */
trait VwJniModelJson extends SpecJson {

    /**
     *
     * Note that as is, this declaration will cause a compiler warning:
     *
     *     "The outer reference in this type test cannot be checked at run time."
     *
     * This is a known issue and is a scala bug.  See:
     * - https://issues.scala-lang.org/browse/SI-4440
     * - http://stackoverflow.com/questions/16450008/typesafe-swing-events-the-outer-reference-in-this-type-test-cannot-be-checked-a
     *
     * A solution that would remove the warning is to make the class not ''final''.  Not doing this just to remove a
     * warning.
     * @param params VW initialization parameters.  This is either a sequence of parameters that will be made into a
     *               single string by imploding the list with a " " separator or it is one string.  If None,
     * @param modelSource A [[VwJniModelSource]]
     */
    protected[this] case class Vw(modelSource: VwJniModelSource, params: Option[Either[Seq[String], String]] = Option(Right("")))

    /**
     * Note that as is, this declaration will cause a compiler warning:
     *
     *     "The outer reference in this type test cannot be checked at run time."
     *
     * This is a known issue and is a scala bug.  See:
     * - https://issues.scala-lang.org/browse/SI-4440
     * - http://stackoverflow.com/questions/16450008/typesafe-swing-events-the-outer-reference-in-this-type-test-cannot-be-checked-a
     *
     * A solution that would remove the warning is to make the class not ''final''.  Not doing this just to remove a
     * warning.
     * @param modelType The model type (Should be VwJNI).
     * @param modelId a model ID
     * @param features an map of features (whose iteration order is the declaration order).
     * @param vw an object for configuring the VwScorer object that will be embedded in the VwJniModel.
     * @param namespaces an map of namespace name to sequence of feature names in the namespace.
     * @param numMissingThreshold A threshold dictating how many missing features to allow before making
     *                            the prediction fail.  None means the threshold is &infin;.  If, when mapping
     *                            feature functions over the input, the resulting sequence contains more than
     *                            ''numMissingThreshold'' values that are empty Iterable values, then the
     *                            ''Features.missingOk'' value returned by ''constructFeatures'' will be
     *                            '''false'''; otherwise, it will be '''true'''.
     */
    protected[this] case class VwJNIAst(
        modelType: String,
        modelId: ModelIdentity,
        features: ListMap[String, Spec],
        vw: Vw,
        namespaces: Option[ListMap[String, Seq[String]]] = Some(ListMap.empty),
        numMissingThreshold: Option[Int] = None,
        notes: Option[Seq[String]] = None,
        spline: Option[ConstantDeltaSpline] = None,
        classLabels: Option[SimpleTypeSeq] = None)

    protected[this] implicit object VwFormat extends RootJsonFormat[Vw] {
        override def read(json: JsValue) = {
            val jso = json.asJsObject("Vw expected to be object")

            val creationTime = jso.getFields("creationTime") match {
                case Seq(JsNumber(t)) => t.toLongExact
                case _                => System.currentTimeMillis()
            }

            val modelVal = jso.getFields("model") match {
                case Seq(JsString(m)) => Some(m)
                case _                => None
            }

            val modelUrlVal = jso.getFields("modelUrl") match {
                case Seq(JsString(m)) => Some(m)
                case _                => None
            }

            // Default to VFS2.
            val fsType = jso.getFields("via") match {
                case Seq(via) => jso.convertTo(FsType.JsonReader("via"))
                case _        => FsType.vfs2
            }

            val params = jso.getFields("params") match {
                case Seq(p) => Option(p.convertTo[Either[Seq[String], String]])
                case Nil    => None
            }

            val paramStr = params.map(_.fold(_.mkString(" "), identity)) getOrElse ""

            val modelSource = (modelVal, modelUrlVal, fsType) match {
                case (None, Some(u), t)    => ExternallyDefinedVwModelSource(FsInstance.fromFsType(t)(u), paramStr, creationTime)
                case (Some(m), None, _)    => Base64EncodedBinaryVwModelSource(m, paramStr, creationTime)
                case (Some(m), Some(u), _) => throw new DeserializationException("Exactly one of 'model' and 'modelUrl' should be supplied. Both supplied: " + json.compactPrint)
                case (None, None, _)       => throw new DeserializationException("Exactly one of 'model' and 'modelUrl' should be supplied. Neither supplied: " + json.compactPrint)
            }

            Vw(modelSource, params)
        }

        override def write(v: Vw) = {
            val model = v.modelSource match {
                case Base64EncodedBinaryVwModelSource(b64, _, time) => Seq("model" -> JsString(b64),
                                                                           "creationDate" -> JsNumber(time))
                case ExternallyDefinedVwModelSource(fs, _,  time)   => Seq("modelUrl" -> JsString(fs.descriptor),
                                                                           "creationDate" -> JsNumber(time),
                                                                           "via" -> JsString(fs.fsType.toString))
            }

            val params = v.params.map(p => "params" -> p.toJson)
            val fields = model ++ params
            JsObject(scala.collection.immutable.ListMap(fields:_*))
        }
    }

    protected[this] final implicit val splineJsonFormat = jsonFormat(ConstantDeltaSpline, "min", "max", "knots")
    protected[this] final implicit val vwJNIAstFormat: RootJsonFormat[VwJNIAst] = jsonFormat9(VwJNIAst.apply)
}