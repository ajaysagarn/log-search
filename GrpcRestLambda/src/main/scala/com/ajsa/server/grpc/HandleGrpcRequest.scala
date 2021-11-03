package com.ajsa.server.grpc

import HelperUtils.CreateLogger
import com.ajsa.grpcproto.LambdaProto.{lambdaRequest, lambdaResponse}
import service.SearchService

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class HandleGrpcRequest
object HandleGrpcRequest{

  val logger = CreateLogger(classOf[HandleGrpcRequest])

  def handleGrpcRequest (request: lambdaRequest) : lambdaResponse = {
    logger.info("Handling grpc request")
    val service = new SearchService
    logger.info("Calling grpc service\n")
    val sr: lambdaResponse = Await.result(service.searchLogs(request), 60.seconds)
    logger.info("Received response from grpc service\n")
    sr
  }
}