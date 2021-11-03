package com.ajsa.akka

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.ajsa.grpcproto.LambdaProto.{Input, lambdaRequest, lambdaResponse}
import com.ajsa.service.GrpcService
import scalapb.json4s.JsonFormat
import spray.json.{DefaultJsonProtocol, enrichAny}

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}

case class RestRequest(`type`: String,input: InputOptions)
case class InputOptions(st:String,et:String,pattern:String)

// collect your json format instances into a support trait:
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val input = jsonFormat3(InputOptions)
  implicit val restFormat = jsonFormat2(RestRequest)
}

class HttpRoutingService extends Directives with JsonSupport {
  def startService(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty,"my-system")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

    val grpcService: GrpcService = new GrpcService

    val routes = concat(
      /**
       * Rest api exposed by the akka service that takes the message and communicates with lambda
       * using grpc protobuf compiled classes
       */
      path("grpc") {
        get {
          parameters('st.as[String], 'et.as[String]){
            (st,et) =>{

              val ip: Input = new Input(st,et)
              // construct a payload making use of the GRPC generated class lambdaRequest
              val payload:lambdaRequest = new lambdaRequest("GRPC",Option.apply(ip))

              // calling the service function created by extending the proto generated class
              val response:Future[lambdaResponse] = grpcService.searchLogs(payload)

              //Complete the request after grpc response is received from the service
              onComplete(response){
                case Success(value) => complete(value.status.toInt, List(`Content-Type`(`application/json`)), JsonFormat.toJsonString(value))
                case Failure(ex)    => complete(StatusCodes.InternalServerError,List(`Content-Type`(`text/plain(UTF-8)`)), s"An error occurred: ${ex.getMessage}")
              }
            }
          }

        }
      },
      {
        /**
         * Rest api exposed by the akka service that takes the message and communicates with lambda
         * using a REST POST CALL
         */
        path("rest" / "findLogPattern"){
          post{
            entity(as[RestRequest]){
              req =>{

                // marshall the entity into the correct format for the api gateway
                val entity = req.toJson.toString()

                // send request to the api gateway
                val request = HttpRequest(
                  method = HttpMethods.POST,
                  uri = "https://0x8ylnzku5.execute-api.us-west-2.amazonaws.com/dev/",
                  entity = entity)

                val responseFuture: Future[HttpResponse] = Http().singleRequest(request)

                onComplete(responseFuture){
                  case Success(res) => {
                    val responseAsString: Future[String] = Unmarshal(res.entity).to[String]
                    onComplete(responseAsString){
                      enty => complete(res.status.intValue(), List(`Content-Type`(`application/json`)), enty.get)
                    }
                  }
                  case Failure(ex)   => complete(StatusCodes.InternalServerError,List(`Content-Type`(`text/plain(UTF-8)`)), s"An error occurred: ${ex.getMessage}")
                }
              }
            }
          }
        }
      }
    )

    // Starts the local server at port 8080
    val bindingFuture = Http().newServerAt("localhost", 8080).bind(routes)

    println(s"Server now online. Please navigate to http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
object HttpRoutingService{
  def main(args: Array[String]): Unit = {
    val serv = new HttpRoutingService
    serv.startService(args)
  }
}
