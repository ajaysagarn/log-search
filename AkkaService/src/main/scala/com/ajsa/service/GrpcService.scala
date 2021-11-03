package com.ajsa.service

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.ajsa.grpcproto.LambdaProto.LogSearchServiceGrpc.LogSearchService
import com.ajsa.grpcproto.LambdaProto.{lambdaRequest, lambdaResponse}
import scalapb.json4s.JsonFormat

import scala.concurrent.Future

class GrpcService extends LogSearchService{
  implicit val system = ActorSystem(Behaviors.empty,"my-system")
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.executionContext

  /**
   * Service class that implements the grpc functionality to fetch logs from a lambda function
   * @param request
   * @return Future[lambdaResponse]
   */
  override def searchLogs(request: lambdaRequest): Future[lambdaResponse] = {
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = "https://0x8ylnzku5.execute-api.us-west-2.amazonaws.com/dev/",
      entity = JsonFormat.toJsonString(request))
    val response: Future[HttpResponse] = Http().singleRequest(req)

    response.transformWith[lambdaResponse](res => {
      val responseAsString: Future[String] = Unmarshal(res.get.entity).to[String]
      responseAsString.transformWith[lambdaResponse](entity => Future.successful[lambdaResponse](JsonFormat.fromJsonString[lambdaResponse](entity.get)))
    })

  }
}
