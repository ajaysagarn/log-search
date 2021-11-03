package com.ajsa.server.rest

import HelperUtils.LogSearchUtils.config
import HelperUtils.{CreateLogger, LogSearchUtils, ObtainConfigReference}
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpStatus
import software.amazon.awssdk.services.s3.model.S3Object

import java.util
import collection.JavaConverters._
import java.io.InputStream
import scala.io.Source
import play.api.libs.json._

class HandleRestRequest
object HandleRestRequest{

  val mapper: ObjectMapper = new ObjectMapper()
  val logger = CreateLogger(classOf[HandleRestRequest])
  val config = ObtainConfigReference("application.conf","CONFIG").get

  def handleRestRequest(input: Map[String,Any]):APIGatewayProxyResponseEvent = {
    val st = input.get("st").get.toString
    logger.info("start time = {}", st)
    val et = input.get("et").get.toString
    logger.info("end time = {}", et)
    val pattern = input.get("pattern")

    //Fetch the file name under the input folder in the s3 bucket
    val s3ObjectKey: Option[S3Object] = LogSearchUtils.getLogFileKey(config.getString("S3_BUCKET"))

    //Check if the input fodlder is empty and return 404 if empty
    if(s3ObjectKey.isEmpty){
        return  new APIGatewayProxyResponseEvent()
          .withStatusCode(HttpStatus.SC_OK)
          .withHeaders(Map("Content-Type" -> "application/json").asJava)
          .withIsBase64Encoded(true)
          .withBody( Json.obj("status" -> HttpStatus.SC_OK, "message" -> "Logs found within the given time range").toString())
    }

    // Fetch the filestream from s3
    val logStream: InputStream =  LogSearchUtils.getS3InputStream(config.getString("S3_BUCKET"),s3ObjectKey.get.key())

    val lines: Iterator[String] =  Source.fromInputStream(logStream).getLines()
    // Load the s3 inpur Stream into a scala list
    val logsList: List[String] = LogSearchUtils.getLogsListFromS3(List.empty,lines)

    // Perform binary search to check if the list of logs contain logs within the timestamp
    val isPresent: Int = LogSearchUtils.checkTimeIntervalExistsBS(st,et,logsList)

    if(isPresent >= 0){
      // If no pattern is supplies, only a status code with simple message is returned
      if(pattern.isEmpty){
        new APIGatewayProxyResponseEvent()
          .withStatusCode(HttpStatus.SC_OK)
          .withHeaders(Map("Content-Type" -> "application/json").asJava)
          .withIsBase64Encoded(true)
          .withBody(Json.obj("status"->HttpStatus.SC_OK,"message" -> "Logs found within the given time range").toString())
      }else{ // if the input payload contins the parameter pattern, then matches for that pattern are checked
        logger.info("pattern = {}", pattern.get)
        // fetches the matches for the pattern within the given time range of the timestamp
        val matchesInRange:List[String] = LogSearchUtils.getMatchingLogsInRange(logsList,isPresent,st,et,pattern.get.toString)
        new APIGatewayProxyResponseEvent()
          .withStatusCode(HttpStatus.SC_OK)
          .withHeaders(Map("Content-Type" -> "application/json").asJava)
          .withIsBase64Encoded(true)
          .withBody(Json.obj("status"->HttpStatus.SC_OK, "matches" -> matchesInRange).toString)
      }
    }else{ //No logs have been found within the given time-range return 404
     new APIGatewayProxyResponseEvent()
        .withStatusCode(HttpStatus.SC_NOT_FOUND)
        .withHeaders(Map("Content-Type" -> "application/json").asJava)
        .withIsBase64Encoded(true)
        .withBody(Json.obj("status"->HttpStatus.SC_NOT_FOUND).toString)
    }
  }
}
