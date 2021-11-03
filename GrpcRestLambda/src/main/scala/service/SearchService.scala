package service

import HelperUtils.{CreateLogger, LogSearchUtils, ObtainConfigReference}
import com.ajsa.grpcproto.LambdaProto.LogSearchServiceGrpc.LogSearchService
import com.ajsa.grpcproto.LambdaProto.{Input, lambdaRequest, lambdaResponse}
import org.apache.http.HttpStatus
import software.amazon.awssdk.services.s3.model.S3Object

import java.io.{BufferedReader, InputStream, InputStreamReader}
import scala.concurrent.Future
import scala.io.Source

class SearchService extends LogSearchService{

  val logger = CreateLogger(classOf[SearchService])
  val config = ObtainConfigReference("application.conf","CONFIG").get

  /**
   * Implementation of the GRPC generated class. This class implements the functionality the GRPC service.
   * This service is used by the lambda when the request needs to handled in GRPC style.
   * @param request
   * @return
   */
  override def searchLogs(request: lambdaRequest): Future[lambdaResponse] = {

    logger.info("Retrieving the inputs part of the grpc message")
    val grpcInput: Input = request.getInput;

    val st = grpcInput.getFieldByNumber(1).toString
    val et = grpcInput.getFieldByNumber(2).toString

    logger.info("Get the s3 file contents")
    val s3ObjectKey: Option[S3Object] = LogSearchUtils.getLogFileKey(config.getString("S3_BUCKET"))

    if(s3ObjectKey.isEmpty){
      return Future.successful(lambdaResponse(status = HttpStatus.SC_NOT_FOUND.toString, message = "No logs found for given time range"))
    }

    val s3Stream: InputStream = LogSearchUtils.getS3InputStream(config.getString("S3_BUCKET"),s3ObjectKey.get.key())

    val br = new BufferedReader(new InputStreamReader(s3Stream))
    val fileSize = s3ObjectKey.get.size()
    val isPresent = LogSearchUtils.BinarySearchBufferedReader(st,et,br,fileSize)

    //Construct responses from the grpc generated classes.
    if(!isPresent){
      Future.successful(lambdaResponse(status = HttpStatus.SC_NOT_FOUND.toString, message = "No logs found for given time range"))
    }else{
      Future.successful(lambdaResponse(status = HttpStatus.SC_OK.toString, message = "Logs found for given time range"))
    }

  }
}
