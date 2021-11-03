package com.ajsa.server

import HelperUtils.LogSearchUtils
import com.ajsa.grpcproto.LambdaProto.{lambdaRequest, lambdaResponse}
import com.ajsa.server.grpc.HandleGrpcRequest
import com.ajsa.server.rest.HandleRestRequest
import com.amazonaws.services.lambda.runtime.{Context, LambdaLogger, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.{APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent}
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpStatus
import scalapb.json4s.JsonFormat

import collection.JavaConverters._
import org.json4s.jackson.JsonMethods._


/**
 * This class implements a unified lambda function that can serve requests in GRPC as well as REST style
 * The format in which the request needs to be handled needs to be specifiend in the payload being sent to the lambda function.
 * This lambda function is exposed via a POST api call using the AWS API gateway. Please refer to the Readme.md file for more details
 * regarding the lambda implementation
 */
class DynamicLambda extends RequestHandler[APIGatewayProxyRequestEvent,APIGatewayProxyResponseEvent]{

  val mapper = new ObjectMapper()

  /**
   * Handle the request sent to the lambda function
   * @param input
   * @param context
   * @return
   */
  override def handleRequest(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent = {
    val logger: LambdaLogger = context.getLogger
    logger.log("mapper.writeValueAsString("+input.getBody+")\n")
    // convert the input body into a MAP
    val reqMap: Map[String,Any] = parse(input.getBody).values.asInstanceOf[Map[String, Any]]

    logger.log("Requested type = "+reqMap.get("type").get.toString+"")

    if(reqMap.get("type").get.toString.equals("GRPC")){
      logger.log("Request is being handled in GRPC style")
      // using the lambda request class generated from the protobuf to deserialize the json into to the required grpc class
      val srq: lambdaRequest = JsonFormat.fromJsonString[lambdaRequest](input.getBody)
      // response received from the grpc handler is of type lambdaResponse which is a grpc auto generated class
      val res:lambdaResponse = HandleGrpcRequest.handleGrpcRequest(srq)

      new APIGatewayProxyResponseEvent()
        .withStatusCode(res.status.toInt)
        .withHeaders(Map("Content-Type" -> "application/grpc+proto").asJava) //set the return content-type to grpc
        .withIsBase64Encoded(true)
        .withBody(JsonFormat.toJsonString(res)) // return the response in the appropriate format for the grpc client
    }else{
      logger.log("Request is being handled in REST style")
      // validate if the json value sent to the api is valid. mandatory fields are 'st' and 'et'
      if(!LogSearchUtils.isValidInput(reqMap)){
        return new APIGatewayProxyResponseEvent()
          .withStatusCode(HttpStatus.SC_BAD_REQUEST)
          .withHeaders(Map("Content-Type" -> "application/json").asJava)
          .withIsBase64Encoded(true)
          .withBody("Bad Request")
      }

      val inputVals: Map[String,Any] = reqMap.get("input").get.asInstanceOf[Map[String,Any]]
      //handles the request in rest style. returns the response with content-type application/json
      HandleRestRequest.handleRestRequest(inputVals)
    }

  }
}
