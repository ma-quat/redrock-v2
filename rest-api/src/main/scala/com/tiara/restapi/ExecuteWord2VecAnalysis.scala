package com.tiara.restapi

/**
 * Created by barbaragomes on 4/20/16.
 */

import org.apache.spark.Logging
import play.api.libs.json._
import scala.concurrent.{Future, future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

object ExecuteWord2VecAndFrequencyAnalysis extends Logging{

  val objcName = "distance"
  val objDescription = Json.obj("content" -> Json.arr("word", "distance", "frequency"))

  def getResults(searchTerm: String, number: Int): Future[String] = {

    logInfo(s"Get synonyms for term: $searchTerm.")
    logInfo(s"Required terms: $number.")

    val result = future {getSynonymsFrequencyAndDistance(searchTerm.toLowerCase(),number)}

    result.recover{
      case e: Exception => logError("Could not execute request.", e); Json.stringify(Json.obj("forcegraph" -> JsNull))
    }

  }

  private def getSynonymsFrequencyAndDistance(searchTerm: String, number: Int): String = {

    val startTime = System.nanoTime()
    val synonyms = getSynonyms(searchTerm,number)

    var response:JsObject = buildResponse(searchTerm,false)

    if(synonyms != null && !synonyms.isEmpty){
      val frequency = getFrequency(synonyms.map{case (word, dist) => s""""$word""""})
      if(frequency != null){
        val result:List[JsArray] = (synonyms ++ frequency).groupBy(_._1)
          .values
          .map(result => Json.arr(result(0)._1.toString, result(0)._2.toString, result(1)._2.toString)).toList
        response = buildResponse(searchTerm,result = result)
      }
    }else if(synonyms.isEmpty){
      // If is empty, the search term was not present on the word2vec vocabulary
      response = buildResponse(searchTerm)
    }

    val elapsed = (System.nanoTime() - startTime) / 1e9
    logInfo(s"Get synonyms finished. Exectuion time: $elapsed")

    Json.stringify(response)
  }

  private def buildResponse(searchTerm: String, success: Boolean = true, result: List[JsArray] = null):JsObject = {
    val response = Json.obj("success" -> success) ++
      Json.obj("status" -> 0) ++
      Json.obj("searchTerm" -> searchTerm) ++
      objDescription
    if(!success)
      response ++ Json.obj(objcName -> JsNull)
    else if(result == null){
      response ++ Json.obj(objcName -> Json.arr())
    }else{
      response ++ Json.obj(objcName -> result)
    }
  }

  private def getFrequency(synonyms: Array[String]): Array[(String,Int)] ={
    try {
      val inString = synonyms.mkString("(", ",", ")")
      Word2Vec.frequency.where(s"word in $inString")
        .collect()
        .map(result => (result(0).toString, result(1).asInstanceOf[Int]))
    } catch{
      case e: Exception => logError("Could not get freq count", e); null
    }
  }

  private def getSynonyms(searchTerm: String, number: Int):Array[(String,Double)]={
    try {
      Word2Vec.model.findSynonyms(searchTerm, number)
        .map(result => (result._1, result._2))
    } catch {
      case notOnvac:IllegalStateException => {
        logInfo(s"$searchTerm is not on the vocabulary")
        Array.empty[(String,Double)]
      }
      case e:Exception => logError(s"Could not get synonyms for $searchTerm", e); null
    }
  }

}
